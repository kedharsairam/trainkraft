package com.trainkraft.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Shared freshness / status primitives (P3–P5 design contract).
 *
 * Honesty rule (non-negotiable): a badge never invents freshness.
 *  - `Live(serverTimeLabel)`  — prefer the NTES *server* event time (LTIME).
 *    Screens without a server stamp (train search, station board) may pass the
 *    local fetch time; it must be the actual time the payload arrived.
 *  - `Cached(ageLabel)`       — served from ResponseCache; age is measured
 *    from the cached write, never faked down.
 *  - `Failed`                 — no usable network or cache. Say so.
 */

/** Freshness provenance shown as a capsule badge. See file contract above. */
sealed interface FreshnessState {
    /** Network data; [serverTimeLabel] is e.g. `"23-Sep 14:47"` or `"14:47"`. */
    data class Live(val serverTimeLabel: String) : FreshnessState

    /** Cache fallback; [ageLabel] is e.g. `"12m ago"` (see [cachedAgeLabel]). */
    data class Cached(val ageLabel: String) : FreshnessState

    /** Nothing usable — the screen must offer a retry path. */
    data object Failed : FreshnessState
}

// Apple system palette (DESIGN.md — one accent per semantic, used sparingly).
private val FreshGreen = Color(0xFF34C759)
private val FreshAmber = Color(0xFFFF9F0A)
private val FreshRed = Color(0xFFFF3B30)

/** Tabular (non-jittering) figures — every time, km, delay, distance. */
fun tabularFigures(style: TextStyle): TextStyle = style.copy(fontFeatureSettings = "tnum")

/** `"14:47"` for [epochMs] in the system zone. */
fun clockLabel(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        .format(CLOCK_FORMAT)

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** Human cache age: `<60s → "just now"`, `12m ago`, `3h ago`, `2d ago`. */
fun cachedAgeLabel(ageMs: Long): String {
    val s = (ageMs.coerceAtLeast(0L)) / 1000
    return when {
        s < 60 -> "just now"
        s < 3_600 -> "${s / 60}m ago"
        s < 86_400 -> "${s / 3_600}h ago"
        else -> "${s / 86_400}d ago"
    }
}

/**
 * Delay label: `<=0 → "On time"`, `26 → "+26m"`, `65 → "+1h 05m"`.
 * Locale-safe (no String.format default-locale digits).
 */
fun formatDelay(delayMinutes: Int): String = when {
    delayMinutes <= 0 -> "On time"
    delayMinutes < 60 -> "+${delayMinutes}m"
    else -> {
        val h = delayMinutes / 60
        val m = delayMinutes % 60
        "+${h}h ${if (m < 10) "0$m" else "$m"}m"
    }
}

/**
 * Capsule badge: `● Live · 14:47` / `● Cached · 12m ago` / `● Couldn't refresh`.
 * Non-interactive; carries a spoken content description (TalkBack reads the
 * full sentence, not the glyphs).
 */
@Composable
fun FreshnessBadge(state: FreshnessState, modifier: Modifier = Modifier) {
    val (accent, label, spoken) = when (state) {
        is FreshnessState.Live -> Triple(
            FreshGreen,
            "Live · ${state.serverTimeLabel}",
            "Live data, updated ${state.serverTimeLabel}",
        )
        is FreshnessState.Cached -> Triple(
            FreshAmber,
            "Cached · ${state.ageLabel}",
            "Cached data from ${state.ageLabel}",
        )
        FreshnessState.Failed -> Triple(
            FreshRed,
            "Couldn't refresh",
            "Live data unavailable",
        )
    }
    StatusCapsule(
        accent = accent,
        label = label,
        spoken = spoken,
        modifier = modifier,
    )
}

/**
 * Delay chip. `<=0` renders a quiet green "On time"; amber below
 * [AMBER_DELAY_THRESHOLD_MIN] minutes; red at/above it.
 */
@Composable
fun DelayChip(delayMinutes: Int, modifier: Modifier = Modifier) {
    val accent = when {
        delayMinutes <= 0 -> FreshGreen
        delayMinutes < AMBER_DELAY_THRESHOLD_MIN -> FreshAmber
        else -> FreshRed
    }
    val label = formatDelay(delayMinutes)
    StatusCapsule(
        accent = accent,
        label = label,
        spoken = "Delay $label",
        modifier = modifier,
    )
}

/** Amber band starts here (15 min: matches "significant delay" convention). */
const val AMBER_DELAY_THRESHOLD_MIN: Int = 15

/** Visual tone for [StatusPill]. */
enum class PillTone { Live, Late, Neutral, Danger }

/** Pill for run-state headers: Running / Completed / Yet to start / Cancelled. */
@Composable
fun StatusPill(label: String, tone: PillTone, modifier: Modifier = Modifier) {
    val accent = when (tone) {
        PillTone.Live -> FreshGreen
        PillTone.Late -> FreshAmber
        PillTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        PillTone.Danger -> FreshRed
    }
    val spoken = when (tone) {
        PillTone.Live -> "Status $label"
        PillTone.Late -> "Status $label, delayed"
        PillTone.Neutral -> "Status $label"
        PillTone.Danger -> "Status $label, attention"
    }
    StatusCapsule(accent = accent, label = label, spoken = spoken, modifier = modifier)
}

@Composable
private fun StatusCapsule(
    accent: Color,
    label: String,
    spoken: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(accent.copy(alpha = 0.12f))
            .semantics { contentDescription = spoken }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = tabularFigures(MaterialTheme.typography.labelMedium),
            color = accent,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
