package com.trainkraft.app.presentation

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.SettingsStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayAbbrevFormat = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val dayNumberFormat = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
private val headerDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val emptyDateFormat = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)

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
    val allRows by viewModel.allRows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val source by viewModel.source.collectAsState()
    val sourceAgeMs by viewModel.sourceAgeMs.collectAsState()
    val fetchEpochMs by viewModel.fetchEpochMs.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val sort by viewModel.sort.collectAsState()

    val use24h by remember(appContext) {
        SettingsStore.use24hFlow(appContext)
    }.collectAsState(initial = SettingsStore.DEFAULT_USE_24H)

    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    val today = remember { LocalDate.now() }
    val week = remember(today) { betweenWeekDates(today) }
    val dayOfRuns = remember(allRows) { allRows.map { it.dayOfRun } }
    val counts = remember(dayOfRuns, week) { countsForDates(dayOfRuns, week) }
    val typeOptions = remember(allRows) { distinctTypeFilters(allRows) }
    val visible = remember(allRows, selectedDate, typeFilter, sort) {
        applyBetweenView(allRows, selectedDate, typeFilter, sort)
    }
    val canReload = fromStation != null && toStation != null

    Scaffold(
        topBar = {
            KraftTopBar(
                title = "Trains between",
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.refresh()
                        },
                        enabled = canReload && !isLoading,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
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

            if (!isLoading && error == null && allRows.isEmpty() &&
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

            if (allRows.isNotEmpty()) {
                item(key = "date-carousel") {
                    DateCarousel(
                        week = week,
                        counts = counts,
                        selectedDate = selectedDate,
                        onSelect = { date ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectDate(date)
                        },
                    )
                }

                item(key = "results-header") {
                    ResultsHeader(
                        date = selectedDate,
                        count = visible.size,
                        source = source,
                        sourceAgeMs = sourceAgeMs,
                        fetchEpochMs = fetchEpochMs,
                    )
                }

                item(key = "toolbar") {
                    FilterSortToolbar(
                        typeOptions = typeOptions,
                        typeFilter = typeFilter,
                        sort = sort,
                        onTypeFilter = { filter ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectTypeFilter(filter)
                        },
                        onSort = { next ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectSort(next)
                        },
                    )
                }

                if (visible.isEmpty()) {
                    item(key = "empty-filter") {
                        EmptyState(
                            title = "No trains on ${selectedDate.format(emptyDateFormat)}",
                            message = "No trains on ${selectedDate.format(emptyDateFormat)} — try another day.",
                            icon = Icons.Outlined.Train,
                        )
                    }
                } else {
                    items(visible, key = { "${it.trainNumber}-${it.fromCode}-${it.toCode}" }) { row ->
                        BetweenTrainCard(
                            row = row,
                            selectedDate = selectedDate,
                            use24h = use24h,
                            onTrainClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                focusManager.clearFocus()
                                onTrainClick(row.trainNumber)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 7-day carousel, today first; each chip carries its running-train count. */
@Composable
private fun DateCarousel(
    week: List<LocalDate>,
    counts: List<Int>,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        week.forEachIndexed { index, date ->
            val count = counts.getOrElse(index) { 0 }
            val selected = date == selectedDate
            val weekday = remember(date) { date.format(dayAbbrevFormat) }
            val dayNum = remember(date) { date.format(dayNumberFormat) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(KraftRadius.Standard))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "$weekday $dayNum, $count trains",
                        onClick = { onSelect(date) },
                    )
                    .padding(
                        horizontal = KraftSpacing.Spacing12,
                        vertical = KraftSpacing.Spacing8,
                    )
                    .heightIn(min = KraftSpacing.TouchTarget),
            ) {
                Text(
                    text = weekday,
                    style = tabularFigures(MaterialTheme.typography.labelSmall),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = dayNum,
                    style = tabularFigures(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$count",
                    style = tabularFigures(MaterialTheme.typography.labelSmall),
                    color = (if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        .copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** "Wed, 23 Sep — 2 Trains" plus the honesty badge. */
@Composable
private fun ResultsHeader(
    date: LocalDate,
    count: Int,
    source: DataSource,
    sourceAgeMs: Long?,
    fetchEpochMs: Long?,
) {
    val dateLabel = remember(date) { date.format(headerDateFormat) }
    Column(
        modifier = Modifier.padding(vertical = KraftSpacing.Spacing8),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
    ) {
        Text(
            text = "$dateLabel — $count Train${if (count != 1) "s" else ""}",
            style = tabularFigures(MaterialTheme.typography.titleMedium),
            fontWeight = FontWeight.SemiBold,
        )
        when (source) {
            DataSource.LIVE -> {
                fetchEpochMs?.let {
                    FreshnessBadge(FreshnessState.Live(clockLabel(it)))
                }
            }
            DataSource.CACHED -> {
                FreshnessBadge(
                    FreshnessState.Cached(
                        sourceAgeMs?.let { cachedAgeLabel(it) } ?: "unknown age",
                    ),
                )
            }
            DataSource.OFFLINE -> {
                Text(
                    text = "Offline schedule · GTFS snapshot Aug 2026",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Filter chips (All + present train types) and the Departure/Duration/Arrival sort. */
@Composable
private fun FilterSortToolbar(
    typeOptions: List<String>,
    typeFilter: String?,
    sort: BetweenSort,
    onTypeFilter: (String?) -> Unit,
    onSort: (BetweenSort) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4)) {
        Text(
            text = "Filter",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            ToolbarChip(
                label = BETWEEN_ALL_FILTER,
                selected = typeFilter == null,
                onClick = { onTypeFilter(null) },
            )
            typeOptions.forEach { option ->
                ToolbarChip(
                    label = option,
                    selected = typeFilter == option,
                    onClick = { onTypeFilter(option) },
                )
            }
        }
        Spacer(Modifier.padding(top = KraftSpacing.Spacing4))
        Text(
            text = "Sort",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8)) {
            ToolbarChip(
                label = "Departure",
                selected = sort == BetweenSort.DEPARTURE,
                onClick = { onSort(BetweenSort.DEPARTURE) },
            )
            ToolbarChip(
                label = "Duration",
                selected = sort == BetweenSort.DURATION,
                onClick = { onSort(BetweenSort.DURATION) },
            )
            ToolbarChip(
                label = "Arrival",
                selected = sort == BetweenSort.ARRIVAL,
                onClick = { onSort(BetweenSort.ARRIVAL) },
            )
        }
    }
}

@Composable
private fun ToolbarChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(KraftRadius.Pill))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(
                horizontal = KraftSpacing.Spacing12,
                vertical = KraftSpacing.Spacing8,
            ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BetweenTrainCard(
    row: BetweenUiRow,
    selectedDate: LocalDate,
    use24h: Boolean = true,
    onTrainClick: () -> Unit,
) {
    val durationMin =
        legDurationMinutes(row.depMin, row.depDayOffset, row.arrMin, row.arrDayOffset)
    val plusLabel = arrivalPlusLabel(row.arrDayOffset)
    val weekMask = remember(row.dayOfRun) { runningWeekMask(row.dayOfRun) }
    val classes = remember(row.classes) { splitClasses(row.classes) }

    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = KraftSprings.press(reduceMotion),
        label = "betweenPress",
    )
    Column(
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
                onClickLabel = "Open train ${row.trainNumber} details",
                onClick = onTrainClick,
            )
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            ),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        // Line 1: number + name + type chip.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            Text(
                text = row.trainNumber,
                style = tabularFigures(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = row.trainName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (row.typeDesc.isNotBlank()) {
                TypeBadge(text = row.typeDesc)
            }
        }

        // Line 2 (hero): dep — duration — arr.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = GtfsTime.format(row.depMin, 0, use24h),
                    style = tabularFigures(MaterialTheme.typography.titleLarge),
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = KraftColors.AuroraGreen,
                    maxLines = 1,
                )
                Text(
                    text = row.fromCode,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = formatDuration(durationMin),
                style = tabularFigures(MaterialTheme.typography.labelMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
                ) {
                    Text(
                        text = GtfsTime.format(row.arrMin, 0, use24h),
                        style = tabularFigures(MaterialTheme.typography.titleLarge),
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                    if (plusLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(KraftRadius.Pill),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = plusLabel,
                                style = tabularFigures(MaterialTheme.typography.labelSmall),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(
                                    horizontal = KraftSpacing.Spacing6,
                                    vertical = 2.dp,
                                ),
                            )
                        }
                    }
                }
                Text(
                    text = row.toCode,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // Line 3: running-days row (M T W T F S S).
        Row(horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4)) {
            val selectedWeekday = selectedDate.dayOfWeek
            betweenWeekOrder.forEachIndexed { index, day ->
                val runs = weekMask.getOrElse(index) { false }
                val isSelectedDay = day == selectedWeekday
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (runs) MaterialTheme.colorScheme.primary.copy(
                                alpha = KraftConstants.ContainerAlpha,
                            )
                            else Color.Transparent,
                        ),
                ) {
                    Text(
                        text = betweenWeekLetters[index],
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (runs || isSelectedDay) FontWeight.Bold
                        else FontWeight.Normal,
                        color = if (runs) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }

        // Line 4: class-of-service chips (raw NTES tokens).
        if (classes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
                verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
            ) {
                classes.forEach { cls ->
                    Surface(
                        shape = RoundedCornerShape(KraftRadius.Pill),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = cls,
                            style = tabularFigures(MaterialTheme.typography.labelSmall),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = KraftSpacing.Spacing8,
                                vertical = KraftSpacing.Spacing4,
                            ),
                        )
                    }
                }
            }
        }

        // Actions: Schedule + Track (both open train detail today — the
        // NavHost exposes a single onTrainClick(number) callback).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onTrainClick) {
                Text("Schedule")
            }
            TextButton(onClick = onTrainClick) {
                Text("Track")
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
