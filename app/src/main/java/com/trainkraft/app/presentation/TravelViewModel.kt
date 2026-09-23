package com.trainkraft.app.presentation

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.ARRIVAL_RADIUS_M
import com.trainkraft.app.FixSample
import com.trainkraft.app.TrainKraftApp
import com.trainkraft.app.TravelService
import com.trainkraft.app.arrivedWithinM
import com.trainkraft.app.data.TrainDatabase
import com.trainkraft.app.haversineKm
import com.trainkraft.app.nextStopAfter
import com.trainkraft.app.smoothedSpeedKmh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Travel-mode (Phase D on-board GPS) view-model: the glanceable journey
 * screen's numbers, derived from the trace table the service writes.
 *
 * BINDING CHOICE (documented): no ServiceConnection — [TravelService.onBind]
 * returns null by design (start/stop via intents only), so there is nothing
 * to bind to. The least-machinery rotation-safe option is polling the
 * service's own write path ([com.trainkraft.app.data.TravelTraceDao]) every
 * [TRAVEL_POLL_MS]: the ViewModel survives rotation and re-emits via
 * StateFlow, with zero binder ceremony. Process death wipes the VM *and*
 * the service with no persistence promise — the session is over, and the
 * screen honestly falls back to waiting copy (no false promise of resume).
 *
 * GPS-ONLY: this VM never touches the network. Station coordinates come
 * from the read-only GTFS DAO ([trainDao] route + `searchStations`
 * exact-code pick — the existing VM DAO pattern from
 * [TrainDetailViewModel]). No arrival-time estimates anywhere: speed,
 * remaining distance and next stop are GPS measurements.
 */
