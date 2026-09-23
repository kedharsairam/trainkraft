package com.trainkraft.app.presentation

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kraft.ui.components.EmptyState
import com.kraft.ui.components.ErrorState
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.components.ShimmerList
import com.kraft.ui.motion.KraftSprings
import com.kraft.ui.motion.rememberReduceMotion
import com.kraft.ui.tokens.KraftColors
import com.kraft.ui.tokens.KraftConstants
import com.kraft.ui.tokens.KraftIconSize
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.data.BetweenResult
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.SettingsStore

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
    val source by viewModel.source.collectAsState()
    val sourceAgeMs by viewModel.sourceAgeMs.collectAsState()

    val use24h by remember(appContext) {
        SettingsStore.use24hFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_USE_24H)

    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            KraftTopBar(
                title = "Between Stations",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = KraftSpacing.ScreenEdge,
                end = KraftSpacing.ScreenEdge,
                bottom = KraftSpacing.Spacing16,
            ),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            // Station pickers
            item(key = "pickers") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
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
                    // Swap button — centered frosted circle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(KraftSpacing.TouchTarget)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Swap stations",
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.swap()
                                    },
                                ),
                        ) {
                            Icon(
                                Icons.Filled.SwapVert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
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
            }

            if (isLoading) {
                item(key = "loading") {
                    ShimmerList(rows = 5, contentDescription = "Finding trains")
                }
            }

            if (error != null && !isLoading) {
                item(key = "error") {
                    ErrorState(
                        message = error ?: "",
                        onRetry = { viewModel.retry() },
                    )
                }
            }

            if (!isLoading && error == null && results.isEmpty() &&
                fromStation != null && toStation != null
            ) {
                item(key = "empty") {
                    EmptyState(
                        title = "No direct trains",
                        message = "No trains run directly from ${fromStation?.code} to ${toStation?.code}. Try nearby stations.",
                        icon = Icons.Outlined.Train,
                    )
                }
            }

            if (results.isNotEmpty()) {
                item(key = "results-header") {
                    Column(
                        modifier = Modifier.padding(vertical = KraftSpacing.Spacing8),
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
                        // Source honesty badge.
                        Text(
                            text = when (source) {
                                DataSource.LIVE -> "Live · NTES now"
                                DataSource.CACHED -> sourceAgeMs?.let {
                                    "Cached NTES · updated ${formatAge(it)}"
                                } ?: "Cached NTES response"
                                DataSource.OFFLINE -> "Offline schedule · GTFS snapshot Aug 2026"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(results, key = { "${it.trainNumber}-${it.fromCode}-${it.toCode}" }) { result ->
                BetweenCard(
                    result = result,
                    use24h = use24h,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        focusManager.clearFocus()
                        onTrainClick(result.trainNumber)
                    },
                )
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
            modifier = Modifier.padding(bottom = KraftSpacing.Spacing4),
        )
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Station name or code") },
            leadingIcon = {
                Icon(
                    Icons.Filled.Train,
                    contentDescription = null,
                    modifier = Modifier.size(KraftIconSize.Medium),
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
            shape = RoundedCornerShape(KraftRadius.Pill),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        // Autocomplete results
        if (results.isNotEmpty() && selected == null) {
            Spacer(Modifier.padding(top = KraftSpacing.Spacing4))
            Surface(
                shape = RoundedCornerShape(KraftRadius.Standard),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                .padding(
                                    horizontal = KraftSpacing.Spacing16,
                                    vertical = KraftSpacing.Spacing12,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = station.code,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.width(KraftSpacing.Spacing8))
                            Text(
                                text = station.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (selected != null) {
            Spacer(Modifier.padding(top = KraftSpacing.Spacing4))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(KraftRadius.Pill))
                    .background(
                        KraftColors.AuroraGreen.copy(alpha = KraftConstants.ContainerAlpha),
                    )
                    .padding(
                        horizontal = KraftSpacing.Spacing12,
                        vertical = KraftSpacing.Spacing6,
                    ),
            ) {
                Text(
                    text = "${selected.code} · ${selected.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = KraftColors.AuroraGreen,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BetweenCard(
    result: BetweenResult,
    use24h: Boolean = true,
    onClick: () -> Unit,
) {
    val totalDepMin = result.depMin + result.depDayOffset * 1440
    val totalArrMin = result.arrMin + result.arrDayOffset * 1440
    val durationMin = totalArrMin - totalDepMin
    val durH = durationMin / 60
    val durM = durationMin % 60

    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = KraftSprings.press(reduceMotion),
        label = "betweenPress",
    )
    Row(
        modifier = Modifier
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
                onClick = onClick,
            )
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            )
            .heightIn(min = KraftSpacing.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(KraftIconSize.XLarge)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primary.copy(
                        alpha = KraftConstants.ContainerAlpha,
                    ),
                ),
        ) {
            Icon(
                Icons.Filled.Train,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(KraftIconSize.Medium),
            )
        }
        Spacer(Modifier.width(KraftSpacing.Spacing12))
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
        Spacer(Modifier.width(KraftSpacing.Spacing12))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = GtfsTime.format(result.depMin, result.depDayOffset, use24h),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = KraftColors.AuroraGreen,
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
