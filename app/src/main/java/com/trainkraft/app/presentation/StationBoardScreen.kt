package com.trainkraft.app.presentation

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kraft.ui.components.EmptyState
import com.kraft.ui.components.ErrorState
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.components.ShimmerList
import com.kraft.ui.motion.KraftSprings
import com.kraft.ui.motion.rememberReduceMotion
import com.kraft.ui.tokens.KraftColors
import com.kraft.ui.tokens.KraftConstants
import com.kraft.ui.tokens.KraftIconSize
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.SettingsStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Station departure board — live NTES rows (platform, delay, cancellations)
 * merged over the offline GTFS weekday timetable. The header badge states
 * which source you're looking at (live / cached / offline).
 * Destination from last-stop subquery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationBoardScreen(
    stationCode: String,
    onBack: () -> Unit,
    onTrainClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(stationCode) {
        StationBoardViewModel.Factory(
            context.applicationContext as Application,
            stationCode,
        )
    }
    val viewModel: StationBoardViewModel = viewModel(factory = factory)
    val station by viewModel.station.collectAsState()
    val departures by viewModel.departures.collectAsState()
    val source by viewModel.source.collectAsState()
    val sourceAgeMs by viewModel.sourceAgeMs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val dbError by viewModel.dbError.collectAsState()

    val appContext = context.applicationContext
    val use24h by remember(appContext) {
        SettingsStore.use24hFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_USE_24H)

    val haptics = LocalHapticFeedback.current

    val today = remember { LocalDate.now() }
    val formattedDate = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH))
    }
    val shortWeekday = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))
    }

    Scaffold(
        topBar = {
            KraftTopBar(
                title = station?.code ?: stationCode,
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        when {
            isLoading -> {
                ShimmerList(
                    rows = 10,
                    contentDescription = "Loading departures",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = KraftSpacing.ScreenEdge),
                )
            }
            dbError != null -> {
                ErrorState(
                    message = dbError ?: "Database error",
                    onRetry = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.retry()
                    },
                    modifier = Modifier.padding(paddingValues),
                )
            }
            departures.isEmpty() -> {
                EmptyState(
                    title = "No departures",
                    message = when (source) {
                        DataSource.LIVE, DataSource.CACHED ->
                            "No upcoming departures from ${station?.code ?: stationCode} right now."
                        else ->
                            "No trains from ${station?.code ?: stationCode} for $formattedDate. Timetables vary by weekday."
                    },
                    icon = Icons.Outlined.Train,
                    actionLabel = "Go back",
                    onAction = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    },
                    modifier = Modifier.padding(paddingValues),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = KraftSpacing.ScreenEdge,
                        end = KraftSpacing.ScreenEdge,
                        top = KraftSpacing.Spacing8,
                        bottom = KraftSpacing.Spacing16,
                    ),
                    verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                ) {
                    item(key = "header") {
                        StationBoardHeader(
                            stationCode = station?.code ?: stationCode,
                            stationName = station?.name.orEmpty(),
                            count = departures.size,
                            formattedDate = formattedDate,
                            weekday = shortWeekday,
                            source = source,
                            sourceAgeMs = sourceAgeMs,
                        )
                    }
                    items(departures, key = { it.key }) { departure ->
                        DepartureCard(
                            departure = departure,
                            use24h = use24h,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTrainClick(departure.trainNumber)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationBoardHeader(
    stationCode: String,
    stationName: String,
    count: Int,
    formattedDate: String,
    weekday: String,
    source: DataSource,
    sourceAgeMs: Long?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(KraftSpacing.Spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        Text(
            text = stationCode,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        if (stationName.isNotBlank()) {
            Text(
                text = stationName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "Trains for $formattedDate",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(KraftIconSize.Small),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (count > 0) "$count departures · $weekday schedule"
                else "No departures · $weekday schedule",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Source honesty badge: where these rows actually came from.
        val (sourceIcon, sourceLabel) = when (source) {
            DataSource.LIVE -> Icons.Filled.Wifi to "Live · NTES now"
            DataSource.CACHED -> Icons.Filled.WifiOff to (
                sourceAgeMs?.let { "Cached NTES · updated ${formatAge(it)}" }
                    ?: "Cached NTES response"
            )
            DataSource.OFFLINE -> Icons.Filled.WifiOff to "Offline schedule · GTFS snapshot Aug 2026"
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                sourceIcon,
                contentDescription = null,
                modifier = Modifier.size(KraftIconSize.Tiny),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DepartureCard(
    departure: BoardRow,
    use24h: Boolean = true,
    onClick: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = KraftSprings.press(reduceMotion),
        label = "departurePress",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open train details",
                onClick = onClick,
            )
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            )
            .heightIn(min = KraftSpacing.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(KraftIconSize.XLarge)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primary.copy(
                        alpha = KraftConstants.ContainerAlpha,
                    ),
                ),
        ) {
            Icon(
                Icons.Filled.Train,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(KraftIconSize.Medium),
            )
        }
        Spacer(Modifier.width(KraftSpacing.Spacing12))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = departure.trainNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                if (departure.dayOffset > 0) {
                    DayBadge(departure.dayOffset)
                }
            }
            val subtitle = buildString {
                append(departure.trainName)
                departure.destCode?.takeIf { it.isNotBlank() }?.let { dest ->
                    if (dest != departure.trainNumber) {
                        append("  ·  → ")
                        append(dest)
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!departure.destName.isNullOrBlank() && !departure.destCode.isNullOrBlank()) {
                val destName = departure.destName.orEmpty()
                if (!departure.trainName.contains(destName, ignoreCase = true)) {
                    Text(
                        text = "To $destName (${departure.destCode})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(KraftSpacing.Spacing12))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = departure.depMin?.let { GtfsTime.format(it, departure.dayOffset, use24h) } ?: "--:--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (departure.cancelled) {
                    MaterialTheme.colorScheme.error
                } else {
                    KraftColors.AuroraGreen
                },
                maxLines = 1,
            )
            Text(
                text = if (departure.cancelled) "Cancelled" else "Dept.",
                style = MaterialTheme.typography.labelSmall,
                color = if (departure.cancelled) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            // Live-only extras: platform and departure delay (NTES).
            departure.platform?.let { pf ->
                Text(
                    text = "PF $pf",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            departure.delayMin?.let { d ->
                if (d > 0) {
                    Text(
                        text = "$d min late",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            d <= 5 -> KraftColors.AuroraGreen
                            d <= 15 -> KraftColors.AuroraOrange
                            else -> KraftColors.AuroraRed
                        },
                    )
                }
            }
        }
    }
}
