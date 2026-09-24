package com.trainkraft.app

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
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.trainkraft.app.data.TravelFixEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * On-device GPS travel mode (Phase D core — the true-live layer).
 *
 * A peer builds the travel UI; this service owns ONLY the foreground GPS
 * session: fused fixes → [TravelMath] numbers → trace rows + ongoing
 * notification. It is GPS-only: it never calls NtesRepository, never touches
 * the network, and never writes arrival back to any server (see
 * [arrivedWithinM]'s advisory-only boundary). Station coordinates come from
 * read-only GTFS lookups ([com.trainkraft.app.data.TrainDao.getTrainSchedule]
 * for route order + `searchStations` exact-code match for lat/lon, both via
 * the app container — no DAO is modified).
 *
 * LIFECYCLE / ACTIONS:
 * - ACTION_START_TRAVEL(trainNumber): prune month-old traces, load the route,
 *   go foreground (type location) and request fused updates. Idempotent per
 *   train; starting a new train replaces the session.
 * - ACTION_STOP_TRAVEL: remove updates, log the session row, stop.
 * - The session also ends on advisory arrival at the destination
 *   ([arrivedWithinM] vs the last route stop's GTFS coordinate) and on
 *   process death. Process death carries NO persistence promise — the session
 *   is ephemeral; only the already-written `travel_fixes` rows survive.
 *
 * PERMISSIONS: start requires ACCESS_FINE_LOCATION (checked at entry by the
 * UI peer; the service defensively stops + logs if revoked mid-session —
 * never crashes). POST_NOTIFICATIONS gates notification updates only; the
 * GPS loop is unaffected. No background-location permission exists by design:
 * fixes are requested foreground-only and removed on every stop path, so no
 * background GPS ever runs.
 *
 * BATTERY HONESTY: 10 s HIGH_ACCURACY GPS is the hungriest mode in the app —
 * budget ≤8 %/h as a field-test item, not a verified number. Foreground-only
 * + user-initiated + a stop path on every exit (user stop, arrival, process
 * death) bound the drain. The stop button must always be visible while a
 * session runs (UI peer owns that surface).
 *
 * BATTERY MEASUREMENT PROTOCOL (field test derives %/h): every session logs
 * one DEBUG row on stop — train, start/end timestamps, fix count (see
 * [logSessionRow]). Pair it with Settings → Battery before/after % and the
 * wall duration; %/h = delta% / hours. No in-app battery accounting beyond
 * this row — keep the service minimal.
 *
 * TUNNELS: fixes stop or arrive worse than 50 m → [smoothedSpeedKmh] yields
 * null → the service HOLDS the last published ETA (never invents one from
 * stale data); the notification copy shows "waiting for GPS" until fixes
 * resume. Accuracy rejection itself lives in [TravelMath].
 *
 * FIELD-TEST CHECKLIST (device gaps — phone off USB, none verified here):
 * 1. Start a session at the origin; confirm the ongoing notification appears.
 * 2. Moving: speed reads sane, ETA counts down, next-stop advances in order.
 * 3. Halt: speed < 8 km/h shows "waiting" (no absurd ETA).
 * 4. Tunnel: ETA holds, "waiting for GPS" shows, resumes after exit.
 * 5. Destination: advisory arrival fires within ~800 m, session stops.
 * 6. Battery: record Settings → Battery % before/after + duration → %/h.
 * 7. ColorOS: note any FGS-location kill (expected aggressive; record build).
 * 8. Revoke fine location mid-session → service stops, no crash.
 *
 * CHANNEL REUSE: the ongoing notification shares the worker's
 * `live_status_updates` channel (same ID string as [TrackingService]; cf. its
 * class KDoc — creating an existing channel ID is a no-op, no parallel
 * channel). Distinct notification ID ([FOREGROUND_NOTIFICATION_ID] = 1002).
 */
class TravelService : Service() {

    companion object {
        const val ACTION_START_TRAVEL = "com.trainkraft.app.action.START_TRAVEL"
        const val ACTION_STOP_TRAVEL = "com.trainkraft.app.action.STOP_TRAVEL"
        const val EXTRA_TRAIN_NUMBER = "extra_train_number"

        /** Ongoing-foreground notification ID (1001 is TrackingService's). */
        const val FOREGROUND_NOTIFICATION_ID = 1002

        private const val TAG = "TravelService"

        /** Trace rows older than this are pruned on every start. */
        const val TRACE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

        fun start(context: Context, trainNumber: String) {
            val intent = Intent(context, TravelService::class.java)
                .setAction(ACTION_START_TRAVEL)
                .putExtra(EXTRA_TRAIN_NUMBER, trainNumber)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TravelService::class.java).setAction(ACTION_STOP_TRAVEL),
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ephemeral session state — dies with the process by design. */
    private var activeTrain: String? = null
    private val fixWindow = ArrayDeque<FixSample>()
    private var fixCount = 0
    private var sessionStartMs = 0L
    private var lastNextStop: String? = null
    private var routeCodes: List<String> = emptyList()
    private var stationCoords: Map<String, Pair<Double, Double>> = emptyMap()

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val fix = result.lastLocation ?: return
            onFix(fix.latitude, fix.longitude, fix.speed, fix.accuracy, fix.time)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRAVEL -> {
                val train = intent.getStringExtra(EXTRA_TRAIN_NUMBER)?.trim().orEmpty()
                if (train.isEmpty()) {
                    Log.w(TAG, "START_TRAVEL without train number; ignoring")
                    stopSelf()
                } else {
                    startSession(train)
                }
            }
            ACTION_STOP_TRAVEL -> stopSession("user stop")
            else -> {
                // Sticky restart with no intent: no session to resume (ephemeral).
                if (activeTrain == null) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeUpdatesQuietly()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ session

    private fun startSession(train: String) {
        if (!hasFineLocation()) {
            Log.w(TAG, "START_TRAVEL $train without fine location; stopping (UI must gate)")
            stopSelf()
            return
        }
        activeTrain = train
        fixWindow.clear()
        fixCount = 0
        lastNextStop = null
        routeCodes = emptyList()
        stationCoords = emptyMap()
        sessionStartMs = System.currentTimeMillis()
        ensureForeground(travelSummaryFor(train, null, null, null))
        scope.launch {
            runCatching {
                val trace = container().userDatabase.travelTraceDao()
                trace.pruneBefore(System.currentTimeMillis() - TRACE_RETENTION_MS)
            }.onFailure { Log.w(TAG, "trace prune failed: ${it.message}") }
            loadRoute(train)
        }
        try {
            fused.requestLocationUpdates(
                locationRequest(),
                locationCallback,
                Looper.getMainLooper(),
            )
        } catch (se: SecurityException) {
            // Revoked between check and request — stop, never crash.
            Log.w(TAG, "location permission revoked at request; stopping")
            stopSelf()
        }
    }

    /**
     * Route order + station coordinates, read-only via the existing GTFS DAO:
     * [com.trainkraft.app.data.TrainDao.getTrainSchedule] gives `seq`-ordered
     * codes; each code resolves to lat/lon through `searchStations` with an
     * exact-code pick (stops without coordinates are skipped — legs past them
     * degrade to held ETA rather than wrong ETA).
     */
    private suspend fun loadRoute(train: String) {
        val container = runCatching { container() }.getOrNull() ?: return
        val schedule = runCatching { container.trainDao.getTrainSchedule(train) }
            .getOrDefault(emptyList())
        routeCodes = schedule.map { it.code }
        if (routeCodes.isEmpty()) {
            Log.w(TAG, "no schedule for $train; ETA/next-stop degraded until route loads")
            return
        }
        val coords = mutableMapOf<String, Pair<Double, Double>>()
        for (code in routeCodes.toSet()) {
            val station = runCatching { container.trainDao.searchStations(code) }
                .getOrDefault(emptyList())
                .firstOrNull { it.code == code }
            val lat = station?.lat
            val lon = station?.lon
            if (lat != null && lon != null) coords[code] = lat to lon
        }
        stationCoords = coords
    }

    private fun onFix(lat: Double, lon: Double, speedMps: Float, accuracyM: Float, timeMs: Long) {
        val train = activeTrain ?: return
        if (!hasFineLocation()) {
            Log.w(TAG, "location revoked mid-session for $train; stopping")
            stopSession("permission revoked")
            return
        }
        val speedKmh = (if (speedMps.isFinite()) speedMps.toDouble() else 0.0) * 3.6
        fixWindow.addLast(FixSample(speedKmh, accuracyM))
        while (fixWindow.size > SPEED_WINDOW) fixWindow.removeFirst()
        fixCount++

        scope.launch {
            runCatching {
                container().userDatabase.travelTraceDao().insert(
                    TravelFixEntity(
                        trainNumber = train,
                        tsEpochMs = timeMs,
                        lat = lat,
                        lon = lon,
                        speedKmh = speedKmh,
                        accuracyM = accuracyM,
                    ),
                )
            }.onFailure { Log.w(TAG, "trace insert failed: ${it.message}") }
        }

        // Tunnel hold: bad-accuracy fixes never enter the smoother (see
        // TravelMath); a null smooth reads "waiting" instead of inventing.
        val smooth = smoothedSpeedKmh(fixWindow.toList())
        val anchor = anchorCode(lat, lon)
        val next = anchor?.let { nextStopAfter(it, routeCodes) }
        if (next != null) lastNextStop = next
        val remainingKm = remainingKmTo(lat, lon, lastNextStop)

        updateForeground(travelSummaryFor(train, smooth, remainingKm, lastNextStop))

        // Advisory arrival at the destination ends the session (never server-bound).
        val dest = routeCodes.lastOrNull()
        val destCoords = stationCoords[dest]
        if (dest != null && destCoords != null &&
            arrivedWithinM(destCoords.first, destCoords.second, lat, lon)
        ) {
            Log.i(TAG, "advisory arrival at $dest for $train; ending session")
            stopSession("advisory arrival at $dest")
        }
    }

    /**
     * Nearest route stop within arrival radius = current anchor; null when no
     * route stop is near (between halts the anchor is unknown and the next
     * stop holds). Advisory only — same boundary as [arrivedWithinM].
     */
    private fun anchorCode(lat: Double, lon: Double): String? {
        var best: String? = null
        var bestM = Double.MAX_VALUE
        for ((code, coords) in stationCoords) {
            val m = haversineKm(coords.first, coords.second, lat, lon) * 1000.0
            if (m < bestM) {
                bestM = m
                best = code
            }
        }
        return if (best != null && bestM <= ARRIVAL_RADIUS_M) best else null
    }

    private fun remainingKmTo(lat: Double, lon: Double, stopCode: String?): Double? {
        val coords = stationCoords[stopCode] ?: return null
        return haversineKm(lat, lon, coords.first, coords.second)
    }

    private fun stopSession(reason: String) {
        val train = activeTrain
        removeUpdatesQuietly()
        if (train != null) logSessionRow(train, reason)
        activeTrain = null
        stopSelf()
    }

    private fun removeUpdatesQuietly() {
        runCatching { fused.removeLocationUpdates(locationCallback) }
    }

    /**
     * One DEBUG row per session — the battery protocol's in-app half
     * (field test pairs this with Settings → Battery before/after %):
     * train, start/end timestamps, fix count, reason.
     */
    private fun logSessionRow(train: String, reason: String) {
        Log.d(
            TAG,
            "travel session train=$train start=$sessionStartMs " +
                "end=${System.currentTimeMillis()} fixes=$fixCount reason=$reason",
        )
    }

    // ------------------------------------------------------------ notifications

    private fun container() = (application as TrainKraftApp).container

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun ensureForeground(summary: TravelSummary) {
        val notification = buildForegroundNotification(summary)
        try {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun updateForeground(summary: TravelSummary) {
        if (!notificationsAllowed()) return
        try {
            NotificationManagerCompat.from(this)
                .notify(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(summary))
        } catch (_: SecurityException) {
            // Revoked mid-run: GPS loop continues; notification silently stale.
        }
    }

    private fun buildForegroundNotification(summary: TravelSummary): android.app.Notification {
        createChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(summary.title)
            .setContentText(summary.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(detailPendingIntent(summary.trainNumber))
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

    private fun detailPendingIntent(trainNumber: String): PendingIntent {
        // Same deep-link key MainActivity routes to trainDetail/{trainNumber}.
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(TrackingService.DETAIL_EXTRA_TRAIN, trainNumber)
        }
        return PendingIntent.getActivity(
            this,
            trainNumber.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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

/** Request: HIGH_ACCURACY, 10 s interval, 5 s fastest — foreground only. */
private fun locationRequest(): LocationRequest =
    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
        .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
        .build()

/** Fused cadence: 10 s interval… */
const val UPDATE_INTERVAL_MS = 10_000L

/** …5 s fastest (a burst catch-up never exceeds this rate). */
const val FASTEST_INTERVAL_MS = 5_000L

/** Ongoing-notification copy for a travel session. */
data class TravelSummary(
    val trainNumber: String,
    val title: String,
    val text: String,
)

/**
 * Notification copy for the session state. Pure (String-only) so it is
 * directly unit-testable ([TravelServiceLogicTest]). Speed, remaining
 * distance and next stop are GPS measurements; no arrival-time estimates
 * anywhere. A halted speed (< 8 km/h) reads "waiting · next <stop>"
 * (never a bogus count); with no route at all it reads "waiting for GPS"
 * unless the fix itself is fresh and moving, in which case the live
 * GPS speed leads ("87 km/h · tap for details").
 */
fun travelSummaryFor(
    trainNumber: String,
    speedKmh: Double?,
    remainingKm: Double?,
    nextStop: String?,
): TravelSummary {
    val moving = speedKmh != null && speedKmh >= MIN_MOVING_SPEED_KMH
    val halted = speedKmh != null && !moving
    val speedText = if (moving) "${speedKmh!!.toInt()} km/h" else "waiting"
    val distText = remainingKm?.takeIf { it.isFinite() && it >= 0.0 }?.let {
        if (it < 1.0) "~${(it * 1000).toInt()} m" else "~${it.toInt()} km"
    }
    val text = when {
        nextStop != null && distText != null && !halted ->
            "$speedText · $distText to $nextStop · tap for details"
        // Halt: "waiting" only, never a bogus count.
        nextStop != null -> "$speedText · next $nextStop · tap for details"
        halted -> "$speedText · tap for details"
        // Moving with no route/next-stop yet: lead with the fresh GPS speed
        // ("87 km/h · tap for details") instead of "waiting for GPS" — the
        // fix is live, only the anchor is unknown. A stale (null-speed) fix
        // still falls through to waiting-for-GPS below.
        moving -> "$speedText · tap for details"
        else -> "waiting for GPS · tap for details"
    }
    return TravelSummary(
        trainNumber = trainNumber,
        title = "Travel mode · $trainNumber",
        text = text,
    )
}
