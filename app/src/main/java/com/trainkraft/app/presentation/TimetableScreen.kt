package com.trainkraft.app.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.data.TimetableVersion
import com.trainkraft.app.data.TimetableVersionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline-timetable info + on-demand update check (Agent-V3 line).
 *
 * Route wiring lives in TrainKraftNavHost (peer-owned); the Settings row that
 * navigates here is also peer-owned. This screen takes only [onBack].
 *
 * Honest-copy contract: timetable updates ship inside app updates — this app
 * never downloads timetable data in the background, and the screen states
 * that plainly. The remote check is on-demand only (no cache, no polling:
 * versions change monthly at most).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var local by remember { mutableStateOf<TimetableVersion?>(null) }
    var localLoaded by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<TimetableCheck>(TimetableCheck.Idle) }

    LaunchedEffect(appContext) {
        val version = withContext(Dispatchers.IO) {
            TimetableVersionChecker.readLocal(appContext)
        }
        local = version
        localLoaded = true
    }

    Scaffold(
        topBar = {
            KraftTopBar(
                title = "Timetable",
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = KraftSpacing.Spacing16,
                    vertical = KraftSpacing.Spacing16,
                ),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing16),
        ) {
            // Current snapshot card (tonal surface).
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(KraftSpacing.Spacing16),
                    verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                ) {
                    Text(
                        text = "Current timetable",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    if (!localLoaded) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(KraftSpacing.Spacing24)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else {
                        val current = local
                        if (current == null) {
                            Text(
                                text = "Timetable version info isn't available in this build.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.semantics {
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        } else {
                            TimetableRow(
                                label = "Version",
                                value = current.version,
                            )
                            TimetableRow(
                                label = "Generated",
                                value = current.generatedAt,
                            )
                            TimetableRow(
                                label = "Trains",
                                value = "${current.trains} trains",
                            )
                        }
                    }
                }
            }

            // Plain-spoken update policy: no silent downloads, ever.
            Text(
                text = "Timetable updates ship with app updates. " +
                    "This app never downloads timetable data in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        check = TimetableCheck.Checking
                        val remote = withContext(Dispatchers.IO) {
                            TimetableVersionChecker.fetchRemote()
                        }
                        val baseline = local
                        check = when {
                            remote == null -> TimetableCheck.Offline
                            baseline != null &&
                                TimetableVersionChecker.isUpdateAvailable(
                                    baseline.version,
                                    remote.version,
                                ) -> TimetableCheck.UpdateAvailable(remote)
                            else -> TimetableCheck.UpToDate
                        }
                    }
                },
                enabled = localLoaded && local != null && check != TimetableCheck.Checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Check for updates")
            }

            // Result states (TalkBack: polite live region).
            when (val state = check) {
                is TimetableCheck.Idle -> Unit
                is TimetableCheck.Checking -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalArrangement = Arrangement.spacedBy(
                            KraftSpacing.Spacing8,
                            Alignment.CenterHorizontally,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(KraftSpacing.Spacing24),
                        )
                        Text(
                            text = "Checking…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is TimetableCheck.UpToDate -> {
                    Text(
                        text = "You're on the latest timetable",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
                is TimetableCheck.UpdateAvailable -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    ) {
                        Text(
                            text = "A newer timetable " +
                                "(${TimetableVersionChecker.monthLabel(state.remote.version)}) " +
                                "is available — it ships with the next app update",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uriHandler.openUri(TimetableVersionChecker.RELEASES_URL)
                            },
                        ) {
                            Text("View releases")
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = KraftSpacing.Spacing8)
                                    .size(KraftSpacing.Spacing16),
                            )
                        }
                    }
                }
                is TimetableCheck.Offline -> {
                    Text(
                        text = "Couldn't reach GitHub — try when online",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
            }
        }
    }
}

/** On-demand check states; the check never runs except on button tap. */
private sealed interface TimetableCheck {
    data object Idle : TimetableCheck
    data object Checking : TimetableCheck
    data object UpToDate : TimetableCheck
    data class UpdateAvailable(val remote: TimetableVersion) : TimetableCheck
    data object Offline : TimetableCheck
}

@Composable
private fun TimetableRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
        )
        Text(
            text = value,
            // Tabular figures keep version/date/count digits aligned.
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
