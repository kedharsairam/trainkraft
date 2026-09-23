package com.trainkraft.app.presentation

import com.trainkraft.app.data.DelayPriorEntity
import com.trainkraft.app.data.FogOverlayEntity
import com.trainkraft.app.data.LiveStopDto
import kotlin.math.roundToInt

/**
 * Phase B prediction engine (pure logic — no Android/Compose dependencies).
 *
 * Predicts per-stop delay for the remainder of a journey from two signals:
 * a live anchor (the delay actually observed at the train's current position)
 * and historical per-station averages ([DelayPriorEntity], shipped in the
 * offline intelligence pack). A UI peer wires [predictJourney] into the
 * detail / between screens; both sides implement to the contract below.
 *
 * Data background (verified against
 * `app/src/test/resources/fixtures/live_status_12951_22sep.json`, 12951
 * MMCT→NDLS: `DARR`/`DDEP` carry per-stop actuals such as ST `00:05`,
 * NDLS `On Time`; and `avg_delay_12952.json`, 12952 NDLS→MMCT: BVI dep
 * `00:18`, MMCT arr `On Time`):
 * - [LiveStopDto.code] is the station code (`SC`); [LiveStopDto.distance]
 *   is the `DIST` km marker from the source.
 * - [DelayPriorEntity.arrAvgMin] / [depAvgMin] are whole-minute historical
 *   averages (0 = on time).
 * - [FogOverlayEntity.action] is one of `CANCELLED`, `REDUCED_FREQ`,
 *   `REVISED_TIMING`; [FogOverlayEntity.fromDate]/[toDate] are inclusive
 *   `YYYY-MM-DD` bounds and [season] is the fog-season label.
 */

/** Which signal a [StopPrediction] was derived from. */
enum class PredictionBasis {
    /** Drifted from the live anchor via historical pattern (running only). */
    PATTERN,
    /** Live anchor delay carried flat (no usable pattern for this stop). */
    CARRIED,
    /** Pre-departure: historical average only, no live anchor exists. */
    SCHEDULE,
}

/** How much to trust one [StopPrediction]. */
enum class PredictionConfidence { HIGH, MED, LOW }

/**
 * Predicted delay at one future stop.
 *
 * @param stationCode Station code in route order.
 * @param predictedDelayMin Predicted delay in whole minutes (>= 0).
 * @param basis Which signal produced the number.
 * @param confidence Trust level (see [predictJourney] confidence rule).
 */
data class StopPrediction(
    val stationCode: String,
    val predictedDelayMin: Int,
    val basis: PredictionBasis,
    val confidence: PredictionConfidence,
)

/**
 * Whole-journey prediction for the remainder of a run.
 *
 * @param predictions Future stops only, in route order (empty when the
 *   train has reached the last stop or the stop list is empty).
 * @param positionKm Interpolated current km marker, null when unknown.
 *   Estimated-always: the UI must render it with a "~" prefix and never
 *   present it as a measured GPS position.
 * @param positionBetween (anchorCode, nextCode) the train is travelling
 *   between, null when unknown.
 * @param serviceAlert Hard contradiction notice (fog CANCELLED vs running),
 *   null normally.
 * @param seasonalNote Soft advisory (fog timetable in effect), null normally.
 */
data class JourneyPrediction(
    val predictions: List<StopPrediction>,
    val positionKm: Int?,
    val positionBetween: Pair<String, String>?,
    val serviceAlert: String?,
    val seasonalNote: String?,
)

/** Total pattern adjustment vs the anchor is bounded to ±45 min (see below). */
private const val MAX_DRIFT_MIN = 45

