package com.trainkraft.app.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import com.trainkraft.app.BuildConfig
import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.ui.theme.KraftSpacing
import kotlinx.coroutines.launch

private const val SOURCE_URL = "https://github.com/kedharsairam/trainkraft"
private const val SOURCE_LABEL = "github.com/kedharsairam/trainkraft"

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val autoRefresh by remember(appContext) {
        SettingsStore.autoRefreshLiveFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_AUTO_REFRESH_LIVE)
    val cacheHours by remember(appContext) {
        SettingsStore.cacheDurationHoursFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_CACHE_DURATION_HOURS)
    val showCacheDialog = remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    end = KraftSpacing.spacing16,
                    top = KraftSpacing.spacing8,
                    bottom = KraftSpacing.spacing8,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "data-header") {
                SettingsSectionHeader("Data")
            }
            item(key = "auto-refresh") {
                ListItem(
                    headlineContent = { Text("Auto-refresh live status") },
                    supportingContent = {
                        Text("Refresh live running status automatically")
                    },
                    trailingContent = {
                        Switch(
                            checked = autoRefresh,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    SettingsStore.setAutoRefreshLive(appContext, enabled)
                                }
                            },
                        )
                    },
                    modifier = Modifier.clickable {
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
                            )
                        }
                    },
                    modifier = Modifier.clickable { showCacheDialog.value = true },
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
                        )
                    },
                    modifier = Modifier.clickable { uriHandler.openUri(SOURCE_URL) },
                )
                HorizontalDivider()
            }
            item(key = "gtfs") {
                ListItem(
                    headlineContent = { Text("Timetable data") },
                    supportingContent = { Text("GTFS snapshot · Aug 2026") },
                )
                HorizontalDivider()
            }
            item(key = "license") {
                ListItem(
                    headlineContent = { Text("License") },
                    supportingContent = {
                        Text("Train schedules © Indian Railways. TrainKraft is for personal, non-commercial use.")
                    },
                )
            }
        }
    }

    if (showCacheDialog.value) {
        AlertDialog(
            onDismissRequest = { showCacheDialog.value = false },
            title = { Text("Cache duration") },
            text = {
                Column {
                    SettingsStore.CACHE_DURATION_OPTIONS.forEach { hours ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        SettingsStore.setCacheDurationHours(appContext, hours)
                                    }
                                    showCacheDialog.value = false
                                }
                                .padding(vertical = KraftSpacing.spacing8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = hours == cacheHours,
                                onClick = {
                                    scope.launch {
                                        SettingsStore.setCacheDurationHours(appContext, hours)
                                    }
                                    showCacheDialog.value = false
                                },
                            )
                            Text(
                                text = SettingsStore.cacheDurationLabel(hours),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = KraftSpacing.spacing8),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCacheDialog.value = false }) {
                    Text("Done")
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            horizontal = KraftSpacing.spacing16,
            vertical = KraftSpacing.spacing8,
        ),
    )
}
