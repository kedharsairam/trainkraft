package com.trainkraft.app.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainkraft.app.data.StationEntity
import com.trainkraft.app.data.TrainEntity
import com.trainkraft.app.ui.theme.KraftSpacing

/** Shared train-type label (GTFS route_type, may be null/unknown). */
fun trainTypeLabel(type: String?): String {
    if (type.isNullOrBlank()) return "Rail"
    return when (type.trim()) {
        "0" -> "Tram"
        "1" -> "Metro"
        "2" -> "Rail"
        "3" -> "Bus"
        "4" -> "Ferry"
        "5" -> "Cable"
        "6" -> "Gondola"
        "7" -> "Funicular"
        else -> if (type.length <= 12) type else "Rail"
    }
}

@Composable
fun SearchScreen(
    onTrainClick: (String) -> Unit,
    onStationClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: SearchViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val stationResults by viewModel.stationResults.collectAsState()
    val trainResults by viewModel.trainResults.collectAsState()
    val dbError by viewModel.dbError.collectAsState()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = KraftSpacing.spacing16,
                    end = KraftSpacing.spacing8,
                    top = KraftSpacing.spacing16,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TrainKraft",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
        // Search bar
        TextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(KraftSpacing.spacing16)
                .focusRequester(focusRequester),
            placeholder = { Text("Search trains or stations") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (dbError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KraftSpacing.spacing16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dbError ?: "Database error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(KraftSpacing.spacing8))
                IconButton(onClick = viewModel::retrySearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Retry")
                }
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = viewModel::retrySearch),
                )
            }
        }

        when {
            query.isBlank() -> {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(KraftSpacing.spacing8))
                        Text(
                            text = "Search by train number, train name,",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "station code or station name",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            !isSearching && trainResults.isEmpty() && stationResults.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No results for \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (trainResults.isNotEmpty()) {
                        item(key = "trains-header") {
                            SectionHeader("Trains (${trainResults.size})")
                        }
                        items(trainResults, key = { "train-${it.trainNumber}" }) { train ->
                            TrainRow(
                                train = train,
                                onClick = { onTrainClick(train.trainNumber) },
                            )
                            HorizontalDivider()
                        }
                    }
                    if (stationResults.isNotEmpty()) {
                        item(key = "stations-header") {
                            SectionHeader("Stations (${stationResults.size})")
                        }
                        items(stationResults, key = { "station-${it.stopId}" }) { station ->
                            StationRow(
                                station = station,
                                onClick = { onStationClick(station.code) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
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

@Composable
private fun TrainRow(train: TrainEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = KraftSpacing.spacing16,
                vertical = KraftSpacing.spacing12,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Train,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(KraftSpacing.spacing12))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
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
private fun StationRow(station: StationEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = KraftSpacing.spacing16,
                vertical = KraftSpacing.spacing12,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(KraftSpacing.spacing12))
        Text(
            text = station.code,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(KraftSpacing.spacing8))
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

@Composable
private fun TypeBadge(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.spacing8,
                vertical = KraftSpacing.spacing2,
            ),
        )
    }
}
