package com.trainkraft.app.presentation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainkraft.app.data.AlarmStore
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.ScheduleStop
import com.trainkraft.app.ui.theme.KraftSpacing
import kotlinx.coroutines.launch

// Divider aligns with station text: 32.dp seq badge + 12.dp gap + 12.dp indent.
private val TimelineDividerStartPadding =
    KraftSpacing.spacing32 + KraftSpacing.spacing12 + KraftSpacing.spacing12

@Composable
fun TrainDetailScreen(
    trainNumber: String,
    onBack: () -> Unit,
    onLiveStatus: () -> Unit = {},
) {
    val context = LocalContext.current
    val factory = remember(trainNumber) {
        TrainDetailViewModel.Factory(
            context.applicationContext as Application,
            trainNumber,
        )
    }
    val viewModel: TrainDetailViewModel = viewModel(factory = factory)
    val schedule by viewModel.schedule.collectAsState()
    val train by viewModel.train.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val dbError by viewModel.dbError.collectAsState()
    val liveStatusJson by viewModel.liveStatusJson.collectAsState()
    val liveError by viewModel.liveError.collectAsState()
    val isLiveLoading by viewModel.isLiveLoading.collectAsState()

    val appContext = context.applicationContext
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Destination = last stop of the schedule. Blank until the schedule loads.
    val destinationCode = remember(schedule) { schedule.lastOrNull()?.code.orEmpty() }
    val watchingFlow = remember(trainNumber, destinationCode) {
        AlarmStore.isWatchingFlow(appContext, trainNumber, destinationCode)
    }
    val isWatching by watchingFlow.collectAsState(initial = false)

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    end = KraftSpacing.spacing16,
                    top = KraftSpacing.spacing8,
                    bottom = KraftSpacing.spacing8,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Train ${train?.trainNumber ?: trainNumber}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            dbError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dbError ?: "Database error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = { viewModel.retry() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            schedule.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No schedule found for $trainNumber",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "header") {
                        TrainHeader(
                            number = train?.trainNumber ?: trainNumber,
                            name = train?.name ?: "",
                            type = train?.type,
                            stopCount = schedule.size,
                            isLiveLoading = isLiveLoading,
                            destinationCode = destinationCode,
                            isWatching = isWatching,
                            onLiveStatus = {
                                viewModel.refreshLiveStatus()
                                onLiveStatus()
                            },
                            onWatch = {
                                if (destinationCode.isBlank()) return@TrainHeader
                                scope.launch {
                                    if (isWatching) {
                                        AlarmStore.unwatch(appContext, trainNumber, destinationCode)
                                        snackbarHostState.showSnackbar("Stopped watching $destinationCode")
                                    } else {
                                        AlarmStore.watch(appContext, trainNumber, destinationCode)
                                        snackbarHostState.showSnackbar("Watching $destinationCode")
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                    // Live layer: loading / error / raw JSON. The offline
                    // schedule below always stays visible as the fallback.
                    if (isLiveLoading || liveError != null || liveStatusJson != null) {
                        item(key = "live") {
                            LiveStatusSection(
                                isLiveLoading = isLiveLoading,
                                liveError = liveError,
                                liveStatusJson = liveStatusJson,
                                onRetry = { viewModel.refreshLiveStatus() },
                            )
                            HorizontalDivider()
                        }
                    }
                    itemsIndexed(schedule, key = { _, stop -> "stop-${stop.seq}" }) { index, stop ->
                        ScheduleStopRow(
                            stop = stop,
                            isFirst = index == 0,
                            isLast = index == schedule.lastIndex,
                        )
                        if (index != schedule.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = TimelineDividerStartPadding),
                            )
                        }
                    }
                }
            }
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(KraftSpacing.spacing16),
        )
    }
}

@Composable
private fun TrainHeader(
    number: String,
    name: String,
    type: String?,
    stopCount: Int,
    isLiveLoading: Boolean = false,
    destinationCode: String = "",
    isWatching: Boolean = false,
    onLiveStatus: () -> Unit,
    onWatch: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KraftSpacing.spacing16),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (name.isNotBlank()) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (type != null) {
                        TypeBadgeSmall(trainTypeLabel(type))
                    }
                    Text(
                        text = "$stopCount stops",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(KraftSpacing.spacing12))
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
        ) {
            Button(
                onClick = onLiveStatus,
                enabled = !isLiveLoading,
                modifier = Modifier.weight(1f),
            ) {
                if (isLiveLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.SatelliteAlt, contentDescription = null)
                }
                Spacer(Modifier.width(KraftSpacing.spacing8))
                Text(if (isLiveLoading) "Loading…" else "Live Status")
            }
            OutlinedButton(
                onClick = onWatch,
                enabled = destinationCode.isNotBlank(),
            ) {
                Icon(
                    if (isWatching) Icons.Filled.NotificationsActive
                    else Icons.Filled.NotificationsNone,
                    contentDescription = null,
                )
                Spacer(Modifier.width(KraftSpacing.spacing8))
                Text(if (isWatching) "Watching" else "Watch")
            }
        }
    }
}

@Composable
private fun LiveStatusSection(
    isLiveLoading: Boolean,
    liveError: String?,
    liveStatusJson: String?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KraftSpacing.spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
    ) {
        if (isLiveLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "Loading live status…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (liveError != null) {
            Text(
                text = liveError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Retry")
            }
        }
        if (liveStatusJson != null) {
            Text(
                text = "Live data (raw)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Text(
                    text = liveStatusJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(KraftSpacing.spacing8),
                )
            }
        }
    }
}

@Composable
private fun TypeBadgeSmall(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.spacing8,
                vertical = KraftSpacing.spacing2,
            ),
        )
    }
}

@Composable
private fun ScheduleStopRow(
    stop: ScheduleStop,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val endpointColor = when {
        isFirst -> MaterialTheme.colorScheme.tertiary
        isLast -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KraftSpacing.spacing16,
                vertical = KraftSpacing.spacing12,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Seq badge
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
        Spacer(Modifier.width(KraftSpacing.spacing12))
        // Station
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stop.code,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
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
        // Times
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Arr ${stop.arrMin?.let { GtfsTime.format(it) } ?: "--:--"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Dep ${stop.depMin?.let { GtfsTime.format(it) } ?: "--:--"}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun DayBadge(dayOffset: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "Day +$dayOffset",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.spacing8,
                vertical = KraftSpacing.spacing2,
            ),
        )
    }
}
