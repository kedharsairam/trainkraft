package com.trainkraft.app.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kraft.ui.motion.rememberReduceMotion
import com.kraft.ui.tokens.KraftColors
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.LiveStatusDto
import com.trainkraft.app.data.LiveStopDto
import com.trainkraft.app.data.ScheduleStop
import com.trainkraft.app.data.TrainInstanceDto

// ------------------------------------------------------------ answer header

private fun HeadlineTone.toPill(): PillTone = when (this) {
    HeadlineTone.DANGER -> PillTone.Danger
    HeadlineTone.NEUTRAL -> PillTone.Neutral
    HeadlineTone.LIVE -> PillTone.Live
    HeadlineTone.LATE -> PillTone.Late
}

/**
 * Answer-first header: train number (tabular bold) + name, ONE headline
 * status line with its [StatusPill], then the honesty row ([FreshnessBadge] +
 * manual refresh). [headline] is null before live data arrives (offline mode).
 */
@Composable
internal fun AnswerHeader(
    number: String,
    name: String,
    headline: Headline?,
    freshness: FreshnessState?,
    isLiveLoading: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Hero))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(KraftSpacing.Spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        Text(
            text = number,
            style = tabularFigures(MaterialTheme.typography.displayLarge),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (name.isNotBlank()) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (headline != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headline.text,
                    style = tabularFigures(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                StatusPill(label = headline.text, tone = headline.tone.toPill())
            }
        }
        if (freshness != null || isLiveLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
            ) {
                if (freshness != null) {
                    FreshnessBadge(state = freshness, modifier = Modifier.weight(1f, fill = false))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (isLiveLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh live status",
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------- journey progress

/**
 * 4dp journey-progress bar with `"170 / 1079 km · 15%"` label. Renders
 * nothing when [totalKm] <= 0 (division guard lives in the DTO; the UI hides
 * the whole block). Fill animates unless reduce-motion is on.
 */
@Composable
internal fun JourneyProgress(coveredKm: Int, totalKm: Int) {
    val label = progressLabel(coveredKm, totalKm) ?: return
    val reduceMotion = rememberReduceMotion()
    val target = (coveredKm.toFloat() / totalKm).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = target, label = "journeyProgress")
    val shown = if (reduceMotion) target else animated
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.Spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
    ) {
        Text(
            text = label,
            style = tabularFigures(MaterialTheme.typography.labelMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { shown },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(percent = 50)),
        )
    }
}

// ---------------------------------------------------------- instance strip

private fun runStateGlyph(runState: Int): String = when (runState) {
    2 -> "✓"
    1 -> "●"
    else -> "○"
}

private fun runStateSpoken(runState: Int): String = when (runState) {
    2 -> "arrived"
    1 -> "running"
    else -> "yet to start"
}

/**
 * Horizontal run-date capsules from `trainInstance()` (`vInstanceList`).
 * Tapping a capsule selects that run date (uppercased into the live-status
 * date shape). Renders nothing when there are no instances — loading/error
 * of the strip never blocks the main timeline.
 */
@Composable
internal fun InstanceStrip(
    instances: TrainInstanceDto,
    selectedDate: String?,
    onSelect: (String) -> Unit,
) {
    if (instances.instances.isEmpty()) return
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Recent runs" },
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        items(instances.instances, key = { it.startDate }) { inst ->
            val selected = isSelectedInstance(selectedDate, inst.startDate)
            val spoken = "Run starting ${inst.startDate}, ${runStateSpoken(inst.runState)}" +
                (inst.exceptionMsg.takeIf { it.isNotBlank() }?.let { ". $it" } ?: "")
            Surface(
                shape = RoundedCornerShape(KraftRadius.Pill),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .heightIn(min = KraftSpacing.TouchTarget)
                    .semantics { contentDescription = spoken }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Show run starting ${inst.startDate}",
                        onClick = { onSelect(inst.startDate.uppercase()) },
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = KraftSpacing.Spacing12,
                        vertical = KraftSpacing.Spacing8,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = runStateGlyph(inst.runState),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = instanceCapsuleLabel(inst.startDate),
                            style = tabularFigures(MaterialTheme.typography.labelLarge),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    inst.exceptionMsg.takeIf { it.isNotBlank() }?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------- exceptions banner

/** Red banner for an active service exception — message verbatim, never fabricated. */
@Composable
internal fun ExceptionBanner(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(KraftSpacing.Spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = "Check railway announcements before travel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

// ------------------------------------------------------------ live timeline

/**
 * Three-zone live timeline over [LiveStatusDto.stops]. Current position comes
 * from the ISA/ISD flags ([currentStopIndex]); every row carries a DIST right
 * rail and a spoken station + status description.
 */
@Composable
internal fun LiveTimeline(
    stops: List<LiveStopDto>,
    onCoachClick: (LiveStopDto) -> Unit,
) {
    if (stops.isEmpty()) return
    val arrived = remember(stops) { stops.map { it.arrived } }
    val departed = remember(stops) { stops.map { it.departed } }
    val current = remember(arrived, departed) { currentStopIndex(arrived, departed) }
    val zones = remember(stops.size, current) { classifyStopZones(stops.size, current) }
    val showPfNote = remember(stops, zones) {
        stops.indices.any { i ->
            zones[i] == StopZone.FUTURE && stops[i].platform.isNotBlank()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
    ) {
        stops.forEachIndexed { index, stop ->
            when (zones[index]) {
                StopZone.PAST -> PastStopRow(
                    stop = stop,
                    isFirst = index == 0,
                    isLast = index == stops.lastIndex,
                    onCoachClick = onCoachClick,
                )
                StopZone.CURRENT -> CurrentStopCard(
                    stop = stop,
                    halted = isHaltedAt(arrived, departed, index),
                    onCoachClick = onCoachClick,
                )
                StopZone.FUTURE -> FutureStopRow(
                    stop = stop,
                    isFirst = index == 0,
                    isLast = index == stops.lastIndex,
                    onCoachClick = onCoachClick,
                )
            }
            if (stop.isReversalStop()) ReversalDividerRow()
            if (stop.nonStopCount() > 0) NonStopDisclosure(stop = stop)
        }
        if (showPfNote) {
            Text(
                text = "*Platforms can change — check station displays",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = KraftSpacing.Spacing16,
                    vertical = KraftSpacing.Spacing4,
                ),
            )
        }
    }
}

@Composable
private fun TimelineRowShell(
    marker: @Composable () -> Unit,
    spoken: String,
    coachAvailable: Boolean,
    onCoachClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val clickableMod = if (coachAvailable && onCoachClick != null) {
        Modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .clickable(
                role = Role.Button,
                onClickLabel = "Show coach position",
                onClick = onCoachClick,
            )
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableMod)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        marker()
        Spacer(Modifier.width(KraftSpacing.Spacing12))
        Box(modifier = Modifier.weight(1f)) { content() }
        Spacer(Modifier.width(KraftSpacing.Spacing12))
    }
}

/** Right rail on every timeline row: DIST km, tabular, muted. */
@Composable
private fun DistanceRail(distanceKm: Int) {
    Text(
        text = "$distanceKm km",
        style = tabularFigures(MaterialTheme.typography.labelMedium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PastStopRow(
    stop: LiveStopDto,
    isFirst: Boolean,
    isLast: Boolean,
    onCoachClick: (LiveStopDto) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val arrDelay = stop.arrivalDelayMinutes()
    val depDelay = stop.departureDelayMinutes()
    val spoken = buildString {
        append("${stop.code} ${stop.name}, passed. ")
        if (isFirst) append("Started ${stop.scheduledDeparture}. ")
        else append("Scheduled arrival ${stop.scheduledArrival}, actual ${stop.estArrival}. ")
        if (!isLast) append("Scheduled departure ${stop.scheduledDeparture}, actual ${stop.estDeparture}.")
        else append("Destination.")
    }
    TimelineRowShell(
        marker = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(muted.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = muted,
                    modifier = Modifier.size(12.dp),
                )
            }
        },
        spoken = spoken,
        coachAvailable = stop.coachComposition().isNotBlank(),
        onCoachClick = { onCoachClick(stop) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing2)) {
            Text(
                text = stop.code,
                style = tabularFigures(MaterialTheme.typography.titleSmall),
                fontWeight = FontWeight.Bold,
                color = muted,
            )
            Text(
                text = stop.name,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isFirst) {
                MutedTimeLine(
                    "Starts ${displayTimeOrDash(false, stop.scheduledDeparture)}" +
                        (if (stop.departed) " · Actual ${displayTimeOrDash(false, stop.estDeparture)}" else ""),
                )
            } else {
                MutedTimeLine(
                    "Sched. ${displayTimeOrDash(false, stop.scheduledArrival)}" +
                        " · Actual ${displayTimeOrDash(false, if (stop.arrived) stop.estArrival else stop.scheduledArrival)}",
                )
            }
            if (arrDelay != null && arrDelay > 0 && !isFirst) {
                DelayChip(delayMinutes = arrDelay)
            }
            if (!isLast) {
                if (!isFirst) {
                    MutedTimeLine(
                        "Sched. ${displayTimeOrDash(false, stop.scheduledDeparture)}" +
                            " · Actual ${displayTimeOrDash(false, if (stop.departed) stop.estDeparture else stop.scheduledDeparture)}",
                    )
                }
                if (depDelay != null && depDelay > 0) {
                    DelayChip(delayMinutes = depDelay)
                }
            } else {
                MutedTimeLine("Destination")
            }
        }
        DistanceRail(stop.distance)
    }
}

@Composable
private fun MutedTimeLine(text: String) {
    Text(
        text = text,
        style = tabularFigures(MaterialTheme.typography.bodySmall),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CurrentStopCard(
    stop: LiveStopDto,
    halted: Boolean,
    onCoachClick: (LiveStopDto) -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val spoken = if (halted) {
        "Now at ${stop.code} ${stop.name}" +
            (stop.platform.takeIf { it.isNotBlank() }?.let { ", platform $it" } ?: "")
    } else {
        "Now. Departed ${stop.code} ${stop.name} at ${stop.estDeparture}"
    }
    val dotAlpha = if (reduceMotion) {
        1f
    } else {
        val infinite = rememberInfiniteTransition(label = "currentDot")
        val pulse by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "currentDotPulse",
        )
        pulse
    }
    Surface(
        shape = RoundedCornerShape(KraftRadius.Standard),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (stop.coachComposition().isNotBlank()) {
                        Modifier
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(KraftRadius.Standard))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Show coach position",
                                onClick = { onCoachClick(stop) },
                            )
                    } else {
                        Modifier
                    },
                )
                .padding(KraftSpacing.Spacing16),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            KraftColors.AuroraGreen.copy(alpha = dotAlpha),
                            CircleShape,
                        ),
                )
                Text(
                    text = if (halted) {
                        "Now at ${stop.name.ifBlank { stop.code }}" +
                            (stop.platform.takeIf { it.isNotBlank() }?.let { " · PF$it" } ?: "")
                    } else {
                        "Now · departed ${stop.name.ifBlank { stop.code }} " +
                            displayTimeOrDash(false, stop.estDeparture)
                    },
                    style = tabularFigures(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
            ) {
                Text(
                    text = stop.code,
                    style = tabularFigures(MaterialTheme.typography.labelLarge),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                stop.platform.takeIf { it.isNotBlank() }?.let { pf ->
                    TypeBadge(text = "PF $pf")
                }
                val delay = (if (halted) stop.arrivalDelayMinutes() else stop.departureDelayMinutes())
                    ?: stop.arrivalDelayMinutes()
                if (delay != null && delay > 0) {
                    DelayChip(delayMinutes = delay)
                }
                Spacer(Modifier.weight(1f))
                DistanceRail(stop.distance)
            }
        }
    }
}

@Composable
private fun FutureStopRow(
    stop: LiveStopDto,
    isFirst: Boolean,
    isLast: Boolean,
    onCoachClick: (LiveStopDto) -> Unit,
) {
    val pfSuffix = stop.platform.takeIf { it.isNotBlank() }?.let { " · PF$it*" } ?: ""
    // Departure prediction for intermediates, arrival for the destination;
    // "Starts" for a yet-to-depart source.
    val mainLine: String
    val delay: Int?
    val delayKnown: Boolean
    when {
        isFirst -> {
            mainLine = "Starts ${displayTimeOrDash(stop.departureUnavailable(), stop.scheduledDeparture)}"
            delay = stop.departureDelayMinutes()
            delayKnown = !stop.departureUnavailable()
        }
        isLast -> {
            mainLine = "Arrives ${displayTimeOrDash(stop.arrivalUnavailable(), stop.estArrival.ifBlank { stop.scheduledArrival })}"
            delay = stop.arrivalDelayMinutes()
            delayKnown = !stop.arrivalUnavailable()
        }
        else -> {
            mainLine = "Exp. ${displayTimeOrDash(stop.departureUnavailable(), stop.estDeparture.ifBlank { stop.scheduledDeparture })}"
            delay = stop.departureDelayMinutes() ?: stop.arrivalDelayMinutes()
            delayKnown = !stop.departureUnavailable()
        }
    }
    val spoken = buildString {
        append("${stop.code} ${stop.name}, upcoming. $mainLine. ")
        if (delayKnown && delay != null) append(if (delay > 0) "$delay minutes late." else "On time.")
        else append("Time not available.")
        if (isLast) append(" Destination.")
    }
    TimelineRowShell(
        marker = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        },
        spoken = spoken,
        coachAvailable = stop.coachComposition().isNotBlank(),
        onCoachClick = { onCoachClick(stop) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing2)) {
            Text(
                text = stop.code,
                style = tabularFigures(MaterialTheme.typography.titleSmall),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stop.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
            ) {
                Text(
                    text = mainLine + pfSuffix,
                    style = tabularFigures(MaterialTheme.typography.bodySmall),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (delayKnown && delay != null) {
                    DelayChip(delayMinutes = delay)
                }
            }
        }
        DistanceRail(stop.distance)
    }
}

// -------------------------------------------------- non-stop disclosure

/**
 * Quiet disclosure after a stop with [LiveStopDto.nonStopCount] > 0, collapsed
 * by default (IRCTC-beta parity). Expands inline to WTT minis (code + name +
 * sched time + km). Expansion state is per-row, keyed by station code.
 */
@Composable
private fun NonStopDisclosure(stop: LiveStopDto) {
    var expanded by remember(stop.code) { mutableStateOf(false) }
    val count = stop.nonStopCount()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = KraftSpacing.Spacing32),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(KraftRadius.Standard))
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Collapse non-stop stations" else "Expand non-stop stations",
                    onClick = { expanded = !expanded },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = if (expanded) {
                        "Hide ${nonStopLabel(count, expanded = true).substringAfter("▾ ")} after ${stop.code}"
                    } else {
                        "Show ${nonStopLabel(count, expanded = false).substringAfter("▸ ")} after ${stop.code}"
                    }
                }
                .padding(
                    horizontal = KraftSpacing.Spacing16,
                    vertical = KraftSpacing.Spacing8,
                ),
        ) {
            Text(
                text = nonStopLabel(count, expanded),
                style = tabularFigures(MaterialTheme.typography.labelMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = KraftSpacing.Spacing16,
                        end = KraftSpacing.Spacing16,
                        bottom = KraftSpacing.Spacing8,
                    ),
                verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing2),
            ) {
                stop.nonStoppingStations.forEach { wtt ->
                    val time = wtt.scheduledArrival.ifBlank { wtt.scheduledDeparture }
                    Text(
                        text = "${wtt.code} · ${wtt.name} · $time · ${wtt.distance} km",
                        style = tabularFigures(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------ reversal divider

/** Direction-reversal marker from the LIVE `reversalNumber` (not schedule data). */
@Composable
private fun ReversalDividerRow() {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                KraftSpacing.Spacing8,
                Alignment.CenterHorizontally,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = KraftSpacing.Spacing8)
                .semantics { contentDescription = "Train reverses direction here" },
        ) {
            Icon(
                Icons.Filled.SwapVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Train reverses direction here",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ------------------------------------------------------------------ coach

@Composable
private fun CoachTokenRow(tokens: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
        modifier = Modifier.fillMaxWidth(),
    ) {
        tokens.forEach { coach ->
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Text(
                    text = coach,
                    style = tabularFigures(MaterialTheme.typography.labelSmall)
                        .copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
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

/**
 * Top-level coach section. Shows the CURRENT stop's composition when
 * [highlightIndex] points at a stop that carries one, otherwise falls back to
 * the first stop with a composition (the old behavior — source once assigned;
 * hidden for trains that haven't started).
 */
@Composable
internal fun CoachPositionSection(data: LiveStatusDto, highlightIndex: Int? = null) {
    val stop = highlightIndex?.let { data.stops.getOrNull(it) }
        ?.takeIf { it.coachComposition().isNotBlank() }
        ?: data.stops.firstOrNull {
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
        CoachTokenRow(composition)
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

/**
 * Per-stop coach bottom sheet: that stop's arrival + departure coach strings,
 * monospace/tabular as in the top-level section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachStopSheet(stop: LiveStopDto, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = KraftSpacing.Spacing16,
                    vertical = KraftSpacing.Spacing8,
                ),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            Text(
                text = "Coach position · ${stop.code} ${stop.name}".trim(),
                style = tabularFigures(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.SemiBold,
            )
            CoachLabeledComposition(label = "Arrival", raw = stop.arrivalCoachPosition)
            CoachLabeledComposition(label = "Departure", raw = stop.departureCoachPosition)
            Spacer(Modifier.height(KraftSpacing.Spacing16))
        }
    }
}

@Composable
private fun CoachLabeledComposition(label: String, raw: String) {
    val tokens = raw.split("-").map { it.trim() }.filter { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tokens.isEmpty()) {
            Text(
                text = "—",
                style = tabularFigures(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CoachTokenRow(tokens)
        }
    }
}

// -------------------------------------------------------- avg-delay note

/** Muted 7-day-average footnote under the timeline (existing data only). */
@Composable
internal fun AvgDelayFootnote(delayMinutes: Int) {
    Text(
        text = "Typically +$delayMinutes min here · 7-day avg",
        style = tabularFigures(MaterialTheme.typography.bodySmall),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = KraftSpacing.Spacing16,
            vertical = KraftSpacing.Spacing4,
        ),
    )
}

// ------------------------------------------------------- offline schedule

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
                style = tabularFigures(MaterialTheme.typography.labelMedium),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
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
                    style = tabularFigures(MaterialTheme.typography.titleSmall),
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
                style = tabularFigures(MaterialTheme.typography.bodySmall)
                    .copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Dep ${stop.depMin?.let { GtfsTime.format(it, use24h = use24h) } ?: "--:--"}",
                style = tabularFigures(MaterialTheme.typography.bodySmall)
                    .copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
    }
}
