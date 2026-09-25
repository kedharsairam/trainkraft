package com.trainkraft.app.presentation

import android.app.Application
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.motion.rememberReduceMotion
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing

/**
 * Live journey screen (Phase D travel mode): arm's-length glanceable GPS.
 *
 * DESIGN decisions (documented):
 * - "GPS live" uses [StatusPill], NOT [FreshnessBadge]: the FreshnessState
 *   contract is server/cache provenance honesty (NTES time vs cache age);
 *   GPS fix age is a device-local axis, so a status pill is the honest
 *   vocabulary — reusing the badge would imply server freshness we don't
 *   have. (Freshness.kt itself is untouched per scope.)
 * - Segment progress reuses [JourneyProgress] verbatim (same-package
 *   internal) — no duplicate bar. Remaining distance ("~12 km") is a GPS
 *   measurement, never a forecast.
 * - No-fix-yet state reads bare "Waiting for GPS…" — no server predictions,
 *   no fallback labels.
 * - GPS-stale (> [GPS_STALE_MS] since fix): "GPS searching…" muted, last
 *   values greyed ([TravelDisplay.dimmed]), never live-presented.
 * - Arrival pulse is static under reduce-motion ([rememberReduceMotion]).
 * - Single TalkBack announcement (speed + next + distance in one sentence via
 *   [TravelDisplay.announcement]); Stop is a ≥ 64dp bottom target.
 */
@Composable
fun TravelScreen(
    trainNumber: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val appContext = context.applicationContext
    val factory = remember(trainNumber) {
        TravelViewModel.Factory(appContext as Application, trainNumber)
    }
    val viewModel: TravelViewModel = viewModel(factory = factory)
    val display by viewModel.display.collectAsState()
    val serviceRunning by viewModel.serviceRunning.collectAsState()

    DisposableEffect(trainNumber) {
        viewModel.refreshTravelServiceState(appContext)
        viewModel.startTravelTicker()
        onDispose { viewModel.stopTravelTicker() }
    }

    Scaffold(
        topBar = {
            KraftTopBar(
                title = "Travel mode · $trainNumber",
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close travel mode")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(paddingValues)
                .padding(
                    horizontal = KraftSpacing.ScreenEdge,
                    vertical = KraftSpacing.Spacing16,
                ),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing16),
        ) {
            val frame = display
            if (frame == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Starting travel mode…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                TravelGpsBadgeRow(badge = frame.badge)
                // One merged announcement: speed + next + distance together.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = frame.announcement
                        }
                        .alpha(if (frame.dimmed) 0.6f else 1f),
                    verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                ) {
                    Text(
                        text = frame.speedText,
                        style = tabularFigures(MaterialTheme.typography.displayLarge),
                        fontWeight = FontWeight.Bold,
                        color = if (frame.dimmed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (frame.arrivedCode != null) {
                        TravelArrivalCard(code = frame.arrivedCode)
                    } else if (frame.nextStopText != null) {
                        Text(
                            text = frame.nextStopText,
                            style = tabularFigures(MaterialTheme.typography.headlineSmall),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = frame.detailText,
                        style = tabularFigures(MaterialTheme.typography.titleMedium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                frame.progress?.let { (covered, total) ->
                    JourneyProgress(coveredKm = covered, totalKm = total)
                }
                if (!serviceRunning) {
                    Text(
                        text = "GPS session not running — values above are last-known or estimates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // Thumb-reachable stop: bottom, full-width, ≥ 64dp.
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.stopTravel(appContext)
                    onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(KraftRadius.Medium),
            ) {
                Text(
                    text = "Stop",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** GPS badge: Live (green) vs searching/waiting (neutral) — never faked. */
@Composable
private fun TravelGpsBadgeRow(badge: TravelGpsBadge) {
    when (badge) {
        TravelGpsBadge.LIVE -> StatusPill(label = "GPS live", tone = PillTone.Live)
        TravelGpsBadge.SEARCHING -> StatusPill(label = "GPS searching…", tone = PillTone.Neutral)
        TravelGpsBadge.WAITING -> StatusPill(label = "Waiting for GPS", tone = PillTone.Neutral)
    }
}

/**
 * Advisory-arrival card: static dot under reduce-motion, gentle pulse
 * otherwise (the pulse is positional signal, never decorative motion).
 */
@Composable
private fun TravelArrivalCard(code: String) {
    val reduceMotion = rememberReduceMotion()
    val dotAlpha = if (reduceMotion) {
        1f
    } else {
        val infinite = rememberInfiniteTransition(label = "travelArrival")
        val pulse by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "travelArrivalPulse",
        )
        pulse
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .cardOutline(RoundedCornerShape(KraftRadius.Standard))
            .padding(KraftSpacing.Spacing16),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha),
                    CircleShape,
                ),
        )
        Text(
            text = "Arrived at $code",
            style = tabularFigures(MaterialTheme.typography.titleMedium),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}
