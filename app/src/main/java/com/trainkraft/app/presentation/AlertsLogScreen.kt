package com.trainkraft.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kraft.ui.components.EmptyState
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.data.AlertLogEntity
import com.trainkraft.app.data.UserDatabase
import kotlinx.coroutines.launch

/**
 * On-device alert history: every notification posted by the worker, the
 * minute service, or the station-alarm receiver lands here (best-effort log
 * AFTER each post). Newest first, relative timestamps, body preview.
 * Read-only except Clear-all (haptic); navigation is [onBack] only — the
 * alerts route itself is wired in TrainKraftNavHost (peer's file).
 */
@Composable
fun AlertsLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val dao = remember(appContext) { UserDatabase.getInstance(appContext).alertLogDao() }
    val alerts by remember(dao) { dao.recent(100) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val nowEpochMs = remember { System.currentTimeMillis() }

    Scaffold(
        topBar = {
            KraftTopBar(
                title = "Alerts",
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (alerts.isNotEmpty()) {
                        TextButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { runCatching { dao.clearAll() } }
                        }) {
                            Text("Clear all")
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        if (alerts.isEmpty()) {
            EmptyState(
                title = "No alerts yet",
                message = "No alerts yet — alerts you receive will land here.",
                icon = Icons.Outlined.NotificationsNone,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = KraftSpacing.ScreenEdge),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = KraftSpacing.ScreenEdge,
                    end = KraftSpacing.ScreenEdge,
                    top = KraftSpacing.Spacing8,
                    bottom = KraftSpacing.Spacing16,
                ),
                verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
            ) {
                items(alerts, key = { "alert-${it.id}" }) { alert ->
                    AlertLogRow(alert = alert, nowEpochMs = nowEpochMs)
                }
            }
        }
    }
}

/** One log row: bold tabular train number + title + body preview + age. */
@Composable
private fun AlertLogRow(alert: AlertLogEntity, nowEpochMs: Long) {
    val age = formatAlertAge(nowEpochMs, alert.tsEpochMs)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .cardOutline(RoundedCornerShape(KraftRadius.Standard))
            .semantics {
                contentDescription =
                    "Alert for train ${alert.trainNumber}: ${alert.title}. ${alert.body}. $age."
            }
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            )
            .heightIn(min = KraftSpacing.TouchTarget),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = alert.trainNumber,
                style = tabularFigures(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(KraftSpacing.Spacing8))
            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(KraftSpacing.Spacing8))
            Text(
                text = age,
                style = tabularFigures(MaterialTheme.typography.labelSmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = alert.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Relative age for a log row ("just now", "5m ago", "3h ago", "2d ago").
 * Future timestamps clamp to "just now" (clock skew, never negative).
 * Pure — unit tested.
 */
fun formatAlertAge(nowEpochMs: Long, tsEpochMs: Long): String {
    val minutes = ((nowEpochMs - tsEpochMs).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}