class TravelViewModel(
    application: Application,
    val trainNumber: String,
) : AndroidViewModel(application) {

    private val trainDao = TrainDatabase.getInstance(application).trainDao()
    private val container = (application as? TrainKraftApp)?.container
    private val traceDao = container?.userDatabase?.travelTraceDao()

    private val _display = MutableStateFlow<TravelDisplay?>(null)
    val display: StateFlow<TravelDisplay?> = _display.asStateFlow()

    /**
     * Minute-service presence for display only (same ActivityManager
     * fallback [TrainDetailViewModel] uses for TrackingService — reused,
     * not duplicated). False after process death: session over.
     */
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private var tickerJob: kotlinx.coroutines.Job? = null

    // Route inputs (loaded once; degraded-empty when the DAO fails).
    private var routeLoaded = false
    private var routeCodes: List<String> = emptyList()
    private var nameByCode: Map<String, String> = emptyMap()
    private var coordsByCode: Map<String, Pair<Double, Double>> = emptyMap()

    /** Session-held next stop (screen-scoped; mirrors the service's hold). */
    private var heldNext: String? = null

    /** Screen-side ticker: poll the trace table, re-derive, re-emit. */
    fun startTravelTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            try {
                while (true) {
                    refreshOnce()
                    kotlinx.coroutines.delay(TRAVEL_POLL_MS)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Normal stop path.
            }
        }
    }

    fun stopTravelTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun refreshTravelServiceState(context: Context) {
        _serviceRunning.value = isServiceRunning(context, TravelService::class.java)
    }

    fun stopTravel(context: Context) {
        TravelService.stop(context)
        _serviceRunning.value = false
    }

    fun refreshOnce() {
        viewModelScope.launch {
            ensureRoute()
            val now = System.currentTimeMillis()
            val fixes = try {
                traceDao?.fixesForTrain(trainNumber).orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            val last = fixes.maxByOrNull { it.tsEpochMs }
            val hasFix = last != null
            val ageMs = last?.let { (now - it.tsEpochMs).coerceAtLeast(0L) }
            val stale = isGpsStale(ageMs)
            val recent = fixes.filter {
                it.tsEpochMs <= now && now - it.tsEpochMs <= TRAVEL_FIX_WINDOW_MS
            }
            val speed = smoothedSpeedKmh(recent.map { FixSample(it.speedKmh, it.accuracyM) })

            var next: String? = null
            var remaining: Double? = null
            var covered: Double? = null
            var total: Double? = null
            var arrived: String? = null
            if (last != null) {
                val anchor = anchorCode(last.lat, last.lon)
                val dest = routeCodes.lastOrNull()
                val destCoords = dest?.let { coordsByCode[it] }
                if (dest != null && destCoords != null &&
                    arrivedWithinM(destCoords.first, destCoords.second, last.lat, last.lon)
                ) {
                    arrived = dest
                }
                // Anchor known → strict next (null at the destination);
                // anchor unknown (between halts) → hold the last known next.
                next = if (anchor != null) {
                    nextStopAfter(anchor, routeCodes)
                } else {
                    heldNext?.takeIf { it in routeCodes }
                }
                if (next != null) heldNext = next
                val nextCoords = next?.let { coordsByCode[it] }
                if (nextCoords != null) {
                    remaining = haversineKm(last.lat, last.lon, nextCoords.first, nextCoords.second)
                    val anchorCoords = anchor?.takeIf { it != next }?.let { coordsByCode[it] }
                    if (anchorCoords != null) {
                        val leg = haversineKm(
                            anchorCoords.first, anchorCoords.second,
                            nextCoords.first, nextCoords.second,
                        )
                        if (leg > 0.0) {
                            total = leg
                            covered = (leg - (remaining ?: leg)).coerceIn(0.0, leg)
                        }
                    }
                }
            }
            _display.value = mapTravelDisplay(
                hasFix = hasFix,
                stale = stale,
                speedKmh = speed,
                nextCode = next,
                nextName = next?.let { nameByCode[it] },
                remainingKm = remaining,
                legCoveredKm = covered,
                legTotalKm = total,
                arrivedCode = arrived,
            )
        }
    }

    /**
     * Route order + station coordinates, read-only via the existing GTFS
     * DAO — mirrors [TravelService]'s loadRoute (schedule `seq` order, each
     * code resolved through `searchStations` exact-code pick; stops without
     * coordinates are skipped so legs past them degrade instead of lying).
     */
    private suspend fun ensureRoute() {
        if (routeLoaded) return
        try {
            val schedule = trainDao.getTrainSchedule(trainNumber)
            routeCodes = schedule.map { it.code }
            nameByCode = schedule.associate { it.code to it.name }
            val coords = mutableMapOf<String, Pair<Double, Double>>()
            for (code in routeCodes.toSet()) {
                val station = trainDao.searchStations(code).firstOrNull { it.code == code }
                val lat = station?.lat
                val lon = station?.lon
                if (lat != null && lon != null) coords[code] = lat to lon
            }
            coordsByCode = coords
        } catch (_: Exception) {
            routeCodes = emptyList()
            nameByCode = emptyMap()
            coordsByCode = emptyMap()
        }
        routeLoaded = true
    }

    /** Nearest route stop within arrival radius = current anchor (advisory). */
    private fun anchorCode(lat: Double, lon: Double): String? {
        var best: String? = null
        var bestM = Double.MAX_VALUE
        for ((code, coords) in coordsByCode) {
            val m = haversineKm(coords.first, coords.second, lat, lon) * 1000.0
            if (m < bestM) {
                bestM = m
                best = code
            }
        }
        return if (best != null && bestM <= ARRIVAL_RADIUS_M) best else null
    }

    class Factory(
        private val application: Application,
        private val trainNumber: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TravelViewModel(application, trainNumber) as T
        }
    }
}

// ------------------------------------------------- Phase D pure helpers
// Top-level (no Android dependencies) so they stay unit-testable on the
// JVM — same pattern as TrainDetailViewModel's pure helpers. The ViewModel
// only wires flows around them.

/** No fix newer than this → "GPS searching…" (muted, last values greyed). */
const val GPS_STALE_MS = 30_000L

/** Only fixes this fresh enter the speed smoother (old rows ignored). */
const val TRAVEL_FIX_WINDOW_MS = 60_000L

/** Trace-table poll cadence (the service writes every ~10 s). */
const val TRAVEL_POLL_MS = 5_000L

/** GPS badge axis: device-local fix age — NOT server freshness. */
enum class TravelGpsBadge { LIVE, SEARCHING, WAITING }

/**
 * Display model for one travel-screen frame. [dimmed] means the numbers are
 * last-known (stale) — the screen greys them and never live-presents them.
 * No arrival-time estimates anywhere: speed, remaining distance and next stop
 * are measurements; the screen never forecasts.
 */
data class TravelDisplay(
    val badge: TravelGpsBadge,
    val speedText: String,
    val dimmed: Boolean,
    val nextStopText: String?,
    val detailText: String,
    val progress: Pair<Int, Int>?,
    val arrivedCode: String?,
    val announcement: String,
)

/**
 * Stale check on the last-fix age. Null (no fix ever) is NOT stale — it is
 * the separate bare-waiting state.
 */
fun isGpsStale(lastFixAgeMs: Long?): Boolean =
    lastFixAgeMs != null && lastFixAgeMs > GPS_STALE_MS

