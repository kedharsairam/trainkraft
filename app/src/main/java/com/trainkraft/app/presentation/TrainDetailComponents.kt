package com.trainkraft.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kraft.ui.tokens.KraftColors
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.data.AvgDelayDto
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.LiveStatusDto
import com.trainkraft.app.data.ScheduleStop

/**
 * Live-status body rendered straight from the strict [LiveStatusDto] — no raw
 * JSON guessing. Platform comes from the next unreached stop (the top-level
 * payload has no platform key; the old scanner could never find one), status
 * from CPOS, delay minutes from LDEL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveStatusContent(data: LiveStatusDto) {
    Surface(
        shape = RoundedCornerShape(KraftRadius.Standard),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KraftSpacing.Spacing16),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            val statusText = buildString {
                if (data.statusText.isNotBlank()) append(data.statusText.trim())
                if (data.delayMin > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${data.delayMin}min late")
                }
            }
            if (statusText.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                ) {
                    // Delay severity: green ≤5min, orange ≤15min, red beyond.
                    val dotColor = when {
                        data.delayMin <= 5 -> KraftColors.AuroraGreen
                        data.delayMin <= 15 -> KraftColors.AuroraOrange
                        else -> KraftColors.AuroraRed
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(dotColor, CircleShape),
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            val details = mutableListOf<Pair<String, String>>()
            data.nextUnreachedStop()?.platform?.takeIf { it.isNotBlank() }
                ?.let { details.add("Platform" to it) }
            data.lastStationName.takeIf { it.isNotBlank() }?.let { details.add("At" to it) }
            data.nextStationName.takeIf { it.isNotBlank() }?.let { details.add("Next" to it) }
            data.sourceName.ifBlank { data.sourceCode }
                .takeIf { it.isNotBlank() }?.let { details.add("From" to it) }
            data.destName.ifBlank { data.destCode }
                .takeIf { it.isNotBlank() }?.let { details.add("To" to it) }
            data.journeyDate.takeIf { it.isNotBlank() }?.let { details.add("Date" to it) }

            if (details.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
                ) {
                    details.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(64.dp),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoachPositionSection(data: LiveStatusDto) {
    // First stop that actually carries a composition (source once assigned;
    // blank for trains that haven't started — section hides itself then).
    val stop = data.stops.firstOrNull {
        it.arrivalCoachPosition.isNotBlank() || it.departureCoachPosition.isNotBlank()
    } ?: return
    val composition = stop.coachComposition()
        .split("-")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (composition.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KraftSpacing.Spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Coach position",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
            modifier = Modifier.fillMaxWidth(),
        ) {
            composition.forEach { coach ->
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        text = coach,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }
        // Arrival composition preferred; departure class list at the source.
        val classInfo = coachClassSummary(
            stop.arrivalCoachClass.ifBlank { stop.departureCoachClass },
        )
        if (classInfo != null) {
            Text(
                text = classInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "B1-B1-B2…" → "2×B1 + B2" (same grouping the raw parser did). */
private fun coachClassSummary(raw: String): String? {
    val parts = raw.trim().split("-").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val grouped = mutableListOf<Pair<String, Int>>()
    var current = parts.first()
    var count = 1
    for (i in 1 until parts.size) {
        if (parts[i] == current) {
            count++
        } else {
            grouped.add(current to count)
            current = parts[i]
            count = 1
        }
    }
    grouped.add(current to count)
    return grouped.joinToString(" + ") { (cls, cnt) -> if (cnt > 1) "$cnt×$cls" else cls }
}

@Composable
internal fun AvgDelaySection(
    data: AvgDelayDto?,
    isLoading: Boolean,
    use24h: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KraftSpacing.Spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        Text(
            text = "Average delay",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (data != null && !isLoading) {
            val entries = remember(data) {
                data.stops.map { stn ->
                    val arr = stn.arrivalDelay.trim()
                    val dep = stn.departureDelay.trim()
                    val delay = when {
                        arr.isNotEmpty() && dep.isNotEmpty() -> "Arr $arr / Dep $dep"
                        arr.isNotEmpty() -> "Arr $arr"
                        dep.isNotEmpty() -> "Dep $dep"
                        else -> "On time"
                    }
                    (stn.name.ifBlank { stn.code }) to delay
                }
            }
            if (entries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing2)) {
                    entries.take(10).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = entry.first,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(100.dp),
                            )
                            Spacer(Modifier.width(KraftSpacing.Spacing8))
                            Text(
                                text = entry.second,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScheduleStopRow(
    stop: ScheduleStop,
    isFirst: Boolean,
    isLast: Boolean,
    use24h: Boolean = true,
) {
    val endpointColor = when {
        isFirst -> MaterialTheme.colorScheme.tertiary
        isLast -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(endpointColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stop.seq.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(KraftSpacing.Spacing12))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stop.code,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isFirst || isLast) endpointColor
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (stop.dayOffset > 0) {
                    DayBadge(stop.dayOffset)
                }
            }
            Text(
                text = stop.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Arr ${stop.arrMin?.let { GtfsTime.format(it, use24h = use24h) } ?: "--:--"}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Dep ${stop.depMin?.let { GtfsTime.format(it, use24h = use24h) } ?: "--:--"}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
