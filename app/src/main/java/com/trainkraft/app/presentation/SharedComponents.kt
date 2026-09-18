package com.trainkraft.app.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(
            horizontal = KraftSpacing.spacing16,
            vertical = KraftSpacing.spacing8,
        ),
    )
}

@Composable
fun DayBadge(dayOffset: Int) {
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

@Composable
fun TypeBadge(text: String, color: Color = MaterialTheme.colorScheme.primaryContainer, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.spacing8,
                vertical = KraftSpacing.spacing2,
            ),
        )
    }
}
