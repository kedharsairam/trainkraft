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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.ScheduleStop
import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.ui.theme.KraftSpacing
import org.json.JSONArray
import org.json.JSONObject
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
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(
            horizontal = KraftSpacing.spacing16,
            vertical = KraftSpacing.spacing8,
        ),
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
                TypeBadgeSmall(trainTypeLabel(type))
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

// ---- Coach Position Section ----

@Composable
private fun CoachPositionSection(json: String) {
    val parsed = remember(json) { parseCoachPosition(json) }
    if (parsed == null || parsed.isEmpty()) return

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
        // Show coach layout as horizontal scrollable chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing4),
            modifier = Modifier.fillMaxWidth(),
        ) {
            parsed.forEach { coach ->
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
        // Show class info if available
        val classInfo = remember(json) { parseCoachClass(json) }
        if (classInfo != null && classInfo.isNotEmpty()) {
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

private fun parseCoachPosition(json: String): List<String>? {
    return try {
        val obj = JSONObject(json.trim())
        // Look for STNS array and get first station's arrivalCoachPosition
        val stns = obj.optJSONArray("STNS") ?: return null
        if (stns.length() == 0) return null
        val firstStation = stns.getJSONObject(0)
        val coachPos = firstStation.optString("arrivalCoachPosition", "").trim()
        if (coachPos.isEmpty()) {
            // Try departure
            val depPos = firstStation.optString("departureCoachPosition", "").trim()
            if (depPos.isEmpty()) return null
            depPos.split("-").map { it.trim() }
        } else {
            coachPos.split("-").map { it.trim() }
        }
    } catch (_: Exception) {
        null
    }
}

private fun parseCoachClass(json: String): String? {
    return try {
        val obj = JSONObject(json.trim())
        val stns = obj.optJSONArray("STNS") ?: return null
        if (stns.length() == 0) return null
        val firstStation = stns.getJSONObject(0)
        val coachClass = firstStation.optString("arrivalCoachClass", "").trim()
        if (coachClass.isEmpty()) return null
        // Convert dash-separated class to readable format
        val parts = coachClass.split("-").map { it.trim() }
        // Group consecutive same-class entries
        val grouped = mutableListOf<Pair<String, Int>>()
        var current = parts.firstOrNull() ?: return null
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
        grouped.joinToString(" + ") { (cls, cnt) -> if (cnt > 1) "$cnt\u00d7$cls" else cls }
    } catch (_: Exception) {
        null
    }
}

// ---- Average Delay Section ----

@Composable
private fun AvgDelaySection(
    json: String?,
    isLoading: Boolean,
    use24h: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KraftSpacing.spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
    ) {
        Text(
            text = "Average delay",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "Loading\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (json != null && !isLoading) {
            val entries = remember(json) { parseAvgDelay(json) }
            if (entries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing2)) {
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
                            Spacer(Modifier.width(KraftSpacing.spacing8))
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

private fun parseAvgDelay(json: String): List<Pair<String, String>> {
    return try {
        val obj = JSONObject(json.trim())
        val list = obj.optJSONArray("vAvgDelayList") ?: return emptyList()
        val entries = mutableListOf<Pair<String, String>>()
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            val stnName = item.optString("stnName", item.optString("stn", ""))
            val arrDelay = item.optString("stnArrDelay", "").trim()
            val depDelay = item.optString("stnDepDelay", "").trim()
            val delay = when {
                arrDelay.isNotEmpty() && depDelay.isNotEmpty() -> "Arr $arrDelay / Dep $depDelay"
                arrDelay.isNotEmpty() -> "Arr $arrDelay"
                depDelay.isNotEmpty() -> "Dep $depDelay"
                else -> "On time"
            }
            entries.add(stnName to delay)
        }
        entries
    } catch (_: Exception) {
        emptyList()
    }
}

// ---- Live Parsed Card ----

private data class LiveParsed(
    val delay: String?,
    val platform: String?,
    val lastLocation: String?,
    val status: String?,
    val destination: String?,
    val source: String?,
    val date: String?,
    val fallbackEntries: List<Pair<String, String>>,
)

private fun parseLiveJson(raw: String): LiveParsed? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("{")) return null
    return try {
        val obj = JSONObject(trimmed)
        fun findValue(vararg candidates: String): String? {
            val topKeys = mutableListOf<String>()
            val iter = obj.keys()
            while (iter.hasNext()) topKeys.add(iter.next())
            for (cand in candidates) {
                topKeys.firstOrNull { it.equals(cand, ignoreCase = true) }?.let { k ->
                    val v = obj.optString(k, "").trim()
                    if (v.isNotEmpty() && v != "null") return v
                }
            }
            for (cand in candidates) {
                topKeys.firstOrNull { it.contains(cand, ignoreCase = true) }?.let { k ->
                    val v = obj.optString(k, "").trim()
                    if (v.isNotEmpty() && v != "null") return v
                }
            }
            for (k in topKeys) {
                val nested = runCatching { obj.getJSONObject(k) }.getOrNull() ?: continue
                val nKeys = mutableListOf<String>()
                val nIter = nested.keys()
                while (nIter.hasNext()) nKeys.add(nIter.next())
                for (cand in candidates) {
                    nKeys.firstOrNull { it.equals(cand, ignoreCase = true) }?.let { nk ->
                        val v = nested.optString(nk, "").trim()
                        if (v.isNotEmpty() && v != "null") return v
                    }
                }
                for (cand in candidates) {
                    nKeys.firstOrNull { it.contains(cand, ignoreCase = true) }?.let { nk ->
                        val v = nested.optString(nk, "").trim()
                        if (v.isNotEmpty() && v != "null") return v
                    }
                }
            }
            return null
        }

        // Extract delay from LDEL (numeric) field
        val ldel = obj.optString("LDEL", "").trim()
        val delay = ldel.ifEmpty { findValue("delay", "late", "delayMinutes", "lateMinutes", "delayInMinutes", "arrivalDelay", "departureDelay") }
        val platform = findValue("platform", "platformNo", "platNo", "pf")
        val lastLocation = findValue("lastLocation", "curStation", "currentStation", "currStn", "curStn", "lastStation", "currentLocation", "location", "lastStn")
        val status = findValue("status", "runningStatus", "curStatus", "trainStatus", "curStnStatus")
        val destination = findValue("DSTNN", "destination", "destStation", "dest")
        val source = findValue("SRC", "source", "srcStation", "src")
        val date = findValue("STD", "date", "runDate", "scheduleDate")

        val fallback = mutableListOf<Pair<String, String>>()
        val iter2 = obj.keys()
        while (iter2.hasNext()) {
            val k = iter2.next()
            val v = obj.opt(k)?.toString()?.trim().orEmpty()
            if (v.isNotEmpty() && v != "null" && v != "{}" && v != "[]") {
                val display = if (v.length > 120) v.take(120) + "\u2026" else v
                fallback.add(k to display)
            }
        }
        LiveParsed(delay, platform, lastLocation, status, destination, source, date, fallback)
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveParsedCard(json: String) {
    val parsed = remember(json) { parseLiveJson(json) }
    val showRaw = remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KraftSpacing.spacing12),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
        ) {
            if (parsed != null) {
                val statusText = buildString {
                    parsed.status?.let { append(it) }
                    parsed.delay?.let {
                        if (isNotEmpty()) append(" \u00b7 ")
                        append("${it}min late")
                    }
                }
                if (statusText.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
                    ) {
                        val dotColor = when {
                            parsed.delay == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            parsed.delay.toIntOrNull()?.let { it <= 5 } == true -> MaterialTheme.colorScheme.tertiary
                            parsed.delay.toIntOrNull()?.let { it <= 15 } == true -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.error
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
                parsed.platform?.let { details.add("Platform" to it) }
                parsed.lastLocation?.let { details.add("At" to it) }
                parsed.source?.let { details.add("From" to it) }
                parsed.destination?.let { details.add("To" to it) }
                parsed.date?.let { details.add("Date" to it) }

                if (details.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing4),
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

                if (parsed.fallbackEntries.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.spacing2))
                    Text(
                        text = if (showRaw.value) "Hide raw data" else "Show raw data",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRaw.value = !showRaw.value }
                            .padding(vertical = KraftSpacing.spacing4),
                    )
                    if (showRaw.value) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing2),
                        ) {
                            parsed.fallbackEntries.forEach { (k, v) ->
                                LiveRow(label = k, value = v)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = KraftSpacing.spacing8),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
