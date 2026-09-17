package com.trainkraft.app.presentation

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.StationDeparture
import com.trainkraft.app.ui.theme.KraftSpacing

/**
 * Offline station departure board (dark-only Material3).
 *
 * Shows the offline schedule with an "Offline schedule" note. There is no
 * NtesApi station_live endpoint, so no Live Board action is offered.
 */
@Composable
fun StationBoardScreen(
    stationCode: String,
    onBack: () -> Unit,
    onTrainClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(stationCode) {
        StationBoardViewModel.Factory(
            context.applicationContext as Application,
            stationCode,
        )
    }
    val viewModel: StationBoardViewModel = viewModel(factory = factory)
    val station by viewModel.station.collectAsState()
    val departures by viewModel.departures.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val dbError by viewModel.dbError.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: back + station code (large) + name
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station?.code ?: stationCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = station?.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            dbError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dbError ?: "Database error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = { viewModel.retry() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            departures.isEmpty() -> {
                StationBoardHeader(
                    count = 0,
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No departures found for ${station?.code ?: stationCode}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "header") {
                        StationBoardHeader(
                            count = departures.size,
                        )
                        HorizontalDivider()
                    }
                    items(departures, key = { it.tripId }) { departure ->
                        DepartureRow(
                            departure = departure,
                            onClick = { onTrainClick(departure.trainNumber) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun StationBoardHeader(
    count: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KraftSpacing.spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
    ) {
        if (count > 0) {
            Text(
                text = "$count departures today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Offline schedule",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DepartureRow(
    departure: StationDeparture,
    onClick: () -> Unit,
) {
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
                    text = departure.trainNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                if (departure.dayOffset > 0) {
                    DayBadgeSmall(departure.dayOffset)
                }
            }
            // Note: StationDeparture carries no destination/platform columns
            // in the offline GTFS import, so the train name is the subtitle.
            Text(
                text = departure.trainName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = departure.depMin?.let { GtfsTime.format(it, departure.dayOffset) } ?: "--:--",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun DayBadgeSmall(dayOffset: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "Day +$dayOffset",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.spacing8,
                vertical = KraftSpacing.spacing2,
            ),
        )
    }
}
