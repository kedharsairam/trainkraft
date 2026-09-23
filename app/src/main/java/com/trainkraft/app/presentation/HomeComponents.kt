package com.trainkraft.app.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.kraft.ui.components.EmptyState
import com.kraft.ui.motion.KraftSprings
import com.kraft.ui.motion.rememberReduceMotion
import com.kraft.ui.tokens.KraftIconSize
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.data.SettingsStore

/**
 * Phase 5 home information architecture: quick-action grid, alerts status,
 * and the tracked-trains overview. Hero search card stays in SearchScreen.
 */

/** 2x2 quick-action tiles: literal labels, tonal surfaces, haptic press. */
@Composable
fun QuickActionGrid(
    onBetweenClick: () -> Unit,
    onPnrClick: () -> Unit,
    onStationBoardClick: () -> Unit,
    onTrackedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            QuickActionTile(
                label = "Trains between",
                icon = Icons.Filled.SwapHoriz,
                onClick = onBetweenClick,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                label = "PNR status",
                icon = Icons.Filled.ConfirmationNumber,
                onClick = onPnrClick,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            QuickActionTile(
                label = "Station board",
                icon = Icons.Filled.Schedule,
                onClick = onStationBoardClick,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                label = "Tracked trains",
                icon = Icons.Filled.NotificationsActive,
                onClick = onTrackedClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = KraftSprings.press(reduceMotion),
        label = "quickTilePress",
    )
    Row(
        modifier = modifier
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
                onClickLabel = label,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            )
            .heightIn(min = 88.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(KraftIconSize.Large),
        )
        Spacer(Modifier.width(KraftSpacing.Spacing12))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Quiet notification-status row. Read-only check — never requests permission
 * from home. Taps through to Settings when blocked.
 */
@Composable
fun AlertsStatusRow(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val haptics = LocalHapticFeedback.current
    val systemEnabled = remember(appContext) {
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }
    val autoRefresh by remember(appContext) {
        SettingsStore.autoRefreshLiveFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_AUTO_REFRESH_LIVE)

    val (label, tone, icon) = when {
        !systemEnabled -> Triple(
            "Alerts blocked — open settings",
            PillTone.Danger,
            Icons.Filled.NotificationsOff,
        )
        !autoRefresh -> Triple(
            "Alerts on · manual refresh",
            PillTone.Late,
            Icons.Filled.NotificationsActive,
        )
        else -> Triple(
            "Alerts on",
            PillTone.Live,
            Icons.Filled.NotificationsActive,
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                role = Role.Button,
                onClickLabel = "Open settings",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSettingsClick()
                },
            )
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(KraftIconSize.Medium),
        )
        Spacer(Modifier.width(KraftSpacing.Spacing8))
        Text(
            text = "Train alerts",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        StatusPill(label = label, tone = tone)
    }
}

/**
 * Tracked-trains overview: one row per bell in `tracked_trains`.
 * Row tap opens the train; bell tap untracks (same path as TrainDetail).
 */
@Composable
fun TrackedTrainsSection(
    tracked: List<TrackedRow>,
    onTrainClick: (String) -> Unit,
    onUntrack: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        SectionHeader(trackedSectionLabel(tracked.size))
        if (tracked.isEmpty()) {
            EmptyState(
                title = "No tracked trains yet",
                message = "Open a train and tap the bell to follow it.",
                icon = Icons.Outlined.NotificationsNone,
            )
        } else {
            tracked.forEach { row ->
                TrackedTrainRow(
                    row = row,
                    onOpen = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTrainClick(row.trainNumber)
                    },
                    onUntrack = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUntrack(row.trainNumber)
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackedTrainRow(
    row: TrackedRow,
    onOpen: () -> Unit,
    onUntrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = KraftSprings.press(reduceMotion),
        label = "trackedRowPress",
    )
    Row(
        modifier = modifier
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
                onClick = onOpen,
            )
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            )
            .heightIn(min = KraftSpacing.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.trainNumber,
                style = tabularFigures(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (!row.trainName.isNullOrBlank()) {
                Spacer(Modifier.height(KraftSpacing.Spacing2))
                Text(
                    text = row.trainName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onUntrack) {
            Icon(
                // Matches TrainDetail's tracked bell semantics.
                Icons.Filled.NotificationsActive,
                contentDescription = "Stop tracking train ${row.trainNumber}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
