package com.progresspal.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** One block on the zoomable timeline. Gaps are drawn as tappable empty space. */
data class TLItem(
    val key: Any,
    val start: Long,
    val end: Long,
    val color: Color,
    val title: String,
    val right: String,
    val sub: String,
    val live: Boolean = false,
    val gap: Boolean = false,
    /** Which column it sits in (0 = first). */
    val column: Int = 0,
    /** 1 = bright, lower = dimmed (e.g. a missed plan). */
    val alpha: Float = 1f,
    /** Small badge in the corner, e.g. "✓". */
    val badge: String = "",
    /** Drawn as an outline with a light fill (e.g. an upcoming plan). */
    val outlined: Boolean = false,
)

private const val DAY_MS = 24 * 3_600_000L
private const val MAX_ZOOM = 400f

/**
 * A day from 00:00 to 24:00. Pinch with two fingers to zoom in or out (down to minutes),
 * drag with one finger to scroll, swipe sideways to change day, tap a block to open it.
 */
@Composable
fun ZoomTimeline(
    from: Long,
    items: List<TLItem>,
    nowMs: Long?,
    is24: Boolean,
    lineColor: Color,
    labelColor: Color,
    onItem: (TLItem) -> Unit,
    onSwipeDay: (Int) -> Unit,
    /** Column titles; with two or more the area is split side by side. */
    columns: List<String> = emptyList(),
    /** Tap on empty space in a column: (column, time). */
    onEmptyTap: ((Int, Long) -> Unit)? = null,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val nCols = columns.size.coerceAtLeast(1)
    val placed = remember(items) {
        items.filterNot { it.gap }.groupBy { it.column }.flatMap { (_, l) -> placeLanes(l) }
    }
    val emptyTap by rememberUpdatedState(onEmptyTap)
    val gaps = remember(items) { items.filter { it.gap } }
    val swipe by rememberUpdatedState(onSwipeDay)

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val wPx = constraints.maxWidth.toFloat()
        val padPx = with(density) { 10.dp.toPx() }
        val axisPx = with(density) { 60.dp.toPx() }
        val areaPx = wPx - axisPx - with(density) { 8.dp.toPx() }
        val slop = with(density) { 8.dp.toPx() }
        val swipePx = with(density) { 70.dp.toPx() }

        fun dayPx(z: Float) = (hPx - 2 * padPx) * z
        fun maxOffset(z: Float) = (dayPx(z) + 2 * padPx - hPx).coerceAtLeast(0f)
        val off = offset.coerceIn(0f, maxOffset(zoom))

        val pxPerMs = dayPx(zoom) / DAY_MS
        fun y(ms: Long): Float = padPx + (ms - from) * pxPerMs - off

        fun transform(cy: Float, panY: Float, z: Float) {
            val nz = (zoom * z).coerceIn(1f, MAX_ZOOM)
            val cur = offset.coerceIn(0f, maxOffset(zoom))
            val dayY = cur + cy - padPx
            val newOffset = dayY * (nz / zoom) - (cy - padPx) - panY
            zoom = nz
            offset = newOffset.coerceIn(0f, maxOffset(nz))
        }

        Box(
            Modifier.fillMaxSize().pointerInput(hPx) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dragging = false
                    var multi = false
                    var totalX = 0f
                    var totalY = 0f
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val down = ev.changes.count { it.pressed }
                        if (down >= 2) multi = true
                        val pan = ev.calculatePan()
                        totalX += pan.x; totalY += pan.y
                        if (!dragging && (multi || abs(totalX) > slop || abs(totalY) > slop)) dragging = true
                        if (dragging) {
                            val c = ev.calculateCentroid(useCurrent = true)
                            val cy = if (c == Offset.Unspecified) hPx / 2 else c.y
                            val z = if (down >= 2) ev.calculateZoom() else 1f
                            // One finger moving mostly sideways is a day swipe, not a scroll.
                            val panY = if (!multi && abs(totalX) > abs(totalY) * 1.5f) 0f else pan.y
                            transform(cy, panY, z)
                            ev.changes.forEach { it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (!multi && abs(totalX) > swipePx && abs(totalX) > abs(totalY) * 1.5f) {
                        swipe(if (totalX > 0) -1 else 1)
                    }
                }
            }
        ) {
            val visFrom = from + ((off - padPx) / pxPerMs).toLong()
            val visTo = visFrom + (hPx / pxPerMs).toLong()

            // Grid: pick a step so lines are at least ~24dp apart (2h … 1min).
            val minGap = with(density) { 24.dp.toPx() }
            val stepMin = listOf(1, 2, 5, 10, 15, 30, 60, 120, 180)
                .firstOrNull { it * 60_000L * pxPerMs >= minGap } ?: 180
            val stepMs = stepMin * 60_000L
            val firstTick = from + floor((visFrom - from).toDouble() / stepMs).toLong().coerceAtLeast(0) * stepMs
            val ticks = generateSequence(firstTick) { it + stepMs }
                .takeWhile { it <= minOf(visTo + stepMs, from + DAY_MS) }.toList()

            Canvas(Modifier.fillMaxSize()) {
                ticks.forEach { t ->
                    val yy = y(t)
                    val hour = (t - from) % 3_600_000L == 0L
                    drawLine(
                        lineColor.copy(alpha = if (hour) 0.55f else 0.25f),
                        Offset(4f, yy), Offset(wPx - axisPx, yy), strokeWidth = if (hour) 2f else 1f,
                    )
                }
            }
            val fmt = DateTimeFormatter.ofPattern(
                if (stepMin >= 60) (if (is24) "HH:00" else "hh a") else (if (is24) "HH:mm" else "h:mm a")
            )
            ticks.forEach { t ->
                val label = if (t - from == DAY_MS) (if (is24) "24:00" else "12 AM")
                else fmt.format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())).lowercase()
                Text(
                    label,
                    Modifier
                        .offset { IntOffset((wPx - axisPx + 6).roundToInt(), (y(t) - 22).roundToInt()) }
                        .width(with(density) { (axisPx - 6).toDp() }),
                    fontSize = 11.sp, color = labelColor, maxLines = 1,
                )
            }

            val colW = areaPx / nCols
            // Tap on empty space → callback with the time under the finger.
            if (onEmptyTap != null) for (c in 0 until nCols) {
                Box(
                    Modifier
                        .offset { IntOffset((4f + colW * c).roundToInt(), 0) }
                        .size(width = with(density) { colW.toDp() }, height = with(density) { hPx.toDp() })
                        .pointerInput(hPx, c, from) {
                            detectTapGestures { pos ->
                                val z = zoom
                                val o = offset.coerceIn(0f, maxOffset(z))
                                val ms = from + ((pos.y - padPx + o) / (dayPx(z) / DAY_MS)).toLong()
                                emptyTap?.invoke(c, ms.coerceIn(from, from + DAY_MS - 60_000))
                            }
                        }
                )
            }

            // Untracked gaps.
            gaps.forEach { g ->
                Segment(y(g.start), y(g.end), hPx, 4f + colW * g.column, colW, density.density) {
                    Box(Modifier.fillMaxSize().clickable { onItem(g) }, contentAlignment = Alignment.Center) {
                        Text(g.title, fontSize = 11.sp, color = labelColor.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
            }

            // Blocks; labels stick to the top of the visible part.
            placed.forEach { p ->
                val laneW = colW / p.lanes
                val x0 = 4f + colW * p.item.column + laneW * p.lane
                Segment(y(p.item.start), y(p.item.end), hPx, x0, laneW - 4f, density.density) { hDp ->
                    val b = p.item
                    val shape = RoundedCornerShape(if (hDp > 16) 10.dp else 3.dp)
                    val fill = if (b.outlined) b.color.copy(alpha = 0.18f) else b.color
                    val fg = if (b.outlined) labelColor.copy(alpha = 1f) else contentOn(b.color)
                    Box(
                        Modifier.fillMaxSize()
                            .alpha(b.alpha)
                            .clip(shape)
                            .background(fill)
                            .then(if (b.outlined) Modifier.border(2.dp, b.color, shape) else Modifier)
                            .clickable { onItem(b) }
                            .padding(horizontal = 8.dp, vertical = if (hDp > 26) 4.dp else 0.dp),
                    ) {
                        if (b.badge.isNotEmpty() && hDp >= 12) Text(
                            b.badge,
                            Modifier.align(Alignment.TopEnd).padding(top = if (hDp > 26) 18.dp else 0.dp)
                                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.9f))
                                .padding(horizontal = 5.dp),
                            color = Color(0xFF111111), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                        if (hDp >= 18) Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    b.title, Modifier.weight(1f), color = fg, fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(b.right, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                            }
                            if (hDp >= 40) Text(b.sub, color = fg.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (nowMs != null) {
                val ny = y(nowMs)
                if (ny in 0f..hPx) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawLine(Color(0xFFFF3B30), Offset(0f, ny), Offset(wPx - axisPx, ny), strokeWidth = 4f)
                        drawCircle(Color(0xFFFF3B30), 10f, Offset(8f, ny))
                    }
                }
            }

            if (nCols > 1) {
                Canvas(Modifier.fillMaxSize()) {
                    for (c in 1 until nCols) {
                        val x = 4f + colW * c - 2f
                        drawLine(lineColor.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, hPx), strokeWidth = 2f)
                    }
                }
                columns.forEachIndexed { c, title ->
                    Text(
                        title,
                        Modifier
                            .offset { IntOffset((4f + colW * c + 6f).roundToInt(), 4) }
                            .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (zoom > 1.05f) {
                Text(
                    "${if (zoom < 10) "%.1f".format(zoom) else zoom.roundToInt().toString()}×  ·  tap to reset",
                    Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
                        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); zoom = 1f; offset = 0f } }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Places a block between screen y [top]..[bottom], clipped to the visible window. */
@Composable
private fun Segment(
    top: Float,
    bottom: Float,
    hPx: Float,
    xPx: Float,
    wPx: Float,
    density: Float,
    content: @Composable (heightDp: Float) -> Unit,
) {
    if (bottom < -4f || top > hPx + 4f || wPx <= 0f) return
    val t = maxOf(top, -4f)
    val b = maxOf(minOf(bottom, hPx + 4f), t + 4f * density)
    val hDp = (b - t) / density
    Box(
        Modifier
            .offset { IntOffset(xPx.roundToInt(), t.roundToInt()) }
            .size(width = (wPx / density).dp, height = hDp.dp)
    ) { content(hDp) }
}

private class Lane(val item: TLItem, val lane: Int) { var lanes = 1 }

private fun placeLanes(items: List<TLItem>): List<Lane> {
    val out = mutableListOf<Lane>(); val cluster = mutableListOf<Lane>(); val ends = mutableListOf<Long>()
    var clusterEnd = Long.MIN_VALUE
    fun close() { val n = ends.size.coerceAtLeast(1); cluster.forEach { it.lanes = n }; out += cluster; cluster.clear(); ends.clear() }
    items.sortedWith(compareBy({ it.start }, { -it.end })).forEach { b ->
        if (b.start >= clusterEnd && cluster.isNotEmpty()) close()
        var lane = ends.indexOfFirst { it <= b.start }
        if (lane < 0) { lane = ends.size; ends += b.end } else ends[lane] = b.end
        cluster += Lane(b, lane)
        clusterEnd = if (cluster.size == 1) b.end else maxOf(clusterEnd, b.end)
    }
    if (cluster.isNotEmpty()) close()
    return out
}

private fun contentOn(bg: Color): Color =
    if (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue > 0.62) Color(0xFF111111) else Color.White
