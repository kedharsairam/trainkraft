package com.trainkraft.app.presentation

import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.BuildConfig
import com.trainkraft.app.data.SettingsStore
import kotlinx.coroutines.launch

private const val SOURCE_URL = "https://github.com/kedharsairam/trainkraft"
private const val SOURCE_LABEL = "github.com/kedharsairam/trainkraft"
private const val ISSUE_URL = "https://github.com/kedharsairam/trainkraft/issues/new"
private const val TIMETABLE_LABEL = "Timetable · Aug 2026"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPnrClick: () -> Unit = {},
    onTimetableClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onLicensesClick: () -> Unit = {},
    onAlertsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current

    val autoRefresh by remember(appContext) {
        SettingsStore.autoRefreshLiveFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_AUTO_REFRESH_LIVE)
    val cacheHours by remember(appContext) {
        SettingsStore.cacheDurationHoursFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_CACHE_DURATION_HOURS)
    val use24h by remember(appContext) {
        SettingsStore.use24hFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_USE_24H)
    val showCacheSheet = remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            KraftTopBar(
                title = "Settings",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = KraftSpacing.ScreenEdge,
                end = KraftSpacing.ScreenEdge,
                top = KraftSpacing.Spacing8,
                bottom = KraftSpacing.Spacing24,
            ),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
        ) {
            item(key = "data-header") {
                SettingsSectionHeader("Data")
            }
            item(key = "data-group") {
                SettingsGroup {
                    // Whole-row clickable: row handles toggle + haptics; Switch also toggles
                    // so tapping anywhere (including the switch) works. Switch consumes
                    // its own tap, parent handles the rest — single toggle per tap.
                    ListItem(
                        headlineContent = { Text("Auto-refresh live status") },
                        supportingContent = {
                            Text(
                                text = if (autoRefresh) "Fetches live status when opening a train"
                                else "Live status only when you tap “Check live status”",
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = autoRefresh,
                                onCheckedChange = { enabled ->
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch {
                                        SettingsStore.setAutoRefreshLive(appContext, enabled)
                                    }
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Switch) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    SettingsStore.setAutoRefreshLive(appContext, !autoRefresh)
                                }
                            },
                    )
                    SettingsInsetDivider()
                    ListItem(
                        headlineContent = { Text("Cache duration") },
                        supportingContent = {
                            Text("Keep offline timetable data for ${SettingsStore.cacheDurationLabel(cacheHours)}")
                        },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
                            ) {
                                Text(
                                    text = SettingsStore.cacheDurationLabel(cacheHours),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button, onClickLabel = "Change cache duration") {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showCacheSheet.value = true
                            },
                    )
                    SettingsInsetDivider()
                    ListItem(
                        headlineContent = { Text("24-hour time format") },
                        supportingContent = {
                            Text(
                                text = if (use24h) "Show times as 16:55" else "Show times as 4:55 PM",
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = use24h,
                                onCheckedChange = { enabled ->
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch {
                                        SettingsStore.setUse24h(appContext, enabled)
                                    }
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Switch) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    SettingsStore.setUse24h(appContext, !use24h)
                                }
                            },
                    )
                }
            }

            item(key = "tools-header") {
                SettingsSectionHeader("Tools")
            }
            item(key = "tools-group") {
                SettingsGroup {
                    SettingsNavRow(
                        title = "Check PNR Status",
                        subtitle = "Query Indian Railways for booking status",
                        chevron = ChevronKind.External,
                        onClickLabel = "Open PNR status",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPnrClick()
                        },
                    )
                    SettingsInsetDivider()
                    SettingsNavRow(
                        title = "Timetable",
                        subtitle = TIMETABLE_LABEL,
                        chevron = ChevronKind.Next,
                        onClickLabel = "Open timetable info",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTimetableClick()
                        },
                    )
                    SettingsInsetDivider()
                    SettingsNavRow(
                        title = "Alerts log",
                        subtitle = "Past notifications for tracked trains",
                        chevron = ChevronKind.Next,
                        onClickLabel = "Open alerts log",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAlertsClick()
                        },
                    )
                    SettingsInsetDivider()
                    SettingsNavRow(
                        title = "Help & how to use",
                        subtitle = "Search, track, alarms, travel mode",
                        chevron = ChevronKind.Next,
                        onClickLabel = "Open help",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onHelpClick()
                        },
                    )
                }
            }

            item(key = "about-header") {
                SettingsSectionHeader("About")
            }
            item(key = "about-group") {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text("App version") },
                        supportingContent = {
                            Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                    SettingsInsetDivider()
                    SettingsNavRow(
                        title = "Source code",
                        subtitle = SOURCE_LABEL,
                        chevron = ChevronKind.External,
                        onClickLabel = "Open source code on GitHub",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uriHandler.openUri(SOURCE_URL)
                        },
                    )
                    SettingsInsetDivider()
                    ListItem(
                        headlineContent = { Text("Timetable data") },
                        supportingContent = { Text("GTFS snapshot · Aug 2026") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.heightIn(min = 44.dp),
                    )
                    SettingsInsetDivider()
                    ListItem(
                        headlineContent = { Text("License") },
                        supportingContent = {
                            Text("Train schedules © Indian Railways. TrainKraft is for personal, non-commercial use.")
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
            }

            item(key = "legal-header") {
                SettingsSectionHeader("Legal")
            }
            item(key = "legal-group") {
                SettingsGroup {
                    SettingsNavRow(
                        title = "Terms of use",
                        subtitle = "Personal use, unofficial app, data as-is",
                        chevron = ChevronKind.Next,
                        onClickLabel = "Open terms of use",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTermsClick()
                        },
                    )
                    SettingsInsetDivider()
                    SettingsNavRow(
                        title = "Privacy policy",
                        subtitle = "On-device data, no account, no analytics",
                        chevron = ChevronKind.Next,
                        onClickLabel = "Open privacy policy",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPrivacyClick()
                        },
                    )
                    SettingsInsetDivider()
                    SettingsNavRow(
                        title = "Open-source licenses",
                        subtitle = "Libraries that power TrainKraft",
                        chevron = ChevronKind.Next,
                        onClickLabel = "Open open-source licenses",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLicensesClick()
                        },
                    )
                }
            }

            item(key = "share-header") {
                SettingsSectionHeader("Share")
            }
            item(key = "share-group") {
                SettingsGroup {
                    SettingsNavRow(
                        title = "Share app",
                        subtitle = "Send the GitHub link to a friend",
                        chevron = ChevronKind.Share,
                        onClickLabel = "Share TrainKraft",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, SOURCE_URL)
                            }
                            context.startActivity(Intent.createChooser(send, "Share TrainKraft"))
                        },
                    )
                    SettingsInsetDivider()
                    SettingsNavRow(
                        title = "Star on GitHub",
                        subtitle = SOURCE_LABEL,
                        chevron = ChevronKind.External,
                        onClickLabel = "Open source code on GitHub",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uriHandler.openUri(SOURCE_URL)
                        },
                    )
                    SettingsInsetDivider()
                    SettingsNavRow(
                        title = "Report an issue",
                        subtitle = "Suggest a feature or report a bug",
                        chevron = ChevronKind.External,
                        onClickLabel = "Open issue reporter on GitHub",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uriHandler.openUri(ISSUE_URL)
                        },
                    )
                }
            }
        }
    }

    if (showCacheSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showCacheSheet.value = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = KraftSpacing.Spacing24),
            ) {
                // iOS-style sheet header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KraftSpacing.Spacing16, vertical = KraftSpacing.Spacing8),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Cache duration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "How long to keep offline timetable data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.Spacing8))
                SettingsStore.CACHE_DURATION_OPTIONS.forEach { hours ->
                    val selected = hours == cacheHours
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Set cache duration",
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    SettingsStore.setCacheDurationHours(appContext, hours)
                                }
                                showCacheSheet.value = false
                            }
                            .padding(horizontal = KraftSpacing.Spacing16, vertical = KraftSpacing.Spacing8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = SettingsStore.cacheDurationLabel(hours),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Spacer(Modifier.width(22.dp))
                        }
                    }
                    if (hours != SettingsStore.CACHE_DURATION_OPTIONS.last()) {
                        HorizontalDivider(modifier = Modifier.padding(start = KraftSpacing.Spacing16))
                    }
                }
                // Bottom safe padding for gesture nav
                Spacer(Modifier.padding(KraftSpacing.Spacing8))
            }
        }
    }
}

/** Grouped card: tonal surface, 16dp radius, hairline outline. */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .cardOutline(MaterialTheme.shapes.large),
    ) {
        Column { content() }
    }
}

/** Inset divider between grouped rows — never full-bleed. */
@Composable
private fun SettingsInsetDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = KraftSpacing.Spacing16))
}

private enum class ChevronKind { Next, External, Share }

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    chevron: ChevronKind,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    val icon = when (chevron) {
        ChevronKind.Next -> Icons.Filled.ChevronRight
        ChevronKind.External -> Icons.AutoMirrored.Filled.OpenInNew
        ChevronKind.Share -> Icons.Filled.Share
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = onClickLabel, onClick = onClick),
    )
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = Modifier.padding(
            start = KraftSpacing.Spacing4,
            end = KraftSpacing.Spacing4,
            top = KraftSpacing.Spacing8,
            bottom = KraftSpacing.Spacing4,
        ),
    )
}
