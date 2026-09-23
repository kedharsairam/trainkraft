package com.trainkraft.app

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure on-device GPS session math for Phase D travel mode (the true-live layer).
 *
 * Pure Kotlin + kotlin.math only — no Android imports — so every function here
 * is directly unit-testable with plain JUnit ([TravelMathTest]). The Android
 * half ([TravelService]) owns permissions, the fused provider, the foreground
 * notification and trace writes; it calls into here for all numbers.
 *
 * Source-of-truth notes for GPS legs:
 * - Route order comes from the offline GTFS schedule
 *   ([com.trainkraft.app.data.TrainDao.getTrainSchedule], `seq`-ordered stop
 *   codes) passed in as plain `List<String>` — this file never touches a DAO.
 * - Live legs may ALSO be matched against the NTES live stop list
 *   ([com.trainkraft.app.data.LiveStopDto.code] / `DIST` km markers); the
 *   caller decides which list to pass as [routeCodes]. GTFS order is preferred
 *   offline; live order reflects the actual run (diversions, skipped halts).
 */

/** Mean Earth radius in km (haversine). */
const val EARTH_RADIUS_KM = 6371.0

/** Fixes worse than this accuracy are discarded before smoothing (metres). */
const val MAX_FIX_ACCURACY_M = 50f

/** How many recent fixes the speed smoother looks at (median window). */
const val SPEED_WINDOW = 5

/**
 * Below this speed the train reads "waiting" (km/h): a stationary train at
 * a halt (or a phone in a pocket at a platform) must never show motion.
 */
const val MIN_MOVING_SPEED_KMH = 8.0

/**
 * Advisory-arrival radius around a station (metres). Crossing it only lets the
 * UI layer show "arrived" and lets the service end the session — see
 * [arrivedWithinM].
 */
const val ARRIVAL_RADIUS_M = 800.0

/** One GPS sample for the speed smoother: fused speed + its accuracy. */
data class FixSample(
    val speedKmh: Double,
    val accuracyM: Float,
)

/**
 * Great-circle distance between two WGS-84 points, in km (haversine).
 * Pure — no datum correction: fine for ETA/next-stop legs at rail scale.
 */
fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/**
 * Median of [values]; null when empty. Median (not mean) so one tunnel-exit
 * jump or one fused-provider spike cannot drag the speed.
 */
fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid]
    else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/**
 * Smoothed speed over the last [SPEED_WINDOW] fixes (km/h).
 *
 * Filtering rules (pinned by tests):
 * - Fixes with [FixSample.accuracyM] > [MAX_FIX_ACCURACY_M] are rejected
 *   before smoothing (urban-canyon / indoor junk never enters the window).
 * - Only the last [SPEED_WINDOW] qualifying fixes are considered.
 * - Result is the median of those speeds; null when nothing qualifies.
 *
 * Tunnel behavior: while fixes stop arriving (or all arrive worse than 50 m),
 * the caller gets null and must HOLD the last published ETA — never invent a
 * fresh one from stale data. Documented here because this is where the "hold"
 * contract originates; [TravelService] implements it.
 */
fun smoothedSpeedKmh(fixes: List<FixSample>): Double? {
    val recent = fixes
        .filter { it.accuracyM <= MAX_FIX_ACCURACY_M }
        .takeLast(SPEED_WINDOW)
        .map { it.speedKmh }
    return median(recent)
}

/**
 * Advisory-arrival check: true when the fix is within [radiusM] metres
 * (default [ARRIVAL_RADIUS_M]) of the station coordinate.
 *
 * ADVISORY-ONLY BOUNDARY — READ LOUDLY: this boolean drives local UI
 * ("you've arrived") and local session teardown. It is NEVER written back to
 * any server, never posted to NTES, never shared. Arrival here is a reading
 * from consumer GPS (±50 m fixes against an 800 m radius); it must not feed
 * any upstream dataset.
 */
fun arrivedWithinM(
    stationLat: Double,
    stationLon: Double,
    fixLat: Double,
    fixLon: Double,
    radiusM: Double = ARRIVAL_RADIUS_M,
): Boolean = haversineKm(stationLat, stationLon, fixLat, fixLon) * 1000.0 <= radiusM

/**
 * Next stop code after [currentCode] in [routeCodes] (schedule `seq` order as
 * passed in — see file KDoc for GTFS vs engine source). Null when
 * [currentCode] is unknown, blank, or the last stop (journey end — the caller
 * treats this as the advisory-arrival arm). Matching is exact on the code
 * string; NTES codes and GTFS codes share the same namespace.
 */
fun nextStopAfter(currentCode: String, routeCodes: List<String>): String? {
    if (currentCode.isBlank() || routeCodes.isEmpty()) return null
    val index = routeCodes.indexOf(currentCode)
    if (index < 0 || index + 1 >= routeCodes.size) return null
    return routeCodes[index + 1]
}
