package com.trainkraft.app.presentation

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.data.StationDeparture
import com.trainkraft.app.ui.theme.KraftSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Offline station departure board (dark-only, Apple-level polish).
 *
 * Design decisions:
 * - No NtesApi station_live endpoint exists (see NtesApi.kt) — keep
 *   "Offline schedule" note and omit a dead Live Board CTA.
 * - Destination is sourced from [StationDeparture.destCode]/[destName] via
 *   last-stop subquery in TrainDao.getStationBoard.
 * - Safe area: Scaffold(contentWindowInsets = WindowInsets.safeDrawing).
 * - Large title: LargeTopAppBar with exitUntilCollapsedScrollBehavior.
 * - Date header: "Trains for Monday, Sep 15" using LocalDate.now().
 * - Touch targets ≥ 44dp, haptics on row tap.
 * - Full empty/error/loading states with title + message + action.
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
    val isLoading by viewModel.isLoading.collectAsState()
    val dbError by viewModel.dbError.collectAsState()

    val appContext = context.applicationContext
    val use24h by remember(appContext) {
        SettingsStore.use24hFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_USE_24H)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    val haptics = LocalHapticFeedback.current

    // Date/weekday context: "Monday, Sep 15" / "Trains for Monday, Sep 15"
    val today = remember { LocalDate.now() }
    val formattedDate = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH))
    }
    val shortWeekday = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = station?.code ?: stationCode,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!station?.name.isNullOrBlank()) {
                            Text(
                                text = station?.name ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                        modifier = Modifier.size(48.dp).semantics { role = Role.Button }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            dbError != null -> {
                StationBoardErrorState(
                    message = dbError ?: "Database error",
                    onRetry = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.retry()
                    },
                    modifier = Modifier.padding(paddingValues),
                )
            }
            departures.isEmpty() -> {
                // Show header still so user sees date context, then rich empty state.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(bottom = KraftSpacing.spacing32),
                ) {
                    item(key = "header") {
                        StationBoardHeader(
                            count = 0,
                            formattedDate = formattedDate,
                            weekday = shortWeekday,
                        )
                        HorizontalDivider()
                    }
                    item(key = "empty") {
                        StationBoardEmptyState(
                            stationCode = station?.code ?: stationCode,
                            formattedDate = formattedDate,
                            onRetry = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.retry()
                            },
                            onBack = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            },
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues,
                ) {
                    item(key = "header") {
                        StationBoardHeader(
                            count = departures.size,
                            formattedDate = formattedDate,
                            weekday = shortWeekday,
                        )
                        HorizontalDivider()
                    }
                    items(departures, key = { it.tripId }) { departure ->
                        DepartureRow(
                            departure = departure,
                            use24h = use24h,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTrainClick(departure.trainNumber)
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = KraftSpacing.spacing64))
                    }
                    item(key = "footer-spacer") {
                        Spacer(Modifier.padding(bottom = KraftSpacing.spacing16))
                    }
                }
            }
        }
    }
}

@Composable
private fun StationBoardHeader(
    count: Int,
    formattedDate: String,
    weekday: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.spacing16, vertical = KraftSpacing.spacing12),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
    ) {
        // Primary date context — Apple-style section title
        Text(
            text = "Trains for $formattedDate",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (count > 0) "$count departures · $weekday schedule"
                else "No departures · $weekday schedule",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Offline note — no station_live in NtesApi, keep honest affordance
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Offline schedule · GTFS snapshot Aug 2026",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DepartureRow(
    departure: StationDeparture,
    use24h: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(
                horizontal = KraftSpacing.spacing16,
                vertical = KraftSpacing.spacing12,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.Train,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(KraftSpacing.spacing12))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
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
            // Train name + destination (terminus). Falls back to trainName alone.
            val subtitle = buildString {
                append(departure.trainName)
                val dest = departure.destCode?.takeIf { it.isNotBlank() }
                if (dest != null && dest != departure.trainNumber) {
                    append("  ·  → ")
                    append(dest)
                    departure.destName?.takeIf { it.isNotBlank() }?.let { name ->
                        // Keep concise: CODE only on tight space; name shown on larger subtitle line would wrap.
                        // We prefer CODE for density; name available for contentDescription.
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
            // If we have a destName distinct from trainName, show as second line on capable rows
            if (!departure.destName.isNullOrBlank() && !departure.destCode.isNullOrBlank()) {
                val destName = departure.destName.orEmpty()
                val destLine = "To $destName (${departure.destCode})"
                // Only show when it adds info beyond trainName to avoid duplication
                if (!departure.trainName.contains(destName, ignoreCase = true)) {
                    Text(
                        text = destLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(KraftSpacing.spacing12))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = departure.depMin?.let { GtfsTime.format(it, departure.dayOffset, use24h) } ?: "--:--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = "Dept.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StationBoardEmptyState(
    stationCode: String,
    formattedDate: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.spacing24, vertical = KraftSpacing.spacing32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing12),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.Train,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "No departures",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "No trains are scheduled from $stationCode for $formattedDate. Weekday timetables vary — try a nearby station or check again tomorrow.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(KraftSpacing.spacing8))
        Row(horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing12)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text("Go back")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text("Retry")
            }
        }
        Text(
            text = "Offline GTFS data · Aug 2026",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun StationBoardErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing16),
            modifier = Modifier.padding(KraftSpacing.spacing24),
        ) {
            Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Text(
                text = "Couldn’t load departures",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 44.dp)) {
                Text("Retry")
            }
        }
    }
}


