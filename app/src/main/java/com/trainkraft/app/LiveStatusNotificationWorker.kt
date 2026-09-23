package com.trainkraft.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trainkraft.app.data.NtesApi
import com.trainkraft.app.data.NtesConfig
import com.trainkraft.app.data.NotificationPolicy
import com.trainkraft.app.data.PollSnapshot
import com.trainkraft.app.data.UserDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Background worker that polls NTES live status every 15 minutes and posts a
 * notification ONLY on meaningful changes (see [NotificationPolicy]):
 * delay-category deterioration, big shift inside SEVERE, cancellation, or
 * journey completion. Silent polls just update the stored diff state.
 *
 * Cadence note: WorkManager enforces a 15-minute floor on periodic work
 * ([PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS]) and clamps anything
 * shorter (with a warning, not a crash) — so the schedule requests
 * [POLL_INTERVAL_MINUTES] = 15 min explicitly, with a [POLL_FLEX_MINUTES] =
 * 5 min flex window (the flex floor). Do not lower these: the OS will clamp
 * them back and the "every poll" wording in [NotificationPolicy] /
 * TrackedTrainEntity docs refers to this 15-minute cadence.
 *
 * Failure handling: transient network errors return [Result.retry] (backoff)
 * instead of being swallowed as success; a train that's no longer tracked
 * self-cancels the unique work; journey completion deletes the tracked row
 * (bell turns off) and stops the worker.
 *
 * Usage:
 *   LiveStatusNotificationWorker.start(context, "12952")
 *   LiveStatusNotificationWorker.stop(context, "12952")
 */
class LiveStatusNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "live_status_updates"
        private const val WORK_NAME_PREFIX = "live_status_"
        private const val TRAIN_NUMBER_KEY = "train_number"

        /**
         * Requested poll cadence. Must stay at/above WorkManager's 15-minute
         * periodic floor (shorter values are silently clamped with a warning).
         * Flex is the 5-minute flex floor: work runs in the trailing flex
         * window of each 15-minute interval.
         */
        const val POLL_INTERVAL_MINUTES = 15L
        const val POLL_FLEX_MINUTES = 5L

        fun start(context: Context, trainNumber: String) {
            createNotificationChannel(context)
            val request = PeriodicWorkRequestBuilder<LiveStatusNotificationWorker>(
                POLL_INTERVAL_MINUTES, TimeUnit.MINUTES,
                POLL_FLEX_MINUTES, TimeUnit.MINUTES,
            )
                .setInputData(workDataOf(TRAIN_NUMBER_KEY to trainNumber))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PREFIX + trainNumber,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }

        fun stop(context: Context, trainNumber: String) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME_PREFIX + trainNumber)
        }

        /**
         * Re-enqueues workers for every persisted tracked train. WorkManager
         * normally survives process death, but a forced stop during an app
         * update can leave rows without scheduled work — idempotent thanks
         * to KEEP, so always safe. (Tracking rows live in user.db, which is
         * excluded from backup, so restore drift cannot occur.)
         */
        fun restoreTracked(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val dao = UserDatabase.getInstance(appContext).trackingDao()
                    dao.getAll().forEach { start(appContext, it.trainNumber) }
                } catch (_: Exception) {
                    // DB may not be ready on very first launch — VM paths re-enqueue on toggle.
                }
            }
        }

        private fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Train Status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Periodic live status updates for tracked trains"
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        val trainNumber = inputData.getString(TRAIN_NUMBER_KEY) ?: return Result.failure()

        if (!notificationsAllowed()) return Result.success()

        val dao = UserDatabase.getInstance(applicationContext).trackingDao()
        val tracked = dao.get(trainNumber)
        if (tracked == null) {
            // Untracked elsewhere (completion delete, backup restore drift): silence.
            stop(applicationContext, trainNumber)
            return Result.success()
        }

        return try {
            val date = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }.format(Date()).uppercase(Locale.ENGLISH)

            val keys = NtesConfig.getKeys(applicationContext)
            val json = NtesApi.liveStatus(trainNumber, date, keys)
                .getOrElse { return Result.retry() }

            val snapshot = parseSnapshot(json)
            val decision = NotificationPolicy.decide(trainNumber, snapshot, tracked)
            val category = NotificationPolicy.delayCategory(snapshot.delayMin).name
            val now = System.currentTimeMillis()

            when (decision) {
                is NotificationPolicy.Decision.Notify -> {
                    postNotification(trainNumber, decision.title, decision.body)
                    if (decision.journeyOver) {
                        dao.delete(trainNumber)
                        stop(applicationContext, trainNumber)
                    } else {
                        dao.updatePollState(trainNumber, snapshot.delayMin, snapshot.lastStation, category, now)
                    }
                }
                NotificationPolicy.Decision.Silent -> {
                    dao.updatePollState(trainNumber, snapshot.delayMin, snapshot.lastStation, category, now)
                }
            }
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (_: Exception) {
            // Config/parse problem — don't spin the scheduler on retry.
            Result.success()
        }
    }

    private fun parseSnapshot(json: String): PollSnapshot {
        val o = JSONObject(json)
        return PollSnapshot(
            delayMin = o.optString("LDEL", "0").toIntOrNull() ?: 0,
            lastStation = o.optString("LSTNN", o.optString("LSTN", "")),
            nextStation = o.optString("NSTNN", o.optString("NSTN", "")),
            statusText = o.optString("CPOS", o.optString("LASTUPD", "")),
            runState = o.optInt("TRUNST", -1),
            arrivedAtDest = o.optBoolean("isArrDSTN", false),
        )
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Posts the notification. [notificationsAllowed] already verifies the
     * revocable POST_NOTIFICATIONS permission (notify() itself would throw);
     * lint can't prove that across the helper boundary, hence the suppression.
     */
    @SuppressLint("MissingPermission")
    private fun postNotification(trainNumber: String, title: String, body: String) {
        if (!notificationsAllowed()) return

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val notificationId = trainNumber.hashCode()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }
}
