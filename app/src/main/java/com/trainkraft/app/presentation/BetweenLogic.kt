package com.trainkraft.app.presentation

import com.trainkraft.app.data.DelayPriorEntity
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pure (no Android dependencies) presentation logic for the "Trains between
 * stations" screen.
 *
 * Data background (verified against
 * `app/src/test/resources/fixtures/trains_between_NDLS_MMCT.json`):
 * - `BetweenTrainDto.dayOfRun` is `"Daily"` or one-or-more English weekday
 *   abbreviations joined with commas, e.g. `"Mon"`, `"Mon,thu"`,
 *   `"Tue,wed,sun"`, `"Mon,tue,thu,sat"`. Case is inconsistent server-side.
 * - `BetweenTrainDto.travelTime` is `"HH:MM"` elapsed time and may exceed 24h
 *   (e.g. `"26:25"`).
 * - The DTO carries NO arrival day offset, so it is derived (see
 *   [inferArrivalDayOffset]).
 * - `TrainBtwStnJson` takes no date parameter, so per-day train counts are
 *   derived client-side from [parseDayOfRun] across the result set.
 */

/** Filter-chip label that means "no type filtering". */
const val BETWEEN_ALL_FILTER = "All"

/** Monday-first week order, matching the running-days row (M T W T F S S). */
val betweenWeekOrder: List<DayOfWeek> = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

/** Single-letter labels aligned with [betweenWeekOrder]. */
val betweenWeekLetters: List<String> = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * One enriched trains-between row for the UI.
 *
 * Times come from [com.trainkraft.app.data.BetweenResult] (GTFS minutes, or
 * NTES `DepTimeFrom`/`ArrTimeTo` parsed to minutes); the remaining fields are
 * carried over verbatim from `BetweenTrainDto` so nothing is lost in mapping:
 * `dayOfRun`, `classes` (`ClassOfTravel`, raw comma tokens), `typeDesc`
 * (`TrainTypeDesc`), `travelRaw` (`TravelTime`, kept accessible next to the
 * derived duration).
 *
 * Offline GTFS rows have no NTES metadata; the ViewModel synthesizes
 * [dayOfRun] as the queried weekday's 3-letter abbreviation (those rows were
 * already weekday-filtered server-side by the DAO query), so date counts stay
 * honest for them too.
 */
data class BetweenUiRow(
    val trainNumber: String,
    val trainName: String,
    val fromCode: String,
    val fromName: String,
    val depMin: Int,
    val depDayOffset: Int,
    val toCode: String,
    val toName: String,
    val arrMin: Int,
    val arrDayOffset: Int,
    val dayOfRun: String = "",
    val classes: String = "",
    val typeDesc: String = "",
    val travelRaw: String = "",
)

/** Client-side sort orders for the between-stations list. */
enum class BetweenSort { DEPARTURE, DURATION, ARRIVAL, SMARTEST }

/**
 * Typical-delay badge label from the pack arrival prior at the destination
 * stop. Null when [arrAvgMin] is null (no pack row → no badge, never
 * invented); `<= 0` means typically on time. Pure — unit-tested. Shared with
 * the station board (same package, no duplication).
 */
fun usualDelayBadgeLabel(arrAvgMin: Int?): String? = when {
    arrAvgMin == null -> null
    arrAvgMin <= 0 -> "usually on time"
    else -> "usually +$arrAvgMin"
}

/**
 * Arrival prior for one station out of a train's full pack prior list
 * ([com.trainkraft.app.data.PackDao.priorsForTrain] shape). Returns the
 * `arrAvgMin` of the first row matching [stationCode] (case-insensitive),
 * null when the pack has no row for this station → no badge, never invented.
 * Pure — unit-tested; used by the station board batch.
 */
fun usualDelayForStation(
    priors: List<DelayPriorEntity>,
    stationCode: String,
): Int? = priors.firstOrNull {
    it.stationCode.equals(stationCode, ignoreCase = true)
}?.arrAvgMin

private val dayTokenTable: Map<String, DayOfWeek> = mapOf(
    "mon" to DayOfWeek.MONDAY,
    "monday" to DayOfWeek.MONDAY,
    "tue" to DayOfWeek.TUESDAY,
    "tues" to DayOfWeek.TUESDAY,
    "tuesday" to DayOfWeek.TUESDAY,
    "wed" to DayOfWeek.WEDNESDAY,
    "wednesday" to DayOfWeek.WEDNESDAY,
    "thu" to DayOfWeek.THURSDAY,
    "thur" to DayOfWeek.THURSDAY,
    "thurs" to DayOfWeek.THURSDAY,
    "thursday" to DayOfWeek.THURSDAY,
    "fri" to DayOfWeek.FRIDAY,
    "friday" to DayOfWeek.FRIDAY,
    "sat" to DayOfWeek.SATURDAY,
    "saturday" to DayOfWeek.SATURDAY,
    "sun" to DayOfWeek.SUNDAY,
    "sunday" to DayOfWeek.SUNDAY,
)

