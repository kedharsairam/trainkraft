package com.trainkraft.app.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.BatteryExemption
import com.trainkraft.app.presentation.PermissionFlow.LiveTrackingRequirement

/**
 * First-Go-live onboarding sheet (Phase C live tracking).
 *
 * Tonal ModalBottomSheet listing the live-tracking requirements in request
 * order with their literal rationale strings and per-row system-intent
 * buttons. Shown once: the shown-flag persists in SettingsStore
 * (`live_tracking_onboarding_shown` — same boolean DataStore pattern as the
 * other settings; see [shouldShowLiveTrackingOnboarding]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingOnboardingSheet(
    requirements: List<LiveTrackingRequirement>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = KraftSpacing.Spacing16,
                    vertical = KraftSpacing.Spacing8,
                ),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            Text(
                text = "Live tracking",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Checks every minute · uses more battery.",
                style = tabularFigures(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            requirements.forEach { requirement ->
                OnboardingRequirementRow(
                    requirement = requirement,
                    onOpenSettings = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        context.startActivity(requirementSettingsIntent(context.packageName, requirement))
                    },
                )
            }
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onConfirm()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = KraftSpacing.TouchTarget),
            ) {
                Text("Start live tracking")
            }
            Spacer(Modifier.height(KraftSpacing.Spacing8))
        }
    }
}

@Composable
private fun OnboardingRequirementRow(
    requirement: LiveTrackingRequirement,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
        ) {
            Text(
                text = requirementTitle(requirement),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = PermissionFlow.liveTrackingRationales.getValue(requirement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.heightIn(min = KraftSpacing.TouchTarget),
            shape = RoundedCornerShape(KraftRadius.Pill),
        ) {
            Text("Allow")
        }
    }
}

/** Short row title per requirement (rationale body comes from the map). */
fun requirementTitle(requirement: LiveTrackingRequirement): String = when (requirement) {
    LiveTrackingRequirement.NOTIFICATIONS -> "Notifications"
    LiveTrackingRequirement.EXACT_ALARM -> "Exact alarms"
    LiveTrackingRequirement.BATTERY_EXEMPTION -> "Battery exemption"
}

/**
 * System intent per requirement: notification settings, exact-alarm access,
 * or the battery-exemption request. Pure intent-builder (package-supplied)
 * so it stays unit-testable without a Context.
 */
fun requirementSettingsIntent(
    packageName: String,
    requirement: LiveTrackingRequirement,
): Intent = when (requirement) {
    LiveTrackingRequirement.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
    LiveTrackingRequirement.EXACT_ALARM -> Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        android.net.Uri.parse("package:$packageName"),
    )
    LiveTrackingRequirement.BATTERY_EXEMPTION -> BatteryExemption.exemptionIntent(packageName)
}

/**
 * Show the sheet iff the persisted shown-flag is false (first Go-live).
 * The flag lives in SettingsStore; memory-only fallback is NOT used — the
 * DataStore boolean pattern already covers it.
 */
fun shouldShowLiveTrackingOnboarding(shownFlag: Boolean): Boolean = !shownFlag
