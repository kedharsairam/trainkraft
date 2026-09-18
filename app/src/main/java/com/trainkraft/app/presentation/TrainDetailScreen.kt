package com.trainkraft.app.presentation

import android.app.Application
import android.content.Intent

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    LaunchedEffect(autoRefresh, schedule) {
        if (autoRefresh && schedule.isNotEmpty() && liveStatusJson == null && liveError == null && !isLiveLoading) {
            viewModel.refreshLiveStatus()
        }
    }

    // Load average delay after live status loads
    LaunchedEffect(liveStatusJson) {
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
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDatePicker.value = true
                    }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Select date")
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
                        item(key = "hero") {
                            TrainHeroCard(
                                number = train?.trainNumber ?: trainNumber,
                                name = train?.name ?: "",
                                type = train?.type,
                                stopCount = schedule.size,
                                isLiveLoading = isLiveLoading,
                                hasLiveData = liveStatusJson != null && liveError == null,
                                onLiveStatus = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.refreshLiveStatus()
                                },
                            )
                        }
                        if (isLiveLoading || liveError != null || liveStatusJson != null) {
                            item(key = "live") {
                                LiveStatusCard(
                                    isLiveLoading = isLiveLoading,
                                    liveError = liveError,
                                    liveStatusJson = liveStatusJson,
                                    updatedLabel = liveUpdatedLabel,
                                    selectedDate = selectedDate,
                                    onRetry = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.refreshLiveStatus()
                                    },
                                )
                            }
                        }
                        // Coach position section
                        if (liveStatusJson != null && liveError == null) {
                            item(key = "coach-position") {
                                CoachPositionSection(json = liveStatusJson!!)
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
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
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

/**
 * Hero card — big train number, name, type badge, stop count,
 * and a full-width Live Status action with spring press.
 */
@Composable
private fun TrainHeroCard(
    number: String,
    name: String,
    type: String?,
    stopCount: Int,
    isLiveLoading: Boolean = false,
    hasLiveData: Boolean = false,
    onLiveStatus: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = KraftSprings.press(reduceMotion),
        label = "heroLivePress",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Hero))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(KraftSpacing.Spacing16),
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (name.isNotBlank()) {
            Spacer(Modifier.height(KraftSpacing.Spacing4))
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(KraftSpacing.Spacing8))
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (type != null) {
                TypeBadge(trainTypeLabel(type))
            }
            Text(
                text = "$stopCount stops",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasLiveData) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(KraftColors.AuroraGreen, CircleShape),
                )
                Text(
                    text = "Live",
                    style = MaterialTheme.typography.labelMedium,
                    color = KraftColors.AuroraGreen,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(KraftSpacing.Spacing16))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(KraftRadius.Pill))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = !isLiveLoading,
                    role = Role.Button,
                    onClickLabel = "Check live status",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLiveStatus()
                    },
                )
                .heightIn(min = KraftSpacing.TouchTarget)
                .padding(horizontal = KraftSpacing.Spacing16),
        ) {
            if (isLiveLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    Icons.Filled.SatelliteAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(KraftIconSize.Medium),
                )
            }
            Spacer(Modifier.width(KraftSpacing.Spacing8))
            Text(
                text = if (isLiveLoading) "Loading\u2026" else "Live Status",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LiveStatusCard(
    isLiveLoading: Boolean,
    liveError: String?,
    liveStatusJson: String?,
    updatedLabel: String?,
    selectedDate: String?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(KraftSpacing.Spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
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
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(KraftRadius.Pill))
                    .background(
                        MaterialTheme.colorScheme.error.copy(
                            alpha = KraftConstants.ContainerAlpha,
                        ),
                    )
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Retry live status",
                        onClick = onRetry,
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
        if (liveStatusJson != null && liveError == null && !isLiveLoading) {
            LiveParsedCard(json = liveStatusJson)
        }
    }
}
