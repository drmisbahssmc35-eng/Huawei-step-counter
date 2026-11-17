package com.timetally.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetally.app.data.ActivityType
import com.timetally.app.data.fmtClockSec
import com.timetally.app.data.fmtDurSec
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One coloured block on the day timeline. [key] is handed back on tap. */
data class TimelineBlock(
    val key: Any,
    val start: Long,
    val end: Long,
    val type: ActivityType?,
    val comment: String,
    val live: Boolean,
)

private const val HOUR_MS = 3_600_000L
private val AxisWidth = 58.dp
private val MinBlock = 5.dp

/**
 * The whole day from 00:00 to 24:00 top to bottom, every block as tall as its duration.
 * zoom = 1 fits the day on screen; 2 and 4 stretch it and scroll.
 */
@Composable
fun DayTimeline(
    dayKey: Long,
    from: Long,
    blocks: List<TimelineBlock>,
    gaps: List<Pair<Long, Long>>,
    nowMs: Long?,
    zoom: Int,
    onBlock: (Any) -> Unit,
    onGap: (Long, Long) -> Unit,
) {
    val ctx = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val density = LocalDensity.current
    val placed = remember(blocks) { assignLanes(blocks) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val topPad = 8.dp
        val hourH: Dp = ((maxHeight - topPad * 2) / 24) * zoom
        val dayH = hourH * 24
        val areaW = maxWidth - AxisWidth - 8.dp
        fun yOf(ms: Long): Dp = hourH * ((ms - from).toFloat() / HOUR_MS)

        val scroll = rememberScrollState()
        // When zoomed, jump to "now" (today) or the first record.
        LaunchedEffect(dayKey, zoom) {
            if (zoom == 1) { scroll.scrollTo(0); return@LaunchedEffect }
            val target = nowMs ?: blocks.minOfOrNull { it.start } ?: (from + 8 * HOUR_MS)
            val px = with(density) { (yOf(target) - maxHeight / 3).toPx() }.toInt().coerceAtLeast(0)
            scroll.scrollTo(px)
        }

        val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(dayH + topPad * 2).padding(vertical = topPad)) {
                // Hour lines and labels on the right.
                for (h in 0..24) {
                    val y = hourH * h
                    Box(
                        Modifier.offset(y = y).padding(start = 4.dp, end = AxisWidth)
                            .fillMaxWidth().height(1.dp).background(lineColor)
                    )
                    val every = if (hourH < 22.dp) 2 else 1
                    if (h % every == 0) {
                        Text(
                            hourLabel(h, is24),
                            Modifier.align(Alignment.TopEnd).offset(y = y - 8.dp).width(AxisWidth - 4.dp),
                            fontSize = 12.sp, color = labelColor, maxLines = 1,
                        )
                    }
                }

                // Untracked gaps: tap to fill.
                gaps.forEach { (s, e) ->
                    val h = yOf(e) - yOf(s)
                    if (h >= 3.dp) Box(
                        Modifier
                            .offset(x = 4.dp, y = yOf(s))
                            .width(areaW)
                            .height(h)
                            .clickable { onGap(s, e) }
                    ) {
                        if (h >= 18.dp) Text(
                            "+ ${fmtDurSec(e - s)} untracked",
                            Modifier.align(Alignment.Center), fontSize = 11.sp,
                            color = labelColor.copy(alpha = 0.7f),
                        )
                    }
                }

                // Activity blocks.
                placed.forEach { p ->
                    val b = p.block
                    val top = yOf(b.start)
                    val h = maxOf(yOf(b.end) - top, MinBlock)
                    val laneW = areaW / p.lanes
                    BlockView(
                        b, is24, h,
                        Modifier
                            .offset(x = 4.dp + laneW * p.lane, y = top)
                            .width(laneW - 2.dp)
                            .height(h),
                    ) { onBlock(b.key) }
                }

                // Red "now" line.
                if (nowMs != null) {
                    val y = yOf(nowMs)
                    Box(
                        Modifier.offset(y = y - 1.dp).padding(end = AxisWidth)
                            .fillMaxWidth().height(2.dp).background(Color(0xFFFF3B30))
                    )
                    Box(Modifier.offset(x = 0.dp, y = y - 5.dp).size(10.dp).background(Color(0xFFFF3B30), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun BlockView(b: TimelineBlock, is24: Boolean, h: Dp, modifier: Modifier, onClick: () -> Unit) {
    val color = (b.type?.color ?: 0xFF9E9E9E).asColor()
    val fg = contentOn(color)
    val shape = RoundedCornerShape(if (h > 16.dp) 10.dp else 4.dp)
    Box(
        modifier
            .clip(shape)
            .background(color)
            .then(if (b.live) Modifier.border(2.dp, fg.copy(alpha = 0.8f), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (h > 26.dp) 4.dp else 0.dp),
    ) {
        if (h >= 18.dp) Column(Modifier.fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (b.type?.emoji ?: "❓") + " " + (b.type?.name ?: "Deleted activity"),
                    Modifier.weight(1f), color = fg, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (if (b.live) "▶ " else "") + fmtDurSec(b.end - b.start),
                    color = fg.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
                )
            }
            if (h >= 40.dp) Text(
                fmtClockSec(b.start, is24) + " - " + if (b.live) "now" else fmtClockSec(b.end, is24),
                color = fg.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1,
            )
            if (h >= 58.dp && b.comment.isNotBlank()) Text(
                b.comment, color = fg.copy(alpha = 0.85f), fontSize = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun hourLabel(h: Int, is24: Boolean): String =
    if (is24) "%02d:00".format(h % 24)
    else {
        val hh = h % 24
        val h12 = if (hh % 12 == 0) 12 else hh % 12
        "%02d %s".format(h12, if (hh < 12) "am" else "pm")
    }

private class Placed(val block: TimelineBlock, val lane: Int) { var lanes = 1 }

/** Side-by-side lanes for blocks that overlap (two timers at once). */
private fun assignLanes(blocks: List<TimelineBlock>): List<Placed> {
    val out = mutableListOf<Placed>()
    val cluster = mutableListOf<Placed>()
    val laneEnds = mutableListOf<Long>()
    var clusterEnd = Long.MIN_VALUE
    fun closeCluster() {
        val n = laneEnds.size.coerceAtLeast(1)
        cluster.forEach { it.lanes = n }
        out += cluster; cluster.clear(); laneEnds.clear()
    }
    blocks.sortedWith(compareBy({ it.start }, { -it.end })).forEach { b ->
        if (b.start >= clusterEnd && cluster.isNotEmpty()) closeCluster()
        var lane = laneEnds.indexOfFirst { it <= b.start }
        if (lane < 0) { lane = laneEnds.size; laneEnds += b.end } else laneEnds[lane] = b.end
        cluster += Placed(b, lane)
        clusterEnd = maxOf(if (cluster.size == 1) b.end else clusterEnd, b.end)
    }
    if (cluster.isNotEmpty()) closeCluster()
    return out
}

/** Bottom bar: five days around the selected one, an add button and a menu. */
@Composable
fun DayStrip(
    day: LocalDate,
    onPick: (LocalDate) -> Unit,
    onAdd: () -> Unit,
    listMode: Boolean,
    zoom: Int,
    onPickDate: () -> Unit,
    onToday: () -> Unit,
    onToggleList: () -> Unit,
    onZoom: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (-2..2).forEach { i ->
            val d = day.plusDays(i.toLong())
            val sel = i == 0
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .clickable { onPick(d) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (sel) DateTimeFormatter.ofPattern("MMM").format(d) else " ",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    DateTimeFormatter.ofPattern("EEE").format(d), fontSize = 13.sp,
                    color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    d.dayOfMonth.toString(),
                    fontSize = if (sel) 20.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        SquareButton(onAdd) { Icon(Icons.Filled.Add, "Add record") }
        Box {
            SquareButton({ menu = true }) { Icon(Icons.Filled.MoreHoriz, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Go to today") }, onClick = { menu = false; onToday() })
                DropdownMenuItem(text = { Text("Pick a date…") }, onClick = { menu = false; onPickDate() })
                DropdownMenuItem(
                    text = { Text(if (listMode) "Show timeline" else "Show as list") },
                    onClick = { menu = false; onToggleList() },
                )
                if (!listMode) DropdownMenuItem(
                    text = { Text("Zoom: ${zoom}× → ${if (zoom >= 4) 1 else zoom * 2}×") },
                    onClick = { menu = false; onZoom() },
                )
            }
        }
    }
}

@Composable
private fun SquareButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
