package com.trainkraft.app.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
    onSettingsClick: () -> Unit = {},
    viewModel: SearchViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val stationResults by viewModel.stationResults.collectAsState()
    val trainResults by viewModel.trainResults.collectAsState()
    val dbError by viewModel.dbError.collectAsState()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

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
            // Search pill — frosted surface, no underline, 44dp rhythm
            TextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Try 12951, Rajdhani, or NDLS") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.clearQuery()
                        }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(KraftRadius.Pill),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                query.isBlank() -> {
                    EmptyState(
                        title = "Find a train or station",
                        message = "Try a number like 12951, a name like Rajdhani, or a code like NDLS.",
                        icon = Icons.Outlined.Search,
                    )
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
                        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                    ) {
                        if (trainResults.isNotEmpty()) {
                            item(key = "trains-header") {
                                SectionHeader("Trains (${trainResults.size})")
                            }
                            items(trainResults, key = { "train-${it.trainNumber}" }) { train ->
                                TrainCard(
                                    train = train,
                                    onClick = { onTrainClick(train.trainNumber) },
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
                                    onClick = { onStationClick(station.code) },
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
