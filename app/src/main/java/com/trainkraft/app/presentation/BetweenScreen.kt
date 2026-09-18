package com.trainkraft.app.presentation

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainkraft.app.data.BetweenResult
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.ui.theme.KraftSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetweenScreen(
    onBack: () -> Unit,
    onTrainClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val factory = remember {
        BetweenViewModel.Factory(appContext as Application)
    }
    val viewModel: BetweenViewModel = viewModel(factory = factory)

    val fromQuery by viewModel.fromQuery.collectAsState()
    val toQuery by viewModel.toQuery.collectAsState()
    val fromResults by viewModel.fromResults.collectAsState()
    val toResults by viewModel.toResults.collectAsState()
    val fromStation by viewModel.fromStation.collectAsState()
    val toStation by viewModel.toStation.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val use24h by remember(appContext) {
        SettingsStore.use24hFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_USE_24H)

    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Between Stations",
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
            // Station pickers
            item(key = "pickers") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KraftSpacing.spacing16, vertical = KraftSpacing.spacing8),
                    verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
                ) {
                    StationPicker(
                        label = "From",
                        query = fromQuery,
                        results = fromResults,
                        selected = fromStation,
                        onQueryChange = viewModel::onFromQueryChange,
                        onSelect = viewModel::selectFrom,
                        onClear = { viewModel.onFromQueryChange("") },
                    )
                    // Swap button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.swap()
                            },
                        ) {
                            Icon(
                                Icons.Filled.SwapVert,
                                contentDescription = "Swap stations",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    StationPicker(
                        label = "To",
                        query = toQuery,
                        results = toResults,
                        selected = toStation,
                        onQueryChange = viewModel::onToQueryChange,
                        onSelect = viewModel::selectTo,
                        onClear = { viewModel.onToQueryChange("") },
                    )
                }
                HorizontalDivider()
            }

            // Loading
            if (isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Error
            if (error != null && !isLoading) {
                item(key = "error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(KraftSpacing.spacing24),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
                    ) {
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Results header
            if (results.isNotEmpty()) {
                item(key = "results-header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = KraftSpacing.spacing16, vertical = KraftSpacing.spacing12),
                    ) {
                        Text(
                            text = "${results.size} train${if (results.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${fromStation?.code} → ${toStation?.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }

            // Result rows
            items(results, key = { "${it.trainNumber}-${it.fromCode}-${it.toCode}" }) { result ->
                BetweenRow(
                    result = result,
                    use24h = use24h,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTrainClick(result.trainNumber)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = KraftSpacing.spacing64))
            }

            // Bottom spacer
            if (results.isNotEmpty()) {
                item(key = "bottom-spacer") {
                    Spacer(Modifier.padding(bottom = KraftSpacing.spacing32))
                }
            }
        }
    }
}

@Composable
private fun StationPicker(
    label: String,
    query: String,
    results: List<com.trainkraft.app.data.StationEntity>,
    selected: com.trainkraft.app.data.StationEntity?,
    onQueryChange: (String) -> Unit,
    onSelect: (com.trainkraft.app.data.StationEntity) -> Unit,
    onClear: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Station name or code")
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Train,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        onClear()
                        focusManager.clearFocus()
                    }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        // Autocomplete results
        if (results.isNotEmpty() && selected == null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    results.take(5).forEach { station ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusManager.clearFocus()
                                    onSelect(station)
                                }
                                .padding(horizontal = KraftSpacing.spacing16, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Train,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(KraftSpacing.spacing8))
                            Text(
                                text = station.code,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.width(KraftSpacing.spacing8))
                            Text(
                                text = station.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = KraftSpacing.spacing40))
                    }
                }
            }
        }
    }
}

@Composable
private fun BetweenRow(
    result: BetweenResult,
    use24h: Boolean = true,
    onClick: () -> Unit,
) {
    // Duration calculation (handles day offset)
    val totalDepMin = result.depMin + result.depDayOffset * 1440
    val totalArrMin = result.arrMin + result.arrDayOffset * 1440
    val durationMin = totalArrMin - totalDepMin
    val durH = durationMin / 60
    val durM = durationMin % 60

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(
                horizontal = KraftSpacing.spacing16,
                vertical = KraftSpacing.spacing12,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Train icon
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.Train,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(KraftSpacing.spacing12))
        // Train info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.trainNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = result.trainName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(KraftSpacing.spacing12))
        // Times
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = GtfsTime.format(result.depMin, result.depDayOffset, use24h),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = GtfsTime.format(result.arrMin, result.arrDayOffset, use24h),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "${durH}h ${durM}m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
