package com.progresspal.app.reading.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Indigo = Color(0xFF4F46E5)
private val IndigoDark = Color(0xFF3730A3)
private val Teal = Color(0xFF0D9488)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFFD97706),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF0F6),
    onSurfaceVariant = Color(0xFF5B6272),
    surfaceContainer = Color(0xFFF1F3F9),
    surfaceContainerHigh = Color(0xFFE9ECF4),
    outline = Color(0xFFCBD0DC),
    outlineVariant = Color(0xFFE3E6EE),
    error = Color(0xFFDC2626),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFBBF24),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1A1D26),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF252935),
    onSurfaceVariant = Color(0xFFA3A9B8),
    surfaceContainer = Color(0xFF1F222C),
    surfaceContainerHigh = Color(0xFF282C37),
    outline = Color(0xFF454B5A),
    outlineVariant = Color(0xFF2E3240),
    error = Color(0xFFF87171),
)

/** Colors used by charts and the dashboard hero. */
@Immutable
data class ChartColors(
    val bar: Color,
    val barMuted: Color,
    val goal: Color,
    val grid: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val good: Color,
)

private val LightChart = ChartColors(
    bar = Indigo,
    barMuted = Color(0xFFA5B4FC),
    goal = Color(0xFFD97706),
    grid = Color(0xFFE5E7EB),
    heroStart = IndigoDark,
    heroEnd = Teal,
    good = Color(0xFF059669),
)
private val DarkChart = ChartColors(
    bar = Color(0xFF818CF8),
    barMuted = Color(0xFF4338CA),
    goal = Color(0xFFFBBF24),
    grid = Color(0xFF2E3240),
    heroStart = Color(0xFF3730A3),
    heroEnd = Color(0xFF0F766E),
    good = Color(0xFF34D399),
)

val LocalChartColors = staticCompositionLocalOf { LightChart }

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val NumberStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)

@Composable
fun ReadingTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalChartColors provides if (dark) DarkChart else LightChart
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