/**
 * Predicts the rest of a journey.
 *
 * Rules (each with its why):
 *
 * 1. Not started ([anchorIndex] null — pre-departure or unknown position):
 *    every stop predicts `priors[code]?.arrAvgMin ?: 0` with basis
 *    [PredictionBasis.SCHEDULE] and [PredictionConfidence.MED]
 *    ([PredictionConfidence.LOW] while a fog entry is active). Why: with no
 *    live observation the historical average is the only signal; this is what
 *    powers the between-screen "usually +N" badges.
 *
 * 2. Running (valid [anchorIndex]): for each future stop `i`,
 *    when priors exist for BOTH the anchor station and stop `i`,
 *    `drift = priorArr(i) - priorDep(anchor)` and
 *    `pred = max(0, anchorDelayMin + drift.coerceIn(-45, +45))` with basis
 *    [PredictionBasis.PATTERN]. Why: the drift captures the route's usual
 *    recovery/pickup between those points (e.g. a train that is always
 *    +18 at BVI but on time at MMCT is expected to recover, not to stay
 *    +18); the ±45 clamp bounds wild swings from stale priors, and `max(0,
 *    …)` keeps predictions in the NTES delay domain (early running shows
 *    as on time, never negative).
 *    Worked example (must hold — covered by unit test): anchor BVI dep +18
 *    with prior dep +18 and MMCT prior arr 0 → drift = 0 − 18 = −18 →
 *    pred = max(0, 18 − 18) = 0 (recovery). A naive carry would say +18.
 *
 * 3. Running without both priors → `pred = anchorDelayMin`, basis
 *    [PredictionBasis.CARRIED]. Why: with no pattern evidence the honest
 *    fallback is "the delay you see is the delay you get".
 *
 * 4. Confidence (running): `anchorAgeMin < 15` AND stops-ahead ≤ 2 → HIGH;
 *    `anchorAgeMin < 60` → MED; else LOW. A carried (pattern-less)
 *    prediction caps at MED, and an active `REVISED_TIMING` fog overlay
 *    caps at LOW. Why: fresh observations predict near stops well; stale
 *    ones don't; a fog timetable invalidates historical patterns; a carried
 *    value has no pattern evidence behind it.
 *
 * 5. Fog: an entry is active iff [todayYMD] lies in
 *    `[fog.fromDate, fog.toDate]` (inclusive; `YYYY-MM-DD` zero-padding
 *    makes lexicographic comparison chronological). `CANCELLED` on a
 *    RUNNING instance → [JourneyPrediction.serviceAlert] =
 *    `"Listed as cancelled <from>–<to> under the <season> fog program —
 *    verify before travel"`. Why the honest framing: a moving train
 *    contradicts the circular, so we surface the listing without asserting
 *    the train is cancelled (on a not-started instance there is no
 *    contradiction to flag, so no alert). `REVISED_TIMING` → seasonalNote
 *    `"Fog timetable in effect — predictions less certain"` (the timetable
 *    behind both anchor and priors shifted). `REDUCED_FREQ` → no output
 *    (frequency is irrelevant to a running instance). Unknown actions →
 *    no output.
 *
 * 6. Position: `fraction = (elapsedMin / schedSegMin)` clamped to
 *    `0..0.99`, `positionKm = anchorDist + fraction * (nextDist −
 *    anchorDist)`, `positionBetween = (anchorCode, nextCode)` using
 *    [LiveStopDto.distance]. Why the 0.99 cap: we must never claim arrival
 *    at the next stop before the event (the anchor is the last *observed*
 *    position). Any null/invalid position input (either time null,
 *    `schedSegMin ≤ 0`, no next stop, blank codes) → both position fields
 *    null. Estimated-always: the UI prefixes "~".
 *
 * 7. Never throws on weird input: empty stops → empty predictions + null
 *    position; out-of-range [anchorIndex] clamps to the last stop
 *    (negative → treated as not-started); negative [anchorDelayMin] /
 *    [anchorAgeMin] / [elapsedMin] coerce to 0; negative `DIST` coerces to
 *    0; blank station codes never match priors (→ 0 / CARRIED) and null the
 *    position pair.
 *
 * @param stops Full route stops in order ([LiveStopDto]).
 * @param anchorIndex Index of the last reached stop (see `currentStopIndex`
 *   in TrainDetailLogic.kt), null when not started / unknown.
 * @param anchorDelayMin Observed delay at the anchor in minutes.
 * @param anchorAgeMin Minutes since the anchor observation.
 * @param priors Historical averages keyed by station code.
 * @param fog Fog overlay for this train, null when none shipped.
 * @param todayYMD Today as `YYYY-MM-DD` (drives the fog window).
 * @param elapsedMin Minutes since departing the anchor (position only).
 * @param schedSegMin Scheduled anchor→next minutes (position only).
 */
