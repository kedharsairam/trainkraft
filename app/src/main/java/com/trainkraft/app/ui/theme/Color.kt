package com.trainkraft.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// -- Accents (dark-tuned) --
val AccentBlue = Color(0xFF0A84FF)
val AccentGreen = Color(0xFF30D158)
val AccentRed = Color(0xFFFF453A)
val AccentOrange = Color(0xFFFF9F0A)
val AccentYellow = Color(0xFFFFD60A)
val AccentPurple = Color(0xFFBF5AF2)

// -- Backgrounds & Surfaces (dark-only) --
val BackgroundDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceSecondaryDark = Color(0xFF2C2C2E)
val SurfaceTertiaryDark = Color(0xFF3A3A3C)

// -- Text --
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFFEBEBF5)
val TextTertiaryDark = Color(0xFF8E8E93)

// -- Separators & Fills --
val SeparatorDark = Color(0xFF38383A)
val FillPrimaryDark = Color(0xFF787880)

// -- Theme-aware color holder (dark-only app) --
@Immutable
data class ThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceTertiary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val separator: Color,
    val accentBlue: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val accentOrange: Color,
    val accentYellow: Color,
    val accentPurple: Color,
)

object KraftThemeColors {
    val dark = ThemeColors(
        background = BackgroundDark,
        surface = SurfaceDark,
        surfaceSecondary = SurfaceSecondaryDark,
        surfaceTertiary = SurfaceTertiaryDark,
        textPrimary = TextPrimaryDark,
        textSecondary = TextSecondaryDark,
        textTertiary = TextTertiaryDark,
        separator = SeparatorDark,
        accentBlue = AccentBlue,
        accentGreen = AccentGreen,
        accentRed = AccentRed,
        accentOrange = AccentOrange,
        accentYellow = AccentYellow,
        accentPurple = AccentPurple,
    )
}
