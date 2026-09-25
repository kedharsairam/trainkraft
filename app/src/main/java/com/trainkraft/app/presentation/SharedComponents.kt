package com.trainkraft.app.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraft.ui.tokens.KraftSpacing

/**
 * Hairline card outline — 1dp [outlineVariant] at half alpha, drawn over the
 * card's own shape. Dark-theme cards otherwise melt into the near-black
 * background; this lifts them without elevation shadows (which read muddy
 * on OLED black).
 */
@Composable
fun Modifier.cardOutline(shape: Shape = MaterialTheme.shapes.medium): Modifier =
    this.border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        shape = shape,
    )

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
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = Modifier.padding(
            start = KraftSpacing.Spacing4,
            end = KraftSpacing.Spacing4,
            top = KraftSpacing.Spacing8,
            bottom = KraftSpacing.Spacing4,
        ),
    )
}

@Composable
fun DayBadge(dayOffset: Int) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "Day +$dayOffset",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.Spacing8,
                vertical = KraftSpacing.Spacing2,
            ),
        )
    }
}

@Composable
fun TypeBadge(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color,
        modifier = modifier.cardOutline(MaterialTheme.shapes.small),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.Spacing8,
                vertical = KraftSpacing.Spacing2,
            ),
        )
    }
}
