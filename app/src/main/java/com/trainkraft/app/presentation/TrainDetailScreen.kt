package com.trainkraft.app.presentation

import android.app.Application
import android.content.Intent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth

import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.lifecycle.viewmodel.compose.viewModel

import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.ui.theme.KraftSpacing

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Divider aligns with station text: 32.dp seq badge + 12.dp gap + 12.dp indent.
private val TimelineDividerStartPadding =
    KraftSpacing.spacing32 + KraftSpacing.spacing12 + KraftSpacing.spacing12

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
    val liveStatusJson by viewModel.liveStatusJson.collectAsState()
    val liveError by viewModel.liveError.collectAsState()
    val isLiveLoading by viewModel.isLiveLoading.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val avgDelayJson by viewModel.avgDelayJson.collectAsState()
    val isAvgDelayLoading by viewModel.isAvgDelayLoading.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()

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
    androidx.compose.runtime.LaunchedEffect(autoRefresh, schedule) {
        if (autoRefresh && schedule.isNotEmpty() && liveStatusJson == null && liveError == null && !isLiveLoading) {
            viewModel.refreshLiveStatus()
        }
    }

    // Load average delay after live status loads
    androidx.compose.runtime.LaunchedEffect(liveStatusJson) {
        if (liveStatusJson != null && avgDelayJson == null && !isAvgDelayLoading) {
            viewModel.loadAvgDelay()
        }
    }

    val liveUpdatedLabel = remember(liveStatusJson) {
        if (liveStatusJson == null) null
        else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    // Date picker dialog
    val showDatePicker = remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = train?.name?.takeIf { it.isNotBlank() } ?: "Train $trainNumber",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (train?.name?.isNotBlank() == true) {
                            Text(
                                text = trainNumber,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Notification toggle
                    if (schedule.isNotEmpty()) {
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (!viewModel.hasNotificationPermission()) {
                                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleTracking()
                            }
                        }) {
                            Icon(
                                if (isTracking) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                                contentDescription = if (isTracking) "Stop tracking" else "Track live status",
                            )
                        }
                    }
                    // Share button
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
                    // Date picker button
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDatePicker.value = true
                    }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Select date")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(KraftSpacing.spacing16),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dbError ?: "Database error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(KraftSpacing.spacing12))
                            Button(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.retry()
                                },
                                modifier = Modifier.heightIn(min = 44.dp),
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                schedule.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = KraftSpacing.spacing24),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No schedule found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(KraftSpacing.spacing8))
                            Text(
                                text = "We couldn't find a timetable for $trainNumber. Check the number or try again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(KraftSpacing.spacing16))
                            Button(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.retry()
                                },
                                modifier = Modifier.heightIn(min = 44.dp),
                            ) {
                                Text("Try again")
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = KraftSpacing.spacing16),
                    ) {
                        item(key = "header") {
                            TrainHeader(
                                number = train?.trainNumber ?: trainNumber,
                                name = train?.name ?: "",
                                type = train?.type,
                                stopCount = schedule.size,
                                isLiveLoading = isLiveLoading,
                                onLiveStatus = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.refreshLiveStatus()
                                },
                            )
                            HorizontalDivider()
                        }
                        if (isLiveLoading || liveError != null || liveStatusJson != null) {
                            item(key = "live") {
                                LiveStatusSection(
                                    isLiveLoading = isLiveLoading,
                                    liveError = liveError,
                                    liveStatusJson = liveStatusJson,
                                    updatedLabel = liveUpdatedLabel,
                                    use24h = use24h,
                                    selectedDate = selectedDate,
                                    onRetry = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.refreshLiveStatus()
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                        // Coach position section
                        if (liveStatusJson != null && liveError == null) {
                            item(key = "coach-position") {
                                CoachPositionSection(json = liveStatusJson!!)
                                HorizontalDivider()
                            }
                        }
                        // Average delay section
                        if (avgDelayJson != null || isAvgDelayLoading) {
                            item(key = "avg-delay") {
                                AvgDelaySection(
                                    json = avgDelayJson,
                                    isLoading = isAvgDelayLoading,
                                    use24h = use24h,
                                )
                                HorizontalDivider()
                            }
                        }
                        item(key = "stops-header") {
                            SectionHeader(text = "Stops \u00b7 ${schedule.size}")
                        }
                        itemsIndexed(schedule, key = { _, stop -> "stop-${stop.seq}" }) { index, stop ->
                            ScheduleStopRow(
                                stop = stop,
                                isFirst = index == 0,
                                isLast = index == schedule.lastIndex,
                                use24h = use24h,
                            )
                            if (index != schedule.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = TimelineDividerStartPadding),
                                )
                            }
                        }
                        item(key = "bottom-spacing") {
                            Spacer(Modifier.height(KraftSpacing.spacing8))
                        }
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker.value) {
        DatePickerDialog(
            onDismiss = { showDatePicker.value = false },
            onDateSelected = { dateStr ->
                showDatePicker.value = false
                viewModel.setSelectedDate(dateStr)
            },
        )
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
                        modifier = Modifier.padding(start = KraftSpacing.spacing16, top = KraftSpacing.spacing16),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    )
}



@Composable
private fun TrainHeader(
    number: String,
    name: String,
    type: String?,
    stopCount: Int,
    isLiveLoading: Boolean = false,
    onLiveStatus: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KraftSpacing.spacing16),
    ) {
        if (name.isNotBlank()) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(KraftSpacing.spacing4))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (type != null) {
                TypeBadge(trainTypeLabel(type))
            }
            Text(
                text = "$stopCount stops \u00b7 $number",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(KraftSpacing.spacing12))
        Button(
            onClick = onLiveStatus,
            enabled = !isLiveLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
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
            Text(if (isLiveLoading) "Loading\u2026" else "Live Status")
        }
    }
}

@Composable
private fun LiveStatusSection(
    isLiveLoading: Boolean,
    liveError: String?,
    liveStatusJson: String?,
    updatedLabel: String?,
    use24h: Boolean,
    selectedDate: String?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KraftSpacing.spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
        ) {
            Text(
                text = "Live status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (selectedDate != null) {
                Text(
                    text = selectedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (updatedLabel != null && liveStatusJson != null && liveError == null && !isLiveLoading) {
                Text(
                    text = "Updated $updatedLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isLiveLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "Loading live status\u2026",
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
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text("Retry")
            }
        }
        if (liveStatusJson != null && liveError == null && !isLiveLoading) {
            LiveParsedCard(json = liveStatusJson)
        }
    }
}












