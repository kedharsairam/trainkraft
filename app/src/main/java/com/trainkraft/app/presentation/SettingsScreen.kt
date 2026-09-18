package com.trainkraft.app.presentation

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
import com.trainkraft.app.BuildConfig
import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.ui.theme.KraftSpacing
import kotlinx.coroutines.launch

private const val SOURCE_URL = "https://github.com/kedharsairam/trainkraft"
private const val SOURCE_LABEL = "github.com/kedharsairam/trainkraft"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
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
    val showCacheSheet = remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
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
                            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing4),
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
            }
            item(key = "bottom-spacer") {
                Spacer(Modifier.padding(KraftSpacing.spacing16))
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
                    .padding(bottom = KraftSpacing.spacing24),
            ) {
                // iOS-style sheet header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KraftSpacing.spacing16, vertical = KraftSpacing.spacing8),
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
                HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.spacing8))
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
                            .padding(horizontal = KraftSpacing.spacing16, vertical = KraftSpacing.spacing8),
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
                        HorizontalDivider(modifier = Modifier.padding(start = KraftSpacing.spacing16))
                    }
                }
                // Bottom safe padding for gesture nav
                Spacer(Modifier.padding(KraftSpacing.spacing8))
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
            horizontal = KraftSpacing.spacing16,
            vertical = KraftSpacing.spacing8,
        ),
    )
}