fun predictJourney(
    stops: List<LiveStopDto>,
    anchorIndex: Int?,
    anchorDelayMin: Int,
    anchorAgeMin: Long,
    priors: Map<String, DelayPriorEntity>,
    fog: FogOverlayEntity?,
    todayYMD: String,
    elapsedMin: Long?,
    schedSegMin: Int?,
): JourneyPrediction {
    val fogActive = isFogActive(fog, todayYMD)
    val fogAction = if (fogActive) fog!!.action.trim().uppercase() else ""

    if (stops.isEmpty()) {
        return JourneyPrediction(
            predictions = emptyList(),
            positionKm = null,
            positionBetween = null,
            serviceAlert = null,
            // REVISED_TIMING still informs even with no stops to predict.
            seasonalNote = if (fogAction == "REVISED_TIMING") REVISED_TIMING_NOTE else null,
        )
    }

    // Clamp the anchor: negative/out-of-range can never index predictions.
    // Negative behaves like "unknown position" (not-started path); past-the
    // end clamps to the last stop so a completed run yields no future stops
    // instead of the full list.
    val anchor: Int? = when {
        anchorIndex == null || anchorIndex < 0 -> null
        anchorIndex >= stops.size -> stops.size - 1
        else -> anchorIndex
    }

    if (anchor == null) {
        val predictions = stops.map { stop ->
            StopPrediction(
                stationCode = stop.code,
                predictedDelayMin = priors[stop.code]?.arrAvgMin?.coerceAtLeast(0) ?: 0,
                basis = PredictionBasis.SCHEDULE,
                confidence = if (fogActive) PredictionConfidence.LOW else PredictionConfidence.MED,
            )
        }
        return JourneyPrediction(
            predictions = predictions,
            positionKm = null,
            positionBetween = null,
            serviceAlert = null,
            seasonalNote = if (fogAction == "REVISED_TIMING") REVISED_TIMING_NOTE else null,
        )
    }

    val anchorDelay = anchorDelayMin.coerceAtLeast(0)
    val age = anchorAgeMin.coerceAtLeast(0)
    val anchorCode = stops[anchor].code
    val anchorPrior = priors[anchorCode]
    val revised = fogAction == "REVISED_TIMING"

    val predictions = ((anchor + 1) until stops.size).map { i ->
        val stop = stops[i]
        val stopPrior = priors[stop.code]
        val ahead = i - anchor
        if (anchorPrior != null && stopPrior != null) {
            val drift = (stopPrior.arrAvgMin - anchorPrior.depAvgMin)
                .coerceIn(-MAX_DRIFT_MIN, MAX_DRIFT_MIN)
            StopPrediction(
                stationCode = stop.code,
                predictedDelayMin = (anchorDelay + drift).coerceAtLeast(0),
                basis = PredictionBasis.PATTERN,
                confidence = runningConfidence(age, ahead, hasPattern = true, revised = revised),
            )
        } else {
            StopPrediction(
                stationCode = stop.code,
                predictedDelayMin = anchorDelay,
                basis = PredictionBasis.CARRIED,
                confidence = runningConfidence(age, ahead, hasPattern = false, revised = revised),
            )
        }
    }

    val (positionKm, positionBetween) =
        interpolatePosition(stops, anchor, elapsedMin, schedSegMin)

    val serviceAlert = if (fogAction == "CANCELLED") {
        "Listed as cancelled ${fog!!.fromDate}–${fog.toDate} " +
            "under the ${fog.season} fog program — verify before travel"
    } else {
        null
    }

    return JourneyPrediction(
        predictions = predictions,
        positionKm = positionKm,
        positionBetween = positionBetween,
        serviceAlert = serviceAlert,
        seasonalNote = if (revised) REVISED_TIMING_NOTE else null,
    )
}

/** Soft advisory for an active `REVISED_TIMING` fog overlay (exact UI copy). */
private const val REVISED_TIMING_NOTE = "Fog timetable in effect — predictions less certain"

/**
 * Fog window check: active iff [fog] is non-null and [todayYMD] lies in
 * `[fromDate, toDate]` inclusive. Lexicographic comparison is chronological
 * for zero-padded `YYYY-MM-DD`; blank/garbled dates simply match nothing
 * (never throws).
 */
private fun isFogActive(fog: FogOverlayEntity?, todayYMD: String): Boolean {
    if (fog == null) return false
    val today = todayYMD.trim()
    if (today.isEmpty()) return false
    return fog.fromDate <= today && today <= fog.toDate
}

/**
 * Confidence ladder: fresh + near → HIGH, fresh-ish → MED, stale → LOW;
 * pattern-less predictions cap at MED (no pattern evidence); an active
 * `REVISED_TIMING` overlay caps everything at LOW (history is suspect).
 */
private fun runningConfidence(
    anchorAgeMin: Long,
    stopsAhead: Int,
    hasPattern: Boolean,
    revised: Boolean,
): PredictionConfidence {
    var c = if (anchorAgeMin < 15 && stopsAhead <= 2) {
        PredictionConfidence.HIGH
    } else if (anchorAgeMin < 60) {
        PredictionConfidence.MED
    } else {
        PredictionConfidence.LOW
    }
    if (!hasPattern && c == PredictionConfidence.HIGH) c = PredictionConfidence.MED
    if (revised) c = PredictionConfidence.LOW
    return c
}

/**
 * Position interpolation between the anchor stop and the next stop.
 * Returns (null, null) on any null/invalid input; distances coerce to ≥ 0
 * and the fraction clamps to 0..0.99 so we never claim arrival pre-event.
 */
private fun interpolatePosition(
    stops: List<LiveStopDto>,
    anchor: Int,
    elapsedMin: Long?,
    schedSegMin: Int?,
): Pair<Int?, Pair<String, String>?> {
    if (elapsedMin == null || schedSegMin == null || schedSegMin <= 0) return null to null
    if (anchor + 1 >= stops.size) return null to null
    val from = stops[anchor]
    val to = stops[anchor + 1]
    if (from.code.isBlank() || to.code.isBlank()) return null to null
    val fraction = (elapsedMin.coerceAtLeast(0).toDouble() / schedSegMin.toDouble())
        .coerceIn(0.0, 0.99)
    val fromKm = from.distance.coerceAtLeast(0)
    val toKm = to.distance.coerceAtLeast(0)
    val km = (fromKm + fraction * (toKm - fromKm)).roundToInt()
    return km to (from.code to to.code)
}
