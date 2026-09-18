package com.trainkraft.app.presentation

import android.app.Application
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
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.ScheduleStop
import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.ui.theme.KraftSpacing
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val appContext = context.applicationContext

    // Wire SettingsStore.autoRefreshLive: auto-fetch live status when enabled.
    val autoRefresh by remember(appContext) {
        SettingsStore.autoRefreshLiveFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_AUTO_REFRESH_LIVE)
    androidx.compose.runtime.LaunchedEffect(autoRefresh, schedule) {
        if (autoRefresh && schedule.isNotEmpty() && liveStatusJson == null && liveError == null && !isLiveLoading) {
            viewModel.refreshLiveStatus()
        }
    }

    // Live updated timestamp — refreshed when JSON changes.
    val liveUpdatedLabel = remember(liveStatusJson) {
        if (liveStatusJson == null) null
        else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

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
                                    onRetry = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.refreshLiveStatus()
                                    },
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
        // Recursively collect all keys from nested objects for fallback search
        fun findValue(vararg candidates: String): String? {
            val topKeys = mutableListOf<String>()
            val iter = obj.keys()
            while (iter.hasNext()) topKeys.add(iter.next())
            // direct match
            for (cand in candidates) {
                // exact case-insensitive
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
            // nested one level
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

        val delay = findValue("delay", "late", "delayMinutes", "lateMinutes", "delayInMinutes", "arrivalDelay", "departureDelay")
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
                // Avoid dumping huge nested json as single value
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
            // Primary status row — big visual indicator
            if (parsed != null) {
                // Status + delay headline
                val statusText = buildString {
                    parsed.status?.let { append(it) }
                    parsed.delay?.let {
                        if (isNotEmpty()) append(" · ")
                        append("${it}min late")
                    }
                }
                if (statusText.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
                    ) {
                        // Colored status dot
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
                // Key details in a compact grid
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

                // Expandable raw data toggle
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
        // Times - tabular nums, monospace retained only here
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Arr ${stop.arrMin?.let { GtfsTime.format(it) } ?: "--:--"}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Dep ${stop.depMin?.let { GtfsTime.format(it) } ?: "--:--"}",
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