/**
 * Huge-speed text. Null (no qualifying fix / halted / tunnel-held) reads
 * "waiting" — never a fabricated number. Truncation (not rounding) mirrors
 * the service notification copy ([travelSummaryFor]).
 */
fun formatTravelSpeed(speedKmh: Double?): String =
    if (speedKmh == null) "waiting" else "${speedKmh.toInt()} km/h"

/**
 * Remaining-distance text. Null (unknown leg — no coordinates) → null (the
 * caller hides the distance, never guesses). GPS-derived values carry "~";
 * sub-km legs read in metres.
 */
fun formatRemainingKm(remainingKm: Double?): String? {
    if (remainingKm == null) return null
    if (!remainingKm.isFinite() || remainingKm < 0.0) return null
    return if (remainingKm < 1.0) "~${(remainingKm * 1000).toInt()} m"
    else "~${remainingKm.toInt()} km"
}

/**
 * Segment-progress args for [JourneyProgress] vocabulary reuse
 * (`covered / total km`). Null unless the leg is known and ≥ 1 km (int-km
 * rounding would lie about sub-km legs — those are covered by the "~x m"
 * distance text instead).
 */
fun legProgressKm(coveredKm: Double?, totalKm: Double?): Pair<Int, Int>? {
    if (coveredKm == null || totalKm == null) return null
    if (!coveredKm.isFinite() || !totalKm.isFinite() || totalKm < 1.0) return null
    val total = totalKm.toInt()
    if (total <= 0) return null
    return coveredKm.coerceIn(0.0, totalKm).toInt() to total
}

/**
 * Maps one frame of travel state to its display model (the unit-tested
 * state-mapping core). Priority: advisory arrival > live GPS (fresh or
 * stale-greyed) > bare waiting. [announcement] is the single TalkBack
 * sentence (speed + next stop + distance). No arrival-time estimates:
 * remaining distance is a GPS measurement, never a forecast.
 */
fun mapTravelDisplay(
    hasFix: Boolean,
    stale: Boolean,
    speedKmh: Double?,
    nextCode: String?,
    nextName: String?,
    remainingKm: Double?,
    legCoveredKm: Double?,
    legTotalKm: Double?,
    arrivedCode: String?,
): TravelDisplay {
    val speedText = formatTravelSpeed(speedKmh)
    if (arrivedCode != null) {
        return TravelDisplay(
            badge = if (hasFix && !stale) TravelGpsBadge.LIVE else TravelGpsBadge.SEARCHING,
            speedText = speedText,
            dimmed = stale,
            nextStopText = arrivedCode,
            detailText = "Arrived — advisory GPS estimate, verify on the platform",
            progress = null,
            arrivedCode = arrivedCode,
            announcement = "Arrived at $arrivedCode. Advisory GPS estimate. Speed $speedText.",
        )
    }
    if (hasFix && nextCode != null) {
        val rem = formatRemainingKm(remainingKm)
        val next = if (nextName.isNullOrBlank()) nextCode else "$nextCode · $nextName"
        val prefix = if (stale) "GPS searching. Last known values. " else ""
        val detail = rem ?: "waiting"
        return TravelDisplay(
            badge = if (stale) TravelGpsBadge.SEARCHING else TravelGpsBadge.LIVE,
            speedText = speedText,
            dimmed = stale,
            nextStopText = next,
            detailText = detail,
            progress = legProgressKm(legCoveredKm, legTotalKm),
            arrivedCode = null,
            announcement = "$prefix$speedText. Next stop $next. $detail.",
        )
    }
    if (hasFix) {
        // Fix but no resolvable next stop (between halts, no coordinates).
        val prefix = if (stale) "GPS searching. Last known values. " else ""
        return TravelDisplay(
            badge = if (stale) TravelGpsBadge.SEARCHING else TravelGpsBadge.LIVE,
            speedText = speedText,
            dimmed = stale,
            nextStopText = null,
            detailText = "Next stop unknown",
            progress = null,
            arrivedCode = null,
            announcement = "${prefix}Speed $speedText. Next stop unknown.",
        )
    }
    // No fix yet → bare waiting. No server predictions, no fallback labels.
    return TravelDisplay(
        badge = TravelGpsBadge.WAITING,
        speedText = speedText,
        dimmed = false,
        nextStopText = null,
        detailText = "Waiting for GPS…",
        progress = null,
        arrivedCode = null,
        announcement = "Waiting for GPS.",
    )
}