/**
 * Parses an NTES `DayOfRun` value to the set of weekdays the train runs.
 *
 * - `"Daily"` (any case, surrounding whitespace tolerated) → all 7 days.
 * - Otherwise splits on commas, semicolons, slashes, pipes and whitespace, so
 *   `"Mon"`, `"Mon,Wed"`, `"Mon, Wed, Fri"`, `"Mon,thu"` and `"Tue wed"` all
 *   parse; matching is case-insensitive on the 3-letter prefix, so full names
 *   (`"Thursday"`) also work.
 * - Never throws: unrecognized tokens are ignored. A blank value (unknown
 *   schedule — should not normally reach here, offline rows get a synthesized
 *   weekday) matches every day so trains are shown rather than silently
 *   hidden; a non-blank value with zero recognized tokens matches no day.
 */
fun parseDayOfRun(raw: String): Set<DayOfWeek> {
    val value = raw.trim()
    if (value.isEmpty()) return enumValues<DayOfWeek>().toSet()
    if (value.equals("daily", ignoreCase = true)) return enumValues<DayOfWeek>().toSet()
    val out = mutableSetOf<DayOfWeek>()
    value.split(',', ';', '/', '|', ' ', '\t').forEach { token ->
        val key = token.trim().lowercase().take(3)
        dayTokenTable[key]?.let { out += it }
    }
    return out
}

/** True when [parseDayOfRun]`(raw)` contains [day]. */
fun runsOnDay(raw: String, day: DayOfWeek): Boolean = day in parseDayOfRun(raw)

/** Monday-first running mask for the seven-letter (M T W T F S S) row. */
fun runningWeekMask(raw: String): List<Boolean> {
    val days = parseDayOfRun(raw)
    return betweenWeekOrder.map { it in days }
}

/** Number of trains (given as raw `dayOfRun` values) running on [day]. */
fun countRunningOn(dayOfRuns: List<String>, day: DayOfWeek): Int =
    dayOfRuns.count { runsOnDay(it, day) }

/** Per-date running counts for [dates], in the same order. */
fun countsForDates(dayOfRuns: List<String>, dates: List<LocalDate>): List<Int> =
    dates.map { countRunningOn(dayOfRuns, it.dayOfWeek) }

/** 7 consecutive dates starting at [start] (today first). */
fun betweenWeekDates(start: LocalDate, days: Int = 7): List<LocalDate> =
    (0 until days).map { start.plusDays(it.toLong()) }

private val travelTimeRegex = Regex("""^(\d{1,3}):([0-5]\d)$""")

/**
 * `"19:12"` → 1152 total minutes. Accepts multi-day legs (`"26:25"` → 1585);
 * anything else (blank, malformed, seconds ≥ 60) → null.
 */
fun durationMinutes(travelTime: String): Int? {
    val match = travelTimeRegex.matchEntire(travelTime.trim()) ?: return null
    val hours = match.groupValues[1].toInt()
    val minutes = match.groupValues[2].toInt()
    return hours * 60 + minutes
}

/** Elapsed minutes for one corridor leg, honoring day offsets. */
fun legDurationMinutes(depMin: Int, depDayOffset: Int, arrMin: Int, arrDayOffset: Int): Int =
    (arrMin + arrDayOffset * 1440) - (depMin + depDayOffset * 1440)

