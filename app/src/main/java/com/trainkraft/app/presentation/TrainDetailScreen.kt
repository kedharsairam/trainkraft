package com.trainkraft.app.presentation

import android.app.Application
import android.content.Intent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.lifecycle.viewmodel.compose.viewModel

import com.kraft.ui.components.EmptyState
import com.kraft.ui.components.ErrorState
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.components.ShimmerList
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.data.LiveStopDto
import com.trainkraft.app.data.NtesFormats
import com.trainkraft.app.data.SettingsStore

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Divider aligns with station text: 32.dp seq badge + 12.dp gap + 12.dp indent.
private val TimelineDividerStartPadding =
    32.dp + KraftSpacing.Spacing12 + KraftSpacing.Spacing12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainDetailScreen(
    trainNumber: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
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
    val liveStatus by viewModel.liveStatus.collectAsState()
    val liveCachedAgeMs by viewModel.liveCachedAgeMs.collectAsState()
    val liveError by viewModel.liveError.collectAsState()
    val isLiveLoading by viewModel.isLiveLoading.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val avgDelay by viewModel.avgDelay.collectAsState()
    val avgDelayError by viewModel.avgDelayError.collectAsState()
    val isAvgDelayLoading by viewModel.isAvgDelayLoading.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    val isOfficialSchedule by viewModel.isOfficialSchedule.collectAsState()
    val instances by viewModel.instances.collectAsState()
    val exceptions by viewModel.exceptions.collectAsState()

    // Notification permission launcher (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.toggleTracking()
    }

    val appContext = context.applicationContext

    // 24h format preference
    val use24h by remember(appContext) {
        SettingsStore.use24hFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_USE_24H)

    // Auto-refresh live status
    val autoRefresh by remember(appContext) {
        SettingsStore.autoRefreshLiveFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_AUTO_REFRESH_LIVE)
    LaunchedEffect(autoRefresh, schedule) {
        if (autoRefresh && schedule.isNotEmpty() && liveStatus == null && liveError == null && !isLiveLoading) {
            viewModel.refreshLiveStatus()
        }
    }

    // Load average delay after live status loads
    LaunchedEffect(liveStatus) {
        if (liveStatus != null && avgDelay == null && !isAvgDelayLoading) {
            viewModel.loadAvgDelay()
        }
    }

    // Answer-first headline (priority: exceptions > arrived > not-started > running).
    val headline = remember(liveStatus, exceptions) {
        liveStatus?.let { live ->
            resolveHeadline(
                exceptionMsg = exceptions?.takeIf { it.hasActiveException() }?.alertMsg,
                runState = live.runState,
                destLabel = live.destName.ifBlank { live.destCode },
                journeyDate = live.journeyDate,
                statusText = live.statusText,
                delayMin = live.delayMin,
            )
        }
    }

    // Honesty badge: server LTIME first, cache age on fallback, failed otherwise.
    val freshness = remember(liveStatus, liveCachedAgeMs) {
        val live = liveStatus
        val age = liveCachedAgeMs
        when {
            live != null && age == null -> {
                val server = serverTimeShort(live.lastUpdateTime)
                    ?: serverTimeShort(live.lastUpdateShort)
                    ?: serverTimeShort(live.lastUpdate)
                FreshnessState.Live(server ?: clockLabel(System.currentTimeMillis()))
            }
            live != null && age != null ->
                FreshnessState.Cached(cachedAgeLabel(age))
            liveError != null -> FreshnessState.Failed
            else -> null
        }
    }

    // Live-timeline position (shared by the coach highlight + avg footnote).
    val liveArrived = remember(liveStatus) { liveStatus?.stops?.map { it.arrived } ?: emptyList() }
    val liveDeparted = remember(liveStatus) { liveStatus?.stops?.map { it.departed } ?: emptyList() }
    val currentLiveIndex = remember(liveArrived, liveDeparted) {
        currentStopIndex(liveArrived, liveDeparted)
    }
    val currentLiveCode = currentLiveIndex?.let { liveStatus?.stops?.getOrNull(it)?.code }

    // 7-day-average footnote for the current station (existing data only).
    val avgFootnoteMin = remember(avgDelay, currentLiveCode) {
        val code = currentLiveCode
        if (avgDelay == null || code.isNullOrBlank()) {
            null
        } else {
            avgDelay?.stops
                ?.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?.let {
                    NtesFormats.delayToMinutes(it.departureDelay)
                        ?: NtesFormats.delayToMinutes(it.arrivalDelay)
                }
                ?.takeIf { it > 0 }
        }
    }

    // Date picker dialog (fallback only — the instance strip covers dated runs).
    val showDatePicker = remember { mutableStateOf(false) }
    // Per-stop coach bottom sheet.
    var coachStop by remember { mutableStateOf<LiveStopDto?>(null) }

    Scaffold(
        topBar = {
            KraftTopBar(
                title = train?.name?.takeIf { it.isNotBlank() } ?: "Train $trainNumber",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (schedule.isNotEmpty()) {
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (!viewModel.hasNotificationPermission()) {
                                notifPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                            } else {
                                viewModel.toggleTracking()
                            }
                        }) {
                            Icon(
                                if (isTracking) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                                contentDescription = if (isTracking) "Stop tracking" else "Track live status",
                                tint = if (isTracking) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (schedule.isNotEmpty()) {
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val shareText = viewModel.buildShareText()
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share train info"))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                    }
                    // Calendar survives ONLY as fallback when the instance strip
                    // has no runs to offer (network failed silently).
                    if (instances == null) {
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showDatePicker.value = true
                        }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Select date")
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                isLoading -> {
                    ShimmerList(
                        rows = 10,
                        contentDescription = "Loading train schedule",
                        modifier = Modifier
                            .fillMaxSize()
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
                        modifier = Modifier.padding(horizontal = KraftSpacing.ScreenEdge),
                    )
                }
                schedule.isEmpty() -> {
                    EmptyState(
                        title = "No schedule found",
                        message = "We couldn't find a timetable for $trainNumber. Check the number or try again.",
                        icon = Icons.Outlined.Schedule,
                        actionLabel = "Try again",
                        onAction = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.retry()
                        },
                        modifier = Modifier.padding(horizontal = KraftSpacing.ScreenEdge),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = KraftSpacing.ScreenEdge,
                            end = KraftSpacing.ScreenEdge,
                            bottom = KraftSpacing.Spacing16,
                        ),
                        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                    ) {
                        item(key = "answer") {
                            AnswerHeader(
                                number = train?.trainNumber ?: trainNumber,
                                name = train?.name ?: "",
                                headline = headline,
                                freshness = freshness,
                                isLiveLoading = isLiveLoading,
                                onRefresh = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.refreshLiveStatus()
                                },
                            )
                        }
                        val live = liveStatus
                        if (live != null) {
                            if (live.totalDistance > 0) {
                                item(key = "progress") {
                                    JourneyProgress(
                                        coveredKm = live.distanceCoveredKm,
                                        totalKm = live.totalDistance,
                                    )
                                }
                            }
                            val runs = instances
                            if (runs != null && runs.instances.isNotEmpty()) {
                                item(key = "instances") {
                                    InstanceStrip(
                                        instances = runs,
                                        selectedDate = selectedDate,
                                        onSelect = { date ->
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.setSelectedDate(date)
                                        },
                                    )
                                }
                            }
                            val exc = exceptions
                            if (exc != null && exc.hasActiveException()) {
                                item(key = "exception") {
                                    ExceptionBanner(message = exc.alertMsg)
                                }
                            }
                            item(key = "coach-position") {
                                CoachPositionSection(
                                    data = live,
                                    highlightIndex = currentLiveIndex,
                                )
                            }
                            item(key = "timeline") {
                                LiveTimeline(
                                    stops = live.stops,
                                    onCoachClick = { stop ->
                                        if (stop.coachComposition().isNotBlank()) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            coachStop = stop
                                        }
                                    },
                                )
                            }
                            val footnote = avgFootnoteMin
                            if (footnote != null) {
                                item(key = "avg-footnote") {
                                    AvgDelayFootnote(delayMinutes = footnote)
                                }
                            }
                        } else {
                            if (isLiveLoading) {
                                item(key = "live-loading") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                                        modifier = Modifier.padding(
                                            horizontal = KraftSpacing.Spacing16,
                                            vertical = KraftSpacing.Spacing8,
                                        ),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Text(
                                            text = "Loading live status…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (liveError != null && !isLiveLoading) {
                                item(key = "live-error") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = KraftSpacing.Spacing16),
                                        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                                    ) {
                                        Text(
                                            text = liveError ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(KraftRadius.Pill))
                                                .background(
                                                    MaterialTheme.colorScheme.error.copy(
                                                        alpha = 0.12f,
                                                    ),
                                                )
                                                .clickable(
                                                    role = Role.Button,
                                                    onClickLabel = "Retry live status",
                                                    onClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        viewModel.refreshLiveStatus()
                                                    },
                                                )
                                                .heightIn(min = KraftSpacing.TouchTarget)
                                                .padding(horizontal = KraftSpacing.Spacing16),
                                        ) {
                                            Text(
                                                text = "Retry",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                            if (!isLiveLoading && liveError == null) {
                                item(key = "live-check") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(KraftRadius.Pill))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable(
                                                role = Role.Button,
                                                onClickLabel = "Check live status",
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    viewModel.refreshLiveStatus()
                                                },
                                            )
                                            .heightIn(min = KraftSpacing.TouchTarget)
                                            .padding(horizontal = KraftSpacing.Spacing16),
                                    ) {
                                        Icon(
                                            Icons.Filled.SatelliteAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(KraftSpacing.Spacing8))
                                        Text(
                                            text = "Live Status",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                        // Minimal honesty note when average-delay data can't load.
                        if (avgDelay == null && avgDelayError != null && !isAvgDelayLoading) {
                            item(key = "avg-delay-error") {
                                Text(
                                    text = avgDelayError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        horizontal = KraftSpacing.Spacing16,
                                        vertical = KraftSpacing.Spacing4,
                                    ),
                                )
                            }
                        }
                        // Offline schedule fallback (no live data): full timetable.
                        if (live == null) {
                            item(key = "stops-header") {
                                Column {
                                    SectionHeader(text = "Stops · ${schedule.size}")
                                    // Official-schedule provenance (GTFS snapshot gap).
                                    if (isOfficialSchedule) {
                                        Text(
                                            text = "Official schedule via NTES · not in the offline snapshot",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(
                                                horizontal = KraftSpacing.Spacing16,
                                            ),
                                        )
                                    }
                                }
                            }
                            items(
                                count = schedule.size,
                                key = { i -> "stop-${schedule[i].seq}" },
                            ) { index ->
                                val stop = schedule[index]
                                ScheduleStopRow(
                                    stop = stop,
                                    isFirst = index == 0,
                                    isLast = index == schedule.lastIndex,
                                    use24h = use24h,
                                )
                                if (index != schedule.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = TimelineDividerStartPadding),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        } else {
                            // Live mode keeps a quiet offline reference count.
                            item(key = "offline-note") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                                    modifier = Modifier.padding(horizontal = KraftSpacing.Spacing16),
                                ) {
                                    Icon(
                                        Icons.Outlined.SearchOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Timetable has ${schedule.size} stops · live above",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Date picker dialog (fallback when the instance strip is unavailable).
    if (showDatePicker.value) {
        DatePickerDialog(
            onDismiss = { showDatePicker.value = false },
            onDateSelected = { dateStr ->
                showDatePicker.value = false
                viewModel.setSelectedDate(dateStr)
            },
        )
    }

    // Per-stop coach composition sheet.
    coachStop?.let { stop ->
        CoachStopSheet(stop = stop, onDismiss = { coachStop = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit,
) {
    val datePickerState = rememberDatePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
                    cal.timeInMillis = millis
                    val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
                    sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                    onDateSelected(sdf.format(cal.time).uppercase(Locale.ENGLISH))
                }
            }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = "Select date for live status",
                        modifier = Modifier.padding(
                            start = KraftSpacing.Spacing16,
                            top = KraftSpacing.Spacing16,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    )
}
