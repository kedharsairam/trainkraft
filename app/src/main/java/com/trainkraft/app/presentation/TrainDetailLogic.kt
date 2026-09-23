package com.trainkraft.app.presentation

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Pure (no Android/Compose dependencies) presentation logic for the
 * train-detail (live tracking) screen.
 *
 * Data background (verified against
 * `app/src/test/resources/fixtures/live_status_12787_23sep.json`, a mid-journey
 * run: `LDSRC=170`, `TTLDIST=1079`, `TRUNST=1`, BZA `reversalNumber=1`,
 * PKO `WTTSTNS` × 5):
 * - `LiveStatusDto.runState` (`TRUNST`): 0 = yet to start, 1 = running,
 *   2 = arrived — same semantics as [com.trainkraft.app.data.NotificationPolicy].
 * - Header delay is [com.trainkraft.app.data.LiveStatusDto.delayMin] (parsed
 *   from `LDEL`); per-stop delays come from `DARR`/`DDEP` via
 *   `arrivalDelayMinutes()` / `departureDelayMinutes()`.
 * - Scheduled/actual/predicted times (`STA`/`STD`/`ETA`/`ETD`) already carry
 *   `"23-Sep"` suffixes — the UI renders them verbatim, never re-parses.
 * - `TrainRunInstanceDto.startDate` is `"DD-MMM-YYYY"` (e.g. `"21-Sep-2026"`,
 *   mixed case; the live-status date param uses the uppercased form).
 */

/** Zone of one live-timeline stop relative to the train's current position. */
enum class StopZone { PAST, CURRENT, FUTURE }

/**
 * Index of the "current" stop: the last stop with `arrived || departed`.
 *
 * - A stop with `departed == true` means the train has left it ("departed X").
 * - A stop with `arrived == true` but `departed == false` means the train is
 *   halted there ("at X") — including a source row (`ISA=false, ISD=true` is
 *   departure; a held arrival is `ISA=true, ISD=false`).
 * - Null when nothing has been reached yet (pre-departure: every row is
 *   FUTURE); when every stop is departed the last index is current (arrived:
 *   no FUTURE rows).
 */
fun currentStopIndex(arrived: List<Boolean>, departed: List<Boolean>): Int? {
    val n = minOf(arrived.size, departed.size)
    var current: Int? = null
    for (i in 0 until n) {
        if (arrived[i] || departed[i]) current = i
    }
    return current
}

/** True when the train is halted AT [index] (arrived, not yet departed). */
fun isHaltedAt(arrived: List<Boolean>, departed: List<Boolean>, index: Int): Boolean =
    arrived.getOrElse(index) { false } && !departed.getOrElse(index) { false }

/**
 * Zone per stop index for a [count]-stop timeline with [current] from
 * [currentStopIndex] (null = pre-departure → all FUTURE). Empty timeline →
 * empty list.
 */
fun classifyStopZones(count: Int, current: Int?): List<StopZone> {
    if (count <= 0) return emptyList()
    if (current == null) return List(count) { StopZone.FUTURE }
    return List(count) { i ->
        when {
            i < current -> StopZone.PAST
            i == current -> StopZone.CURRENT
            else -> StopZone.FUTURE
        }
    }
}

/** Visual tone for the answer-first headline pill (mapped to `PillTone` in UI). */
enum class HeadlineTone { DANGER, NEUTRAL, LIVE, LATE }

/** Answer-first headline: one status line + pill tone. */
data class Headline(val text: String, val tone: HeadlineTone)

/** `<=0 → "On time"`, `26 → "+26m"`, `65 → "+1h 05m"` (mirrors `formatDelay`). */
fun formatLogicDelay(delayMinutes: Int): String = when {
    delayMinutes <= 0 -> "On time"
    delayMinutes < 60 -> "+${delayMinutes}m"
    else -> {
        val h = delayMinutes / 60
        val m = delayMinutes % 60
        "+${h}h ${if (m < 10) "0$m" else "$m"}m"
    }
}

/**
 * Headline resolver, strict priority:
 * 1. Active exception message → "Cancelled" / "Diverted" / "Disrupted" (Danger).
 *    Active = non-blank and not the server's "no exceptional details" sentinel.
 * 2. `runState == 2` → "Arrived {dest} · {delay}" (Neutral).
 * 3. `runState == 0` → "Starts {journeyDate}" (Neutral).
 * 4. Running → "{n} min late · {statusText}" (Late) or [statusText] (Live).
 */
