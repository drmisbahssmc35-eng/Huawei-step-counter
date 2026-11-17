@file:OptIn(ExperimentalMaterial3Api::class)

package com.timetally.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetally.app.data.AppData
import com.timetally.app.data.RangeKind
import com.timetally.app.data.fmtDur
import com.timetally.app.data.rangeFor
import com.timetally.app.data.startMillis
import com.timetally.app.data.totalsIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val UntrackedColor = Color(0xFFB0B4BE)

private data class Slice(val label: String, val emoji: String, val color: Color, val ms: Long)

@Composable
fun StatsScreen(data: AppData) {
    val now = rememberNow()
    val ctx = LocalContext.current
    var kindName by rememberSaveable { mutableStateOf(RangeKind.DAY.name) }
    val kind = RangeKind.valueOf(kindName)
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val range = rangeFor(kind, offset, data.settings.weekStartsMonday, data)

    val totals = totalsIn(data, range.from, range.to, now)
    val tracked = totals.values.sum()
    val elapsed = (minOf(range.to, now) - range.from).coerceAtLeast(0)
    val untracked = if (data.settings.showUntracked && kind != RangeKind.ALL) (elapsed - tracked).coerceAtLeast(0) else 0L

    val slices = buildList {
        data.types.forEach { t -> totals[t.id]?.let { if (it > 0) add(Slice(t.name, t.emoji, t.color.asColor(), it)) } }
        val orphan = totals.filterKeys { k -> data.types.none { it.id == k } }.values.sum()
        if (orphan > 0) add(Slice("Deleted", "❓", Color.Gray, orphan))
        sortByDescending { it.ms }
        if (untracked > 0) add(Slice("Untracked", "⏳", UntrackedColor, untracked))
    }
    val whole = slices.sumOf { it.ms }.coerceAtLeast(1)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RangeKind.entries.forEach { k ->
                FilterChip(selected = k == kind, onClick = { kindName = k.name; offset = 0 }, label = { Text(k.label) })
            }
        }
        if (kind != RangeKind.ALL) {
            PeriodSwitcher(
                title = range.title,
                onPrev = { offset-- },
                onNext = { offset++ },
                onTitle = if (kind == RangeKind.DAY) ({
                    pickDate(ctx, range.firstDay ?: LocalDate.now()) {
                        offset = (it.toEpochDay() - LocalDate.now().toEpochDay()).toInt()
                    }
                }) else null,
            )
        } else Spacer(Modifier.height(12.dp))

        // Donut
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.surfaceVariant
            Canvas(Modifier.size(200.dp)) {
                val stroke = 34.dp.toPx()
                val d = size.minDimension - stroke
                val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                if (slices.isEmpty()) {
                    drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                } else {
                    var angle = -90f
                    slices.forEach { s ->
                        val sweep = 360f * s.ms / whole
                        drawArc(s.color, angle, sweep, false, tl, Size(d, d), style = Stroke(stroke))
                        angle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmtDur(tracked), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("tracked", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (kind != RangeKind.ALL) BarChart(data, kind, range.firstDay, now)

        if (slices.isEmpty()) {
            Text(
                "Nothing tracked in this period",
                Modifier.fillMaxWidth().padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        slices.forEach { s -> SliceRow(s, s.ms.toFloat() / whole) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SliceRow(s: Slice, frac: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(s.emoji, s.color.copy(alpha = 0.25f), 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(s.label, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmtDur(s.ms), fontWeight = FontWeight.SemiBold)
            Text(
                "  %d%%".format((frac * 100).toInt()), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.padding(start = 44.dp, top = 4.dp).fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(s.color))
        }
    }
}

/** Stacked bars: hours of the day, days of the week/month, or months of the year. */
@Composable
private fun BarChart(data: AppData, kind: RangeKind, first: LocalDate?, now: Long) {
    if (first == null) return
    data class Bucket(val label: String, val from: Long, val to: Long)
    val buckets: List<Bucket> = when (kind) {
        RangeKind.DAY -> (0 until 24).map { h ->
            val f = first.startMillis() + h * 3_600_000L
            Bucket(if (h % 6 == 0) "$h" else "", f, f + 3_600_000L)
        }
        RangeKind.WEEK -> (0 until 7).map { i ->
            val d = first.plusDays(i.toLong())
            Bucket(DateTimeFormatter.ofPattern("EEE").format(d).take(2), d.startMillis(), d.plusDays(1).startMillis())
        }
        RangeKind.MONTH -> (0 until first.lengthOfMonth()).map { i ->
            val d = first.plusDays(i.toLong())
            Bucket(if (i % 5 == 0) "${i + 1}" else "", d.startMillis(), d.plusDays(1).startMillis())
        }
        RangeKind.YEAR -> (0 until 12).map { i ->
            val d = first.plusMonths(i.toLong())
            Bucket(DateTimeFormatter.ofPattern("MMM").format(d).take(1), d.startMillis(), d.plusMonths(1).startMillis())
        }
        RangeKind.ALL -> emptyList()
    }
    val perBucket = buckets.map { b -> totalsIn(data, b.from, b.to, now) }
    val max = perBucket.maxOfOrNull { it.values.sum() }?.coerceAtLeast(1L) ?: 1L
    if (perBucket.all { it.isEmpty() }) return

    val order = data.types
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Peak: ${fmtDur(max)}", fontSize = 12.sp, color = labelColor)
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = buckets.size
            val slot = size.width / n
            val barW = slot * 0.7f
            perBucket.forEachIndexed { i, m ->
                var y = size.height
                val x = i * slot + (slot - barW) / 2
                order.forEach { t ->
                    val v = m[t.id] ?: 0L
                    if (v > 0) {
                        val h = size.height * v / max
                        drawRoundRect(t.color.asColor(), Offset(x, y - h), Size(barW, h), CornerRadius(2f, 2f))
                        y -= h
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            buckets.forEach { b ->
                Text(b.label, Modifier.weight(1f), fontSize = 10.sp, color = labelColor, maxLines = 1)
            }
        }
    }
}
