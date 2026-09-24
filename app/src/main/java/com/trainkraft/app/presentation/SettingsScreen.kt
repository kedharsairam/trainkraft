package com.trainkraft.app.presentation

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
        ) {
            item(key = "data-header") {
                SettingsSectionHeader("Data")
            }
            item(key = "auto-refresh") {
                // Whole-row clickable: row handles toggle + haptics; Switch also toggles
                // so tapping anywhere (including the switch) works. Switch consumes
                // its own tap, parent handles the rest — single toggle per tap.
                ListItem(
                    headlineContent = { Text("Auto-refresh live status") },
                    supportingContent = {
                        Text(
                            text = if (autoRefresh) "Fetches live status when opening a train"
                            else "Live status only when you tap “Live Status”",
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
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Switch) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                SettingsStore.setAutoRefreshLive(appContext, !autoRefresh)
                            }
                        },
                )
                HorizontalDivider()
            }
            item(key = "cache-duration") {
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCacheSheet.value = true
                        },
                )
                HorizontalDivider()
            }
            item(key = "use-24h") {
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
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Switch) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                SettingsStore.setUse24h(appContext, !use24h)
                            }
                        },
                )
                HorizontalDivider()
            }

            item(key = "pnr") {
                ListItem(
                    headlineContent = { Text("Check PNR Status") },
                    supportingContent = { Text("Query Indian Railways for booking status") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPnrClick()
                        },
                )
                HorizontalDivider()
            }

            item(key = "timetable") {
                ListItem(
                    headlineContent = { Text("Timetable") },
                    supportingContent = { Text(TIMETABLE_LABEL) },
                    trailingContent = {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTimetableClick()
                        },
                )
                HorizontalDivider()
            }
            item(key = "alerts-log") {
                ListItem(
                    headlineContent = { Text("Alerts log") },
                    supportingContent = { Text("Past notifications for tracked trains") },
                    trailingContent = {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAlertsClick()
                        },
                )
                HorizontalDivider()
            }
            item(key = "help") {
                ListItem(
                    headlineContent = { Text("Help & how to use") },
                    supportingContent = { Text("Search, track, alarms, travel mode") },
                    trailingContent = {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onHelpClick()
                        },
                )
                HorizontalDivider()
            }

            item(key = "about-header") {
                SettingsSectionHeader("About")
            }
            item(key = "version") {
                ListItem(
                    headlineContent = { Text("App version") },
                    supportingContent = {
                        Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.heightIn(min = 56.dp),
                )
                HorizontalDivider()
            }
            item(key = "source") {
                ListItem(
                    headlineContent = { Text("Source code") },
                    supportingContent = { Text(SOURCE_LABEL) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uriHandler.openUri(SOURCE_URL)
                        },
                )
                HorizontalDivider()
            }
            item(key = "gtfs") {
                ListItem(
                    headlineContent = { Text("Timetable data") },
                    supportingContent = { Text("GTFS snapshot · Aug 2026") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.heightIn(min = 44.dp),
                )
                HorizontalDivider()
            }
            item(key = "license") {
                ListItem(
                    headlineContent = { Text("License") },
                    supportingContent = {
                        Text("Train schedules © Indian Railways. TrainKraft is for personal, non-commercial use.")
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.heightIn(min = 56.dp),
                )
                HorizontalDivider()
            }
            item(key = "legal-header") {
                SettingsSectionHeader("Legal")
            }
            item(key = "terms") {
                ListItem(
                    headlineContent = { Text("Terms of use") },
                    supportingContent = { Text("Personal use, unofficial app, data as-is") },
                    trailingContent = {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTermsClick()
                        },
                )
                HorizontalDivider()
            }
            item(key = "privacy") {
                ListItem(
                    headlineContent = { Text("Privacy policy") },
                    supportingContent = { Text("On-device data, no account, no analytics") },
                    trailingContent = {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPrivacyClick()
                        },
                )
                HorizontalDivider()
            }
            item(key = "licenses") {
                ListItem(
                    headlineContent = { Text("Open-source licenses") },
                    supportingContent = { Text("Libraries that power TrainKraft") },
                    trailingContent = {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLicensesClick()
                        },
                )
                HorizontalDivider()
            }
            item(key = "share-header") {
                SettingsSectionHeader("Share")
            }
            item(key = "share-app") {
                ListItem(
                    headlineContent = { Text("Share app") },
                    supportingContent = { Text("Send the GitHub link to a friend") },
                    trailingContent = {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, SOURCE_URL)
                            }
                            context.startActivity(Intent.createChooser(send, "Share TrainKraft"))
                        },
                )
                HorizontalDivider()
            }
            item(key = "star") {
                ListItem(
                    headlineContent = { Text("Star on GitHub") },
                    supportingContent = { Text(SOURCE_LABEL) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uriHandler.openUri(SOURCE_URL)
                        },
                )
                HorizontalDivider()
            }
            item(key = "report") {
                ListItem(
                    headlineContent = { Text("Report an issue") },
                    supportingContent = { Text("Suggest a feature or report a bug") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uriHandler.openUri(ISSUE_URL)
                        },
                )
                HorizontalDivider()
            }
            item(key = "bottom-spacer") {
                Spacer(Modifier.padding(KraftSpacing.Spacing16))
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
                            .clickable(role = Role.Button) {
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

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(
            horizontal = KraftSpacing.Spacing16,
            vertical = KraftSpacing.Spacing8,
        ),
    )
}
