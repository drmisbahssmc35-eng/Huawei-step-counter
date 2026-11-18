package com.progresspal.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.progresspal.app.data.SimpleTimeTracker
import kotlin.math.roundToInt

/**
 * Floating button shown on every page that opens Simple Time Tracker, with its own app icon.
 * Tap = open the app; drag = move it out of the way (it remembers where you left it).
 */
@Composable
fun SttLauncherFab() {
    val ctx = LocalContext.current
    val icon = remember {
        runCatching { ctx.packageManager.getApplicationIcon(SimpleTimeTracker.PKG).toBitmap(128, 128).asImageBitmap() }.getOrNull()
    }
    // Only when Simple Time Tracker is installed.
    if (icon == null) return

    val prefs = remember { ctx.getSharedPreferences("stt_fab", android.content.Context.MODE_PRIVATE) }
    var fx by rememberSaveable { mutableFloatStateOf(prefs.getFloat("x", -1f)) }
    var fy by rememberSaveable { mutableFloatStateOf(prefs.getFloat("y", -1f)) }
    val density = LocalDensity.current
    val sizeDp = 52.dp

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val sizePx = with(density) { sizeDp.toPx() }
        val maxX = constraints.maxWidth - sizePx
        val maxY = constraints.maxHeight - sizePx
        // Default: right edge, a bit above the middle-bottom so it does not cover bottom bars.
        val x = (if (fx < 0) maxX - with(density) { 10.dp.toPx() } else fx).coerceIn(0f, maxX.coerceAtLeast(0f))
        val y = (if (fy < 0) maxY * 0.72f else fy).coerceIn(0f, maxY.coerceAtLeast(0f))

        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(sizeDp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF1E1F24))
                .border(2.dp, Color.White.copy(alpha = .85f), CircleShape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = { prefs.edit().putFloat("x", fx).putFloat("y", fy).apply() },
                    ) { change, amount ->
                        change.consume()
                        val cx = if (fx < 0) x else fx
                        val cy = if (fy < 0) y else fy
                        fx = (cx + amount.x).coerceIn(0f, maxX)
                        fy = (cy + amount.y).coerceIn(0f, maxY)
                    }
                }
                .clickable { SimpleTimeTracker.openApp(ctx) },
            contentAlignment = Alignment.Center,
        ) {
            Image(icon, "Open Simple Time Tracker", Modifier.size(sizeDp - 8.dp).clip(CircleShape))
        }
    }
}

