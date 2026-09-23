package com.trainkraft.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trainkraft.app.data.LoadResult
import com.trainkraft.app.data.NtesConfig
import com.trainkraft.app.data.NtesRepository
import com.trainkraft.app.data.NotificationPolicy
import com.trainkraft.app.data.ResponseCache
import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.data.UserDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Fires [AlarmScheduler]-scheduled station alarms.
 *
 * On fire: immediate [NtesRepository.liveStatus] poll for the alarmed train,
 * arrival/approach evaluation, then notify — all funneled through
 * [NotificationPolicy] against the shared `tracked_trains` poll state, the
 * same dedup the worker and [TrackingService] use (see the service's KDoc:
 * the row IS the dedup). Poll state is updated on every handled fire,
 * including silent ones.
 *
 * Guards (all silent returns): wrong action, missing extras, train no longer
 * tracked (stale alarm after bell-off/completion), network failure with no
 * cache (Offline value is still usable and IS used), completion (handled via
 * the policy's `journeyOver` path — row deleted, worker cancelled).
 *
 * Manifest-registered (`exported=false`, AlarmManager-owned PendingIntent —
 * see manifest patch). `goAsync()` keeps the broadcast alive across the
 * suspend poll; a SupervisorJob scope means one bad fire never affects
 * another. Never throws out of [onReceive].
 */
class AlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != AlarmScheduler.ACTION_STATION_ALARM) return
        val trainNumber = intent.getStringExtra(AlarmScheduler.EXTRA_TRAIN_NUMBER)
            ?.trim().orEmpty()
        val stationCode = intent.getStringExtra(AlarmScheduler.EXTRA_STATION_CODE)
            ?.trim().orEmpty()
        if (trainNumber.isEmpty() || stationCode.isEmpty()) return

        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                fire(appContext, trainNumber, stationCode)
            } catch (_: Exception) {
                // Alarm fires are best-effort; never crash the broadcast.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, trainNumber: String, stationCode: String) {
        val userDb = UserDatabase.getInstance(context)
        val dao = userDb.trackingDao()
        val tracked = dao.get(trainNumber) ?: return // Stale alarm: bell off / completed.

        val repository = NtesRepository(
            ResponseCache(userDb.cacheDao()) { SettingsStore.cacheDurationMs(context) }
        ) { NtesConfig.getKeys(context) }
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date(now)).uppercase(Locale.ENGLISH)

        val dto = when (val r = repository.liveStatus(trainNumber, date)) {
            is LoadResult.Live -> r.value
            is LoadResult.Offline -> r.value
            is LoadResult.Failed -> return // No data, no state change, no notify.
        }
        val snapshot = dto.toPollSnapshot()
        val category = NotificationPolicy.delayCategory(snapshot.delayMin).name

        // Terminal states first, through the shared policy (deduped).
        when (val decision = NotificationPolicy.decide(trainNumber, snapshot, dao.get(trainNumber))) {
            is NotificationPolicy.Decision.Notify -> {
                postNotification(context, trainNumber, decision.title, decision.body)
                if (decision.journeyOver) {
                    dao.delete(trainNumber)
                    LiveStatusNotificationWorker.stop(context, trainNumber)
                } else {
                    dao.updatePollState(trainNumber, snapshot.delayMin, snapshot.lastStation, category, now)
                }
                return
            }
            NotificationPolicy.Decision.Silent -> {
                dao.updatePollState(trainNumber, snapshot.delayMin, snapshot.lastStation, category, now)
            }
        }

        // The alarm trigger itself IS the time condition (user chose
        // minutesBefore), so the approach check runs in alarmFired mode:
        // notify for the requested station unless already notified.
        val fresh = dao.get(trainNumber) ?: return
        val approach = NotificationPolicy.evaluateApproach(
            trainNumber = trainNumber,
            watchStationCode = stationCode,
            current = snapshot,
            minutesUntilArrival = null,
            priorApproachFor = fresh.lastApproachFor,
            alarmFired = true,
        )
        if (approach is NotificationPolicy.Decision.Notify) {
            postNotification(context, trainNumber, approach.title, approach.body)
            runCatching { dao.markApproachNotified(trainNumber, stationCode.uppercase()) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(context: Context, trainNumber: String, title: String, body: String) {
        // POST_NOTIFICATIONS is checked by the scheduler-side onboarding; the
        // try/catch keeps a revoked grant from crashing the broadcast.
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(trainNumber.hashCode(), notification)
        } catch (_: SecurityException) {
            // Revoked — drop silently.
        }
    }
}

/** Worker's channel ID, reused (no parallel channels — see service KDoc). */
private const val CHANNEL_ID = "live_status_updates"
