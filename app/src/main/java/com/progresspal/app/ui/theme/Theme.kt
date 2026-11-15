package com.progresspal.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.progresspal.app.data.ProjectColor

private fun hsl(h: Float, s: Float, l: Float) = Color.hsl(h, s, l)

object Brand {
    val green = hsl(152f, .60f, .48f)
    val amber = hsl(38f, .92f, .55f)
    val coral = hsl(12f, .85f, .60f)
    val blue = hsl(217f, .91f, .60f)
    val purple = hsl(270f, .60f, .58f)
    val accent = hsl(25f, .95f, .60f)
    val destructive = hsl(0f, .72f, .55f)

    val timeGradient = Brush.horizontalGradient(listOf(hsl(30f, 1f, .55f), hsl(0f, .85f, .50f)))
    val urgentGradient = Brush.horizontalGradient(listOf(hsl(0f, .85f, .50f), hsl(0f, .90f, .40f)))
    val taskGradient = Brush.horizontalGradient(listOf(hsl(55f, .90f, .50f), hsl(140f, .70f, .45f)))

    val chartColors = listOf(green, blue, purple, amber, coral, accent, destructive)
}

fun ProjectColor.color(): Color = when (this) {
    ProjectColor.GREEN -> Brand.green
    ProjectColor.AMBER -> Brand.amber
    ProjectColor.CORAL -> Brand.coral
    ProjectColor.BLUE -> Brand.blue
    ProjectColor.PURPLE -> Brand.purple
}

@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val foreground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val border: Color,
    val primary: Color = Brand.green,
    val destructive: Color = Brand.destructive,
)

private val DarkPalette = Palette(
    background = hsl(228f, .20f, .10f),
    card = hsl(228f, .18f, .14f),
    foreground = hsl(210f, .20f, .92f),
    muted = hsl(228f, .14f, .18f),
    mutedForeground = hsl(215f, .15f, .55f),
    border = hsl(228f, .14f, .20f),
)

private val LightPalette = Palette(
    background = hsl(220f, .20f, .97f),
    card = Color.White,
    foreground = hsl(220f, .20f, .10f),
    muted = hsl(220f, .14f, .94f),
    mutedForeground = hsl(220f, .10f, .50f),
    border = hsl(220f, .14f, .88f),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

object Pal {
    val c: Palette @Composable get() = LocalPalette.current
}

val NumberStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace)

@Composable
fun ProgressPalTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.primary, onPrimary = Color.White, background = p.background, onBackground = p.foreground,
            surface = p.card, onSurface = p.foreground, surfaceVariant = p.muted, onSurfaceVariant = p.mutedForeground,
            outline = p.border, outlineVariant = p.border, error = p.destructive, surfaceContainerHigh = p.card,
            surfaceContainer = p.card, surfaceContainerHighest = p.muted, surfaceContainerLow = p.card,
            secondaryContainer = p.primary.copy(alpha = .2f), onSecondaryContainer = p.foreground,
            primaryContainer = p.primary.copy(alpha = .2f), onPrimaryContainer = p.foreground,
        )
    } else {
        lightColorScheme(
            primary = p.primary, onPrimary = Color.White, background = p.background, onBackground = p.foreground,
            surface = p.card, onSurface = p.foreground, surfaceVariant = p.muted, onSurfaceVariant = p.mutedForeground,
            outline = p.border, outlineVariant = p.border, error = p.destructive, surfaceContainerHigh = p.card,
            surfaceContainer = p.card, surfaceContainerHighest = p.muted, surfaceContainerLow = p.card,
            secondaryContainer = p.primary.copy(alpha = .2f), onSecondaryContainer = p.foreground,
            primaryContainer = p.primary.copy(alpha = .2f), onPrimaryContainer = p.foreground,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