fun resolveHeadline(
    exceptionMsg: String?,
    runState: Int,
    destLabel: String,
    journeyDate: String,
    statusText: String,
    delayMin: Int,
): Headline {
    val exc = exceptionMsg?.trim().orEmpty()
    if (exc.isNotEmpty() && !exc.contains("no exceptional", ignoreCase = true)) {
        val label = when {
            exc.contains("cancel", ignoreCase = true) -> "Cancelled"
            exc.contains("divert", ignoreCase = true) -> "Diverted"
            else -> "Disrupted"
        }
        return Headline(label, HeadlineTone.DANGER)
    }
    if (runState == 2) {
        val dest = destLabel.trim()
        val text = if (dest.isEmpty()) "Arrived" else "Arrived $dest · ${formatLogicDelay(delayMin)}"
        return Headline(text, HeadlineTone.NEUTRAL)
    }
    if (runState == 0) {
        val date = journeyDate.trim()
        return Headline(
            if (date.isEmpty()) "Yet to start" else "Starts $date",
            HeadlineTone.NEUTRAL,
        )
    }
    val status = statusText.trim()
    return if (delayMin > 0) {
        val delay = "$delayMin min late"
        Headline(if (status.isEmpty()) delay else "$delay · $status", HeadlineTone.LATE)
    } else {
        Headline(if (status.isEmpty()) "Running" else status, HeadlineTone.LIVE)
    }
}

private val timeOfDayRegex = Regex("""(\d{1,2}:\d{2})""")

/**
 * Short server clock label: first `HH:MM` inside a raw NTES stamp —
 * `"23-Sep-2026 15:00" → "15:00"`, `"15:01 23-Sep" → "15:01"`.
 * Null when no clock time is present (caller falls back honestly).
 */
fun serverTimeShort(raw: String?): String? =
    raw?.let { timeOfDayRegex.find(it)?.groupValues?.getOrNull(1) }

/**
 * Honest time cell: unavailable flags, blanks and `"**UA**"` sentinels all
 * render as `"—"` — never a fabricated time. Anything else renders verbatim
 * (STA/STD strings already carry their day suffixes).
 */
fun displayTimeOrDash(unavailable: Boolean, value: String): String {
    val v = value.trim()
    return if (unavailable || v.isEmpty() || v.contains("**UA**")) "—" else v
}

/**
 * Journey-progress label: `"170 / 1079 km · 15%"`.
 * Null when [totalKm] <= 0 (the UI hides the whole progress block).
 */
fun progressLabel(coveredKm: Int, totalKm: Int): String? {
    if (totalKm <= 0) return null
    val pct = ((coveredKm.coerceAtLeast(0) * 100) / totalKm).coerceIn(0, 100)
    return "$coveredKm / $totalKm km · $pct%"
}

private val instanceDateParser: DateTimeFormatter = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("d-MMM-yyyy")
    .toFormatter(Locale.ENGLISH)
private val capsuleLabelFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)

/**
 * Instance-strip capsule label: `"21-Sep-2026" → "Mon 21"` (also accepts the
 * uppercased live-status form `"21-SEP-2026"`). Unparseable input returns
 * trimmed verbatim — never throws.
 */
fun instanceCapsuleLabel(startDate: String): String {
    val raw = startDate.trim()
    if (raw.isEmpty()) return raw
    return try {
        LocalDate.parse(raw, instanceDateParser).format(capsuleLabelFormat)
    } catch (_: Exception) {
        raw
    }
}

private val ntesDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)

/** Today's date in the NTES live-status shape (`"23-SEP-2026"`), IST zone. */
fun ntesTodayLabel(today: LocalDate = LocalDate.now(ZoneId.of("Asia/Kolkata"))): String =
    today.format(ntesDateFormat).uppercase(Locale.ENGLISH)

/** True when [instanceStartDate] is the run the live status is showing. */
fun isSelectedInstance(selectedDate: String?, instanceStartDate: String): Boolean {
    val selected = selectedDate?.trim()?.ifEmpty { null } ?: ntesTodayLabel()
    return selected.equals(instanceStartDate.trim(), ignoreCase = true)
}

/**
 * Non-stop disclosure label: `"▸ 5 non-stop stations"` / `"▾ 1 non-stop
 * station"` — singular exactly at 1 (screen-reader and visible text share
 * this; the beta's English uses the same rule).
 */
fun nonStopLabel(count: Int, expanded: Boolean): String {
    val noun = if (count == 1) "non-stop station" else "non-stop stations"
    return (if (expanded) "▾ " else "▸ ") + "$count $noun"
}
