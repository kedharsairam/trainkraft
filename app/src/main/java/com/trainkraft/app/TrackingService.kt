package com.trainkraft.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.trainkraft.app.data.LiveStatusDto
import com.trainkraft.app.data.LoadResult
import com.trainkraft.app.data.NotificationPolicy
import com.trainkraft.app.data.PollSnapshot
import com.trainkraft.app.presentation.currentStopIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Minute-level foreground presence for user-tracked trains (Phase C core).
 *
 * WORKER RELATIONSHIP — READ BEFORE TOUCHING EITHER SIDE:
 * - [LiveStatusNotificationWorker] (15-min WorkManager cadence) is the
 *   BASELINE alerter for ALL tracked trains. It survives process death and
 *   Doze on its own; it is always running while a tracked row exists.
 * - This service is the user-enabled MINUTE presence: a 60s poll loop
 *   (30s boost near arrival) started explicitly (bell → "live tracking").
 *   It dies with the process / on task-removal and is NOT a delivery
 *   guarantee — the worker remains the guarantee.
 * - NO DOUBLE-NOTIFY INVARIANT: both paths funnel every poll through
 *   [NotificationPolicy.decide] against the SHARED `tracked_trains` row
 *   ([com.trainkraft.app.data.TrackedTrainEntity.lastCategory] /
 *   `lastDelayMin` / `lastPollAt`). The row IS the dedup: whichever path
 *   polls first writes the new poll state, the second path diffs against it
 *   and stays silent unless the delay category genuinely moved again. Both
 *   sides MUST call `updatePollState` on every handled poll (including
 *   silent ones) or this invariant breaks. Approach alerts additionally
 *   gate on `lastApproachFor` (one notify per watched station per journey).
 *
 * LIFECYCLE / ACTIONS:
 * - ACTION_START_TRACKING(trainNumber): ensure foreground + (re)start the
 *   poll loop. Idempotent; safe to call per bell toggle.
 * - ACTION_STOP_TRACKING(trainNumber): delete the tracked row, cancel that
 *   train's baseline worker (it would self-cancel on next run anyway once
 *   the row is gone — this is just immediate), drop it from this loop.
 * - ACTION_STOP_ALL: stop the loop and the service.
 * - Stop-self: the loop exits (and the service stops itself) when no
 *   tracked rows remain, or when every remaining train completed
 *   (completion deletes its row via the policy's `journeyOver` path).
 *
 * MULTI-TRAIN: one service, one sequential loop. Trains poll in row order
 * with a [STAGGER_OFFSET_MS] gap between polls inside a cycle so N tracked
 * trains never stampede NTES in the same second (thundering-herd guard).
 * The cycle interval is the MINIMUM of the per-train recommended intervals,
 * so one near-arrival train boosts the whole loop to 30s.
 *
 * POLL LADDER: 60s default; 30s when the next stop's published/expected
 * arrival is ≤15 min away (see [pollIntervalFor]). Missing clocks degrade
 * to 60s — the loop never crashes on prediction input.
 *
 * DOZE HONESTY: a foreground service is a Doze-exempt path only while it is
 * actually running; the OS can still kill it under memory pressure and
 * ColorOS/OEM skins kill aggressively (see field-test item below). Delivery
 * guarantees rest on the 15-min worker + inexact-alarm fallback, never on
 * this loop. ColorOS kill behavior + exact-alarm denial are recorded
 * device-session field-test items — none of this file's timing is verified
 * without a device (phone off USB: gate is compile + unit tests only).
 *
 * CHANNEL REUSE: alerts and the ongoing notification share the worker's
 * `live_status_updates` channel (same ID string, same name/importance).
 * [LiveStatusNotificationWorker.createNotificationChannel] is private, so the
 * creation call is mirrored here — creating an existing channel ID is a
 * no-op, and NO parallel channel is introduced. The channel ID literals must
 * stay in sync by hand until the worker exposes a shared constant.
 */
class TrackingService : Service() {

    companion object {
        const val ACTION_START_TRACKING = "com.trainkraft.app.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.trainkraft.app.action.STOP_TRACKING"
        const val ACTION_STOP_ALL = "com.trainkraft.app.action.STOP_ALL"
        const val EXTRA_TRAIN_NUMBER = "extra_train_number"

        /** Ongoing-foreground notification ID (alert IDs are trainNumber.hashCode(), worker-owned). */
        const val FOREGROUND_NOTIFICATION_ID = 1001

        /**
         * Tap target for service/alert notifications: explicit MainActivity
         * intent carrying the train number. MainActivity routes
         * EXTRA_TRAIN_NUMBER → `trainDetail/{trainNumber}` on create and on
         * new intents (see parseDeepLinkTrainNumber + TrainKraftNavHost).
         */
        const val DETAIL_EXTRA_TRAIN = EXTRA_TRAIN_NUMBER

        fun start(context: Context, trainNumber: String) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_START_TRACKING)
                .putExtra(EXTRA_TRAIN_NUMBER, trainNumber)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, trainNumber: String) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_STOP_TRACKING)
                .putExtra(EXTRA_TRAIN_NUMBER, trainNumber)
            context.startService(intent)
        }

        fun stopAll(context: Context) {
            context.startService(
                Intent(context, TrackingService::class.java).setAction(ACTION_STOP_ALL)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    /** Per-train recommended cycle interval from the last poll (default 60s). */
    private val trainIntervals = mutableMapOf<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureForeground(summaryFor(emptyList()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground(summaryFor(activeTrainsSnapshot()))
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                ensureLoopRunning()
            }
            ACTION_STOP_TRACKING -> {
                val train = intent.getStringExtra(EXTRA_TRAIN_NUMBER)
                scope.launch {
                    if (train != null) stopOneTrain(train)
                    if (trainIntervals.isEmpty()) stopSelf()
                }
            }
            ACTION_STOP_ALL -> {
                loopJob?.cancel()
                loopJob = null
                trainIntervals.clear()
                stopSelf()
            }
            else -> ensureLoopRunning()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ poll loop

    private fun ensureLoopRunning() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            var cycleMs = POLL_INTERVAL_MS
            while (true) {
                val trains = runCatching { container().trackingDao.getAll() }
                    .getOrNull().orEmpty()
                if (trains.isEmpty()) {
                    stopSelf()
                    return@launch
                }
                // Drop completions/untracks from the previous cycle.
                trainIntervals.keys.retainAll(trains.map { it.trainNumber }.toSet())

                var first = true
                for (tracked in trains) {
                    if (!first) delay(STAGGER_OFFSET_MS)
                    first = false
                    val recommended = runCatching { pollTrain(tracked.trainNumber) }
                        .getOrDefault(POLL_INTERVAL_MS)
                    trainIntervals[tracked.trainNumber] = recommended
                }
                updateForeground(summaryFor(trains.map { it.trainNumber }))
                cycleMs = (trainIntervals.values.minOrNull() ?: POLL_INTERVAL_MS)
                    .coerceIn(BOOST_POLL_INTERVAL_MS, POLL_INTERVAL_MS)
                delay(cycleMs)
            }
        }
    }

    /**
     * One poll for one train. Returns the recommended cycle interval for this
     * train until its next poll. Never throws (callers use runCatching
     * anyway): every fallible step degrades to "silent, 60s".
     */
    private suspend fun pollTrain(trainNumber: String): Long {
        val container = runCatching { container() }.getOrNull() ?: return POLL_INTERVAL_MS
        val dao = container.trackingDao
        val tracked = dao.get(trainNumber) ?: run {
            // Untracked elsewhere (bell off, completion delete): drop from loop.
            trainIntervals.remove(trainNumber)
            return POLL_INTERVAL_MS
        }

        val now = System.currentTimeMillis()
        val date = istFormat("dd-MMM-yyyy").format(Date(now)).uppercase(Locale.ENGLISH)
        val dto = when (val r = container.ntesRepository.liveStatus(trainNumber, date)) {
            is LoadResult.Live -> r.value
            is LoadResult.Offline -> r.value
            is LoadResult.Failed -> return trainIntervals[trainNumber] ?: POLL_INTERVAL_MS
        }

        val snapshot = dto.toPollSnapshot()
        val category = NotificationPolicy.delayCategory(snapshot.delayMin).name

        // Next-stop proximity from published/server clocks only (schedule
        // first, NTES expected times second — both displayed, neither
        // computed). No engine, no priors: poll timing is internal machinery,
        // and the timetable is all it needs to know "roughly near a stop".
        val stops = dto.stops
        val anchorIndex = currentStopIndex(
            stops.map { it.arrived },
            stops.map { it.departed },
        )
        val minutesUntil = minutesUntilNextStop(stops, anchorIndex, now)
        val recommended = pollIntervalFor(minutesUntil)

        // Shared-dedup funnel (see class KDoc): decide against stored state…
        when (val decision = NotificationPolicy.decide(trainNumber, snapshot, dao.get(trainNumber))) {
            is NotificationPolicy.Decision.Notify -> {
                postAlert(trainNumber, decision.title, decision.body)
                if (decision.journeyOver) {
                    dao.delete(trainNumber)
                    LiveStatusNotificationWorker.stop(this, trainNumber)
                    trainIntervals.remove(trainNumber)
                    return recommended
                } else {
                    dao.updatePollState(trainNumber, snapshot.delayMin, snapshot.lastStation, category, now)
                }
            }
            NotificationPolicy.Decision.Silent -> {
                dao.updatePollState(trainNumber, snapshot.delayMin, snapshot.lastStation, category, now)
            }
        }

        // …then the additive approach check for the user's watched station.
        // [TrackedTrainEntity.watchStationCode] arrives with the user.db v1→v2
        // migration (schema patch); until then this is a silent no-op.
        val fresh = dao.get(trainNumber)
        val approach = NotificationPolicy.evaluateApproach(
            trainNumber = trainNumber,
            watchStationCode = fresh?.watchStationCode,
            current = snapshot,
            minutesUntilArrival = minutesUntil,
            priorApproachFor = fresh?.lastApproachFor,
        )
        if (approach is NotificationPolicy.Decision.Notify) {
            postAlert(trainNumber, approach.title, approach.body)
            runCatching { dao.markApproachNotified(trainNumber, fresh?.watchStationCode?.trim().orEmpty()) }
        }
        return recommended
    }

    private suspend fun stopOneTrain(trainNumber: String) {
        runCatching { container().trackingDao.delete(trainNumber) }
        runCatching { LiveStatusNotificationWorker.stop(this, trainNumber) }
        trainIntervals.remove(trainNumber)
    }

    // ------------------------------------------------------------ notifications

    private fun container() = (application as TrainKraftApp).container

    private fun activeTrainsSnapshot(): List<String> = trainIntervals.keys.toList()

    private fun ensureForeground(summary: ForegroundSummary) {
        val notification = buildForegroundNotification(summary)
        try {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked on API 33+: cannot hold foreground.
            stopSelf()
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun updateForeground(summary: ForegroundSummary) {
        if (!notificationsAllowed()) return
        try {
            NotificationManagerCompat.from(this)
                .notify(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(summary))
        } catch (_: SecurityException) {
            // Revoked mid-run: keep polling silently; worker baseline unaffected.
        }
    }

    private fun buildForegroundNotification(summary: ForegroundSummary):
        android.app.Notification {
        createChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(summary.title)
            .setContentText(summary.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(detailPendingIntent(summary.tapTrainNumber))
            .build()
    }

    /** Mirrors the worker's channel; creating an existing ID is a no-op. */
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live Train Status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Periodic live status updates for tracked trains" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun detailPendingIntent(trainNumber: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            if (trainNumber != null) putExtra(DETAIL_EXTRA_TRAIN, trainNumber)
        }
        return PendingIntent.getActivity(
            this,
            (trainNumber ?: "all").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    private fun postAlert(trainNumber: String, title: String, body: String) {
        if (!notificationsAllowed()) return
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(detailPendingIntent(trainNumber))
                .build()
            NotificationManagerCompat.from(this).notify(trainNumber.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between check and post — drop, don't crash.
        }
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}

/** Worker's channel ID, mirrored (no parallel channels — see class KDoc). */
private const val CHANNEL_ID = "live_status_updates"

/** Ongoing-notification copy for zero / one / many tracked trains. */
private data class ForegroundSummary(
    val title: String,
    val text: String,
    val tapTrainNumber: String?,
)

private fun summaryFor(trains: List<String>): ForegroundSummary = when (trains.size) {
    0 -> ForegroundSummary(
        title = "TrainKraft tracking",
        text = "Starting live tracking…",
        tapTrainNumber = null,
    )
    1 -> ForegroundSummary(
        title = "Tracking train ${trains[0]}",
        text = "Live status every minute · tap for details",
        tapTrainNumber = trains[0],
    )
    else -> ForegroundSummary(
        title = "Tracking ${trains.size} trains",
        text = trains.take(2).joinToString(" · ") + if (trains.size > 2) " · …" else "",
        tapTrainNumber = trains[0],
    )
}

/** Asia/Kolkata formatter (NTES dates/keys are IST). */
private fun istFormat(pattern: String): SimpleDateFormat =
    SimpleDateFormat(pattern, Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

// ------------------------------------------------------------ pure helpers
// Top-level (no Android) so they are directly unit-testable.

/** Default poll cadence for the service loop. */
const val POLL_INTERVAL_MS = 60_000L

/** Boosted cadence when the next stop is imminent. */
const val BOOST_POLL_INTERVAL_MS = 30_000L

/** Boost when the next stop's published/expected arrival is within this window. */
const val BOOST_THRESHOLD_MIN = 15

/** Inter-poll gap inside one multi-train cycle (thundering-herd guard). */
const val STAGGER_OFFSET_MS = 5_000L

/**
 * Poll ladder: 30s when the next stop is [BOOST_THRESHOLD_MIN] min or less
 * away (small negative tolerance covers "just arrived, stale anchor"), 60s
 * otherwise — including null (no published/expected clock, no next stop:
 * pure, cheap path).
 * Never throws; null-safe by contract.
 */
fun pollIntervalFor(minutesUntilNextStop: Int?): Long =
    if (minutesUntilNextStop != null && minutesUntilNextStop <= BOOST_THRESHOLD_MIN &&
        minutesUntilNextStop >= -2
    ) {
        BOOST_POLL_INTERVAL_MS
    } else {
        POLL_INTERVAL_MS
    }

/**
 * Minutes until the next stop's published/expected arrival (schedule first,
 * NTES expected times second — every value here is displayed on screen, none
 * computed).
 *
 * next stop = stop after [anchorIndex] (first stop when null = pre-departure;
 * null when the anchor is the last stop). Base = first parseable of
 * scheduled arrival → ETA → scheduled departure → ETD (NTES `"HH:MM …"`
 * strings via [com.trainkraft.app.data.NtesFormats.timeToMinutes]).
 * Compared against IST wall-clock derived from [nowEpochMs] (pure/testable).
 * Overnight rollover: deltas below −12h wrap +24h. Null on any unparseable
 * input — the caller degrades to 60s.
 */
fun minutesUntilNextStop(
    stops: List<com.trainkraft.app.data.LiveStopDto>,
    anchorIndex: Int?,
    nowEpochMs: Long,
): Int? {
    val nextIndex = (anchorIndex ?: -1) + 1
    if (nextIndex < 0 || nextIndex >= stops.size) return null
    val next = stops[nextIndex]
    val arrival = listOf(next.scheduledArrival, next.estArrival, next.scheduledDeparture, next.estDeparture)
        .firstNotNullOfOrNull { com.trainkraft.app.data.NtesFormats.timeToMinutes(it) }
        ?: return null
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
        timeInMillis = nowEpochMs
    }
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    var delta = arrival - nowMin
    if (delta < -720) delta += 1440
    return delta
}

/**
 * Maps the repository DTO onto the policy's [PollSnapshot] (the worker parses
 * raw JSON directly; the service goes through [NtesRepository], so the
 * mapping lives here — same fields, same semantics: delay from LDEL,
 * station names with code fallback, TRUNST/isArrDSTN completion flags).
 */
fun LiveStatusDto.toPollSnapshot(): PollSnapshot = PollSnapshot(
    delayMin = delayMin,
    lastStation = lastStationName.ifBlank { lastStationCode },
    nextStation = nextStationName.ifBlank { nextStationCode },
    statusText = statusText,
    runState = runState,
    arrivedAtDest = arrivedAtDest,
)