/** 1152 → `"19h 12m"`; 45 → `"45m"`; 120 → `"2h"`. */
fun formatDuration(totalMinutes: Int): String {
    val total = totalMinutes.coerceAtLeast(0)
    val hours = total / 60
    val minutes = total % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

/**
 * Derives the arrival day offset for an NTES between-stations leg.
 *
 * The DTO carries no day offset, so:
 * - when [travelMinutes] parses (the normal case, including >24h legs like
 *   `"26:25"` → `"19020"`-style 27:35 legs land on +1d), the offset is exact:
 *   `(depMin + depDayOffset·1440 + travel) / 1440 − depDayOffset`;
 * - otherwise falls back to rollover inference: `arrMin <= depMin` → `+1d`
 *   (equal times count as next-day, matching the NTES midnight wrap; a same-
 *   minute arrival would otherwise read as a 0-minute trip), else `+0d`.
 */
fun inferArrivalDayOffset(
    depMin: Int,
    arrMin: Int,
    travelMinutes: Int?,
    depDayOffset: Int = 0,
): Int {
    if (travelMinutes != null) {
        return (depMin + depDayOffset * 1440 + travelMinutes) / 1440 - depDayOffset
    }
    return if (arrMin <= depMin) 1 else 0
}

/** 0 → null (no badge); 1 → `"+1d"`; 2 → `"+2d"`. */
fun arrivalPlusLabel(dayOffset: Int): String? =
    if (dayOffset <= 0) null else "+${dayOffset}d"

/**
 * Splits `ClassOfTravel` on commas, trims, drops empties, keeps the raw
 * tokens verbatim (e.g. `"SL"`, `"3A"`, `"GEN"`, `"PWD"`) — no invented
 * labels.
 */
fun splitClasses(classes: String): List<String> =
    classes.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Distinct non-blank `typeDesc` values, sorted, for the Filter chips. */
fun distinctTypeFilters(rows: List<BetweenUiRow>): List<String> =
    rows.map { it.typeDesc.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

private fun departureKey(row: BetweenUiRow): Int = row.depMin + row.depDayOffset * 1440

private fun arrivalKey(row: BetweenUiRow): Int = row.arrMin + row.arrDayOffset * 1440

private fun durationKey(row: BetweenUiRow): Int =
    legDurationMinutes(row.depMin, row.depDayOffset, row.arrMin, row.arrDayOffset)

private fun predictedArrivalKey(row: BetweenUiRow, usualDelays: Map<String, Int>): Int {
    val scheduled = arrivalKey(row)
    // Documented rule: missing badge (absent key = no pack row) sorts by
    // schedule — no invented delay. Non-positive priors mean typically on
    // time, so they add 0 rather than pulling the arrival earlier.
    val usual = usualDelays[row.trainNumber]?.coerceAtLeast(0) ?: 0
    return scheduled + usual
}

private fun comparatorFor(
    sort: BetweenSort,
    usualDelays: Map<String, Int> = emptyMap(),
): Comparator<BetweenUiRow> = when (sort) {
    BetweenSort.DEPARTURE -> compareBy(::departureKey, { it.trainNumber })
    BetweenSort.DURATION -> compareBy(::durationKey, ::departureKey, { it.trainNumber })
    BetweenSort.ARRIVAL -> compareBy(::arrivalKey, ::departureKey, { it.trainNumber })
    BetweenSort.SMARTEST -> compareBy(
        { row: BetweenUiRow -> predictedArrivalKey(row, usualDelays) },
        ::departureKey,
        { it.trainNumber },
    )
}

/**
 * Train number of the "best pick" header chip: the top card when [sort] is
 * [BetweenSort.SMARTEST] and that card has badge data (a [usualDelays] entry).
 * Null otherwise — no chip, never invented. Pure — unit-tested.
 */
fun smartestBestPickNumber(
    visible: List<BetweenUiRow>,
    sort: BetweenSort,
    usualDelays: Map<String, Int>,
): String? {
    if (sort != BetweenSort.SMARTEST || visible.isEmpty()) return null
    val top = visible.first()
    return if (usualDelays.containsKey(top.trainNumber)) top.trainNumber else null
}

/**
 * Applies the date filter, the type filter and the sort — stable
 * (`sortedWith` preserves input order on ties beyond the train-number
 * tiebreak). [typeFilter] `null` or `"All"` ([BETWEEN_ALL_FILTER]) disables
 * type filtering.
 *
 * [BetweenSort.SMARTEST] ranks by predicted arrival = scheduled arrival +
 * usual delay (priors only, no network); trains without badge data sort by
 * schedule (documented rule above), ties break by departure then number.
 */
fun applyBetweenView(
    rows: List<BetweenUiRow>,
    date: LocalDate,
    typeFilter: String?,
    sort: BetweenSort,
    usualDelays: Map<String, Int> = emptyMap(),
): List<BetweenUiRow> {
    val day = date.dayOfWeek
    val normalizedFilter = typeFilter?.takeUnless { it == BETWEEN_ALL_FILTER }
    return rows
        .asSequence()
        .filter { runsOnDay(it.dayOfRun, day) }
        .filter { normalizedFilter == null || it.typeDesc == normalizedFilter }
        .sortedWith(comparatorFor(sort, usualDelays))
        .toList()
}
