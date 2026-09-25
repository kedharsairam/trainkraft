package com.trainkraft.app.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.kraft.ui.components.EmptyState
import com.kraft.ui.components.ShimmerList
import com.kraft.ui.motion.KraftSprings
import com.kraft.ui.motion.rememberReduceMotion
import com.kraft.ui.tokens.KraftColors
import com.kraft.ui.tokens.KraftConstants
import com.kraft.ui.tokens.KraftIconSize
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.data.StationEntity
import com.trainkraft.app.data.TrainEntity

@Composable
fun SearchScreen(
    onTrainClick: (String) -> Unit,
    onStationClick: (String) -> Unit,
    onBetweenClick: () -> Unit = {},
    onPnrClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: SearchViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val stationResults by viewModel.stationResults.collectAsState()
    val trainResults by viewModel.trainResults.collectAsState()
    val dbError by viewModel.dbError.collectAsState()
    val tracked by viewModel.tracked.collectAsState()
    val liveSummaries by viewModel.liveSummaries.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val homeScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var stationMode by remember { mutableStateOf(false) }

    // Bells toggle on TrainDetail: re-read tracked_trains every time home
    // regains the foreground. One-shot suspend read (no list-all Flow on the
    // DAO) — cheapest reliable option.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshTracked()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = KraftSpacing.ScreenEdge),
        ) {
            Spacer(modifier = Modifier.height(KraftSpacing.Spacing16))
            // App bar — display scale title, pill action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TrainKraft",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBetweenClick()
                    },
                    contentDescription = "Between Stations",
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                }
                Spacer(Modifier.width(KraftSpacing.Spacing8))
                GlassIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSettingsClick()
                    },
                    contentDescription = "Settings",
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                }
            }
            Spacer(modifier = Modifier.height(KraftSpacing.Spacing16))
            // Hero search card — inset tonal surface, generous padding. The
            // primary job stays dominant; behavior (as-you-type, clear,
            // keyboard actions, haptics) is unchanged.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(KraftRadius.Hero))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .cardOutline(RoundedCornerShape(KraftRadius.Hero))
                    .padding(KraftSpacing.Spacing16),
            ) {
                // Search pill — frosted surface, no underline, 44dp rhythm
                TextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            if (stationMode) "Station code (e.g. NDLS)"
                            else "Try 12951, Rajdhani, or NDLS",
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.clearQuery()
                                stationMode = false
                            }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.recordRecentSearch(query)
                        focusManager.clearFocus()
                    }),
                    shape = RoundedCornerShape(KraftRadius.Pill),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
                if (stationMode) {
                    Spacer(modifier = Modifier.height(KraftSpacing.Spacing8))
                    Text(
                        text = "Search a station code (e.g. NDLS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(KraftSpacing.Spacing8))
                // Honest provenance: search data is the local GTFS snapshot.
                Text(
                    text = "Offline timetable · live status when online",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(KraftSpacing.Spacing8))

            if (dbError != null) {
                ErrorBanner(
                    message = dbError ?: "Database error",
                    onRetry = viewModel::retrySearch,
                )
            }

            when {
                isSearching -> {
                    // Skeleton rows match result shape — no content jump on load.
                    ShimmerList(
                        rows = 8,
                        contentDescription = "Searching trains and stations",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = KraftSpacing.ScreenEdge),
                    )
                }
                query.isBlank() -> {
                    // Home IA: recent searches, quick actions, alerts status,
                    // tracked overview. History shows ONLY here (blank query):
                    // any typed query hands the column to results, and an
                    // empty history renders nothing (no empty-state noise).
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(homeScroll),
                        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
                    ) {
                        if (recentSearches.isNotEmpty()) {
                            RecentSearchesSection(
                                items = recentSearches,
                                onRerun = viewModel::onQueryChange,
                                onClearAll = viewModel::clearRecentSearches,
                            )
                        }
                        QuickActionGrid(
                            onBetweenClick = onBetweenClick,
                            onPnrClick = onPnrClick,
                            onStationBoardClick = {
                                viewModel.clearQuery()
                                stationMode = true
                                focusRequester.requestFocus()
                            },
                            onTrackedClick = {
                                scope.launch { homeScroll.animateScrollTo(homeScroll.maxValue) }
                            },
                        )
                        AlertsStatusRow(onSettingsClick = onSettingsClick)
                        TrackedTrainsSection(
                            tracked = tracked,
                            liveSummaries = liveSummaries,
                            onTrainClick = onTrainClick,
                            onUntrack = { viewModel.untrack(it) },
                        )
                        Spacer(modifier = Modifier.height(KraftSpacing.Spacing8))
                    }
                }
                trainResults.isEmpty() && stationResults.isEmpty() -> {
                    EmptyState(
                        title = "No results",
                        message = "No results for \"$query\". Check spelling or try a number.",
                        icon = Icons.Outlined.SearchOff,
                        actionLabel = "Clear search",
                        onAction = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.clearQuery()
                        },
                    )
                }
                else -> {
                    val nestedScrollConnection = remember {
                        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                            override fun onPostScroll(
                                consumed: androidx.compose.ui.geometry.Offset,
                                available: androidx.compose.ui.geometry.Offset,
                                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                            ): androidx.compose.ui.geometry.Offset {
                                if (consumed.y != 0f) focusManager.clearFocus()
                                return available
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                        contentPadding = PaddingValues(
                            start = KraftSpacing.ScreenEdge,
                            end = KraftSpacing.ScreenEdge,
                            bottom = KraftSpacing.Spacing16,
                        ),
                        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                    ) {
                        if (trainResults.isNotEmpty()) {
                            item(key = "trains-header") {
                                SectionHeader("Trains (${trainResults.size})")
                            }
                            items(trainResults, key = { "train-${it.trainNumber}" }) { train ->
                                TrainCard(
                                    train = train,
                                    onClick = {
                                        viewModel.recordRecentSearch(query)
                                        onTrainClick(train.trainNumber)
                                    },
                                )
                            }
                        }
                        if (stationResults.isNotEmpty()) {
                            item(key = "stations-header") {
                                SectionHeader("Stations (${stationResults.size})")
                            }
                            items(stationResults, key = { "station-${it.stopId}" }) { station ->
                                StationCard(
                                    station = station,
                                    onClick = {
                                        viewModel.recordRecentSearch(query)
                                        onStationClick(station.code)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Frosted circular icon button — glass pill actions in the app bar. */
@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = KraftSprings.press(reduceMotion),
        label = "glassButtonPress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(KraftSpacing.TouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        content()
    }
}

/** Inline error banner with retry — surface container, error tint icon. */
@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(
                MaterialTheme.colorScheme.errorContainer.copy(
                    alpha = KraftConstants.ErrorContainerAlpha,
                ),
            )
            .clickable(
                role = Role.Button,
                onClickLabel = "Retry",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRetry()
                },
            )
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            ),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(KraftSpacing.Spacing8))
        Text(
            text = "Retry",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(modifier = Modifier.height(KraftSpacing.Spacing8))
}

@Composable
private fun TrainCard(train: TrainEntity, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = KraftSprings.press(reduceMotion),
        label = "trainCardPress",
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
            .cardOutline(RoundedCornerShape(KraftRadius.Standard))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open train details",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    focusManager.clearFocus()
                    onClick()
                },
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = train.trainNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TypeBadge(trainTypeLabel(train.type))
            }
            Text(
                text = train.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StationCard(station: StationEntity, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = KraftSprings.press(reduceMotion),
        label = "stationCardPress",
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
            .cardOutline(RoundedCornerShape(KraftRadius.Standard))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open station board",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    focusManager.clearFocus()
                    onClick()
                },
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
                    KraftColors.AuroraTeal.copy(alpha = KraftConstants.ContainerAlpha),
                ),
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = KraftColors.AuroraTeal,
                modifier = Modifier.size(KraftIconSize.Medium),
            )
        }
        Spacer(Modifier.width(KraftSpacing.Spacing12))
        Text(
            text = station.code,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(KraftSpacing.Spacing8))
        Text(
            text = station.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Recent-search history: tap re-runs the query (the debounced collector in
 * [SearchViewModel] picks it up), one Clear-all row with haptics, no
 * per-item delete (scope). Rendered ONLY when the query box is blank and the
 * list is non-empty — callers gate both conditions.
 */
@Composable
private fun RecentSearchesSection(
    items: List<String>,
    onRerun: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("Recent searches (${items.size})")
            Spacer(Modifier.weight(1f))
            Text(
                text = "Clear all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Clear all recent searches",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClearAll()
                        },
                    )
                    .padding(
                        horizontal = KraftSpacing.Spacing16,
                        vertical = KraftSpacing.Spacing12,
                    ),
            )
        }
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(KraftRadius.Standard))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .cardOutline(RoundedCornerShape(KraftRadius.Standard))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Search again for $item",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRerun(item)
                        },
                    )
                    .padding(
                        horizontal = KraftSpacing.Spacing16,
                        vertical = KraftSpacing.Spacing12,
                    )
                    .heightIn(min = KraftSpacing.TouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(KraftIconSize.Medium),
                )
                Spacer(Modifier.width(KraftSpacing.Spacing12))
                Text(
                    text = item,
                    style = tabularFigures(MaterialTheme.typography.bodyLarge),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
