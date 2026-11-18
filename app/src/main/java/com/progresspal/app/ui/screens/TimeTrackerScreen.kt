package com.progresspal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progresspal.app.data.SimpleTimeTracker
import com.progresspal.app.data.SttCache
import com.progresspal.app.data.SttRecord
import com.progresspal.app.data.SttStatus
import com.progresspal.app.ui.components.rememberNow
import com.progresspal.app.ui.theme.Pal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HOUR_MS = 3_600_000L

private fun Long.asColor() = Color(this.toInt())
private fun contentOn(bg: Color): Color =
    if (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue > 0.62) Color(0xFF111111) else Color.White

/** Simple Time Tracker icons are either text/emoji or a drawable name we can't show here. */
private fun iconText(icon: String?, name: String?): String =
    if (icon.isNullOrBlank() || icon.startsWith("ic_") || icon.all { it.isLetterOrDigit() || it == '_' } && icon.length > 3)
        (name ?: "?").take(1).uppercase()
    else icon

private fun dur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return when { h > 0 -> "${h}h ${m}m ${sec}s"; m > 0 -> "${m}m ${sec}s"; else -> "${sec}s" }
}
private fun shortDur(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
private fun timer(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}
private fun clock(ms: Long, is24: Boolean): String =
    DateTimeFormatter.ofPattern(if (is24) "HH:mm:ss" else "h:mm:ss a").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
private fun LocalDate.startMs(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
fun TimeTrackerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cache by SimpleTimeTracker.cache.collectAsStateWithLifecycle()
    val status by SimpleTimeTracker.status.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dayEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }

    LaunchedEffect(Unit) { SimpleTimeTracker.syncSoon() }

    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.c.foreground) }
            Column(Modifier.weight(1f)) {
                Text("Time Tracker", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Pal.c.foreground)
                Text(
                    when (status) {
                        SttStatus.SYNCING -> "Syncing with Simple Time Tracker…"
                        else -> if (cache.lastSync > 0) "Simple Time Tracker · synced " +
                            DateTimeFormatter.ofPattern("d MMM, HH:mm").format(Instant.ofEpochMilli(cache.lastSync).atZone(ZoneId.systemDefault()))
                        else "Simple Time Tracker"
                    },
                    fontSize = 12.sp, color = Pal.c.mutedForeground,
                )
            }
            IconButton(onClick = { SimpleTimeTracker.syncSoon(7) }) { Icon(Icons.Filled.Refresh, "Sync now", tint = Pal.c.foreground) }
            IconButton(onClick = { SimpleTimeTracker.openApp(ctx) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open Simple Time Tracker", tint = Pal.c.foreground)
            }
        }

        StatusBanner(status)

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Timers", "Timeline", "Stats").forEachIndexed { i, label ->
                val sel = i == tab
                Text(
                    label,
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Pal.c.primary else Pal.c.muted)
                        .clickable { tab = i }.padding(vertical = 8.dp),
                    color = if (sel) Color.White else Pal.c.foreground,
                    textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> TimersTab(cache)
                1 -> TimelineTab(cache, dayEpoch) { dayEpoch = it }
                else -> StatsTab(cache)
            }
        }
    }
}

@Composable
private fun StatusBanner(status: SttStatus) {
    val text = when (status) {
        SttStatus.NOT_INSTALLED -> "Simple Time Tracker is not installed. Install it from the Play Store (version 1.61 or newer)."
        SttStatus.NO_ANSWER ->
            "Simple Time Tracker did not answer. Open it → Settings → Automated tracking → turn on \"Answer queries\", " +
                "then tap ⟳. On Huawei, also allow it to auto-launch (Settings → Apps → App launch)."
        else -> return
    }
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(Pal.c.destructive.copy(alpha = .12f)).padding(12.dp),
        color = Pal.c.foreground, fontSize = 13.sp,
    )
}

/* ---------------- Timers ---------------- */

@Composable
private fun TimersTab(cache: SttCache) {
    val now by rememberNow()
    val todayTotals = totals(cache.days[LocalDate.now().toString()].orEmpty())
    val byId = cache.activities.associateBy { it.id }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(cache.running, key = { "run-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { r ->
            val a = byId[r.id] ?: return@items
            val bg = a.color.asColor(); val fg = contentOn(bg)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
                    .clickable { SimpleTimeTracker.toggle(a) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(iconText(a.icon, a.name), fontSize = 26.sp, color = fg)
                Spacer(Modifier.width(10.dp))
                Text(a.name, Modifier.weight(1f), color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timer(now - r.startedAt), color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Stop, "Stop", tint = fg)
            }
        }
        if (cache.running.size > 1) item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedButton(onClick = { SimpleTimeTracker.stopAll() }) { Text("Stop all", color = Pal.c.foreground) }
        }
        if (cache.activities.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "No activities yet. They appear here after the first sync with Simple Time Tracker.",
                Modifier.padding(16.dp), color = Pal.c.mutedForeground,
            )
        }
        items(cache.activities, key = { it.id }) { a ->
            val run = cache.running.firstOrNull { it.id == a.id }
            val c = a.color.asColor()
            val on = run != null
            val fg = if (on) contentOn(c) else Pal.c.foreground
            Column(
                Modifier.aspectRatio(0.95f).clip(RoundedCornerShape(16.dp))
                    .background(if (on) c else Pal.c.card)
                    .border(2.dp, c.copy(alpha = if (on) 1f else .5f), RoundedCornerShape(16.dp))
                    .clickable { SimpleTimeTracker.toggle(a) }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(44.dp).background(if (on) Color.Transparent else c.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(iconText(a.icon, a.name), fontSize = 22.sp, color = fg, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(a.name, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (run != null) timer(now - run.startedAt) else todayTotals[a.id]?.let { shortDur(it) } ?: " ",
                    color = fg.copy(alpha = if (on) 1f else .6f), fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun totals(records: List<SttRecord>): Map<Long, Long> =
    records.groupBy { it.activityId ?: -1 }.mapValues { (_, l) -> l.sumOf { it.endedAt - it.startedAt } }

/* ---------------- Timeline ---------------- */

private data class Block(val start: Long, val end: Long, val name: String, val icon: String, val color: Color, val live: Boolean)
private class Placed(val b: Block, val lane: Int) { var lanes = 1 }

@Composable
private fun TimelineTab(cache: SttCache, dayEpoch: Long, onDay: (Long) -> Unit) {
    val ctx = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val now by rememberNow()
    val day = LocalDate.ofEpochDay(dayEpoch)
    val from = day.startMs(); val to = day.plusDays(1).startMs()
    LaunchedEffect(dayEpoch) { if (cache.days[day.toString()] == null) SimpleTimeTracker.syncDay(day) }

    val byId = cache.activities.associateBy { it.id }
    val blocks = buildList {
        cache.days[day.toString()].orEmpty().forEach { r ->
            val a = r.activityId?.let { byId[it] }
            add(Block(maxOf(r.startedAt, from), minOf(r.endedAt, to), a?.name ?: r.activityName ?: "?",
                iconText(a?.icon ?: r.activityIcon, a?.name ?: r.activityName), (a?.color ?: r.activityColor ?: 0xFF888888).asColor(), false))
        }
        cache.running.forEach { r ->
            val a = byId[r.id] ?: return@forEach
            if (r.startedAt < to && now > from) add(Block(maxOf(r.startedAt, from), minOf(now, to), a.name, iconText(a.icon, a.name), a.color.asColor(), true))
        }
    }
    val tracked = blocks.sumOf { it.end - it.start }
    var drag by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Tracked ${shortDur(tracked)}",
            Modifier.padding(horizontal = 16.dp, vertical = 2.dp), fontSize = 12.sp, color = Pal.c.mutedForeground,
        )
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = {
                    if (drag > 140) onDay(dayEpoch - 1) else if (drag < -140 && dayEpoch < LocalDate.now().toEpochDay()) onDay(dayEpoch + 1)
                    drag = 0f
                }) { _, dx -> drag += dx }
            }
        ) {
            val pad = 8.dp
            val hourH: Dp = (maxHeight - pad * 2) / 24
            val axis = 58.dp
            val areaW = maxWidth - axis - 8.dp
            fun y(ms: Long): Dp = hourH * ((ms - from).toFloat() / HOUR_MS)
            val line = Pal.c.border
            Box(Modifier.fillMaxSize().padding(vertical = pad)) {
                for (h in 0..24) {
                    Box(Modifier.offset(y = hourH * h).padding(start = 4.dp, end = axis).fillMaxWidth().height(1.dp).background(line))
                    if (h % (if (hourH < 22.dp) 2 else 1) == 0) Text(
                        if (is24) "%02d:00".format(h % 24) else "%02d %s".format(if (h % 12 == 0) 12 else h % 12, if (h % 24 < 12) "am" else "pm"),
                        Modifier.align(Alignment.TopEnd).offset(y = hourH * h - 8.dp).width(axis - 4.dp),
                        fontSize = 12.sp, color = Pal.c.mutedForeground, maxLines = 1,
                    )
                }
                lanes(blocks).forEach { p ->
                    val b = p.b
                    val top = y(b.start)
                    val h = maxOf(y(b.end) - top, 4.dp)
                    val w = areaW / p.lanes
                    val fg = contentOn(b.color)
                    Box(
                        Modifier.offset(x = 4.dp + w * p.lane, y = top).width(w - 2.dp).height(h)
                            .clip(RoundedCornerShape(if (h > 16.dp) 10.dp else 3.dp)).background(b.color)
                            .padding(horizontal = 8.dp, vertical = if (h > 26.dp) 4.dp else 0.dp),
                    ) {
                        if (h >= 18.dp) Column(Modifier.fillMaxHeight()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${b.icon} ${b.name}", Modifier.weight(1f), color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text((if (b.live) "▶ " else "") + dur(b.end - b.start), color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                            }
                            if (h >= 40.dp) Text(
                                clock(b.start, is24) + " - " + if (b.live) "now" else clock(b.end, is24),
                                color = fg.copy(alpha = .85f), fontSize = 12.sp, maxLines = 1,
                            )
                        }
                    }
                }
                if (now in from until to) {
                    Box(Modifier.offset(y = y(now) - 1.dp).padding(end = axis).fillMaxWidth().height(2.dp).background(Color(0xFFFF3B30)))
                }
            }
        }
        // Day strip
        val today = LocalDate.now()
        Row(
            Modifier.fillMaxWidth().background(Pal.c.card).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (-2..2).forEach { i ->
                val d = day.plusDays(i.toLong())
                val sel = i == 0
                val future = d.isAfter(today)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Pal.c.muted else Color.Transparent)
                        .clickable(enabled = !future) { onDay(d.toEpochDay()) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val col = when { future -> Pal.c.mutedForeground.copy(alpha = .4f); d == today -> Pal.c.primary; else -> Pal.c.foreground }
                    Text(if (sel) DateTimeFormatter.ofPattern("MMM").format(d) else " ", fontSize = 10.sp, color = Pal.c.mutedForeground)
                    Text(DateTimeFormatter.ofPattern("EEE").format(d), fontSize = 13.sp, color = col)
                    Text(d.dayOfMonth.toString(), fontSize = if (sel) 20.sp else 16.sp, fontWeight = FontWeight.Bold, color = col)
                }
            }
        }
    }
}

private fun lanes(blocks: List<Block>): List<Placed> {
    val out = mutableListOf<Placed>(); val cluster = mutableListOf<Placed>(); val ends = mutableListOf<Long>()
    var clusterEnd = Long.MIN_VALUE
    fun close() { val n = ends.size.coerceAtLeast(1); cluster.forEach { it.lanes = n }; out += cluster; cluster.clear(); ends.clear() }
    blocks.sortedBy { it.start }.forEach { b ->
        if (b.start >= clusterEnd && cluster.isNotEmpty()) close()
        var lane = ends.indexOfFirst { it <= b.start }
        if (lane < 0) { lane = ends.size; ends += b.end } else ends[lane] = b.end
        cluster += Placed(b, lane)
        clusterEnd = if (cluster.size == 1) b.end else maxOf(clusterEnd, b.end)
    }
    if (cluster.isNotEmpty()) close()
    return out
}

/* ---------------- Stats ---------------- */

@Composable
private fun StatsTab(cache: SttCache) {
    var range by rememberSaveable { mutableIntStateOf(0) } // 0 today, 1 week, 2 month, 3 all synced
    val today = LocalDate.now()
    val firstDay = when (range) { 0 -> today; 1 -> today.minusDays(6); 2 -> today.minusDays(29); else -> LocalDate.MIN }
    val records = cache.days.filterKeys { runCatching { !LocalDate.parse(it).isBefore(firstDay) }.getOrDefault(false) }.values.flatten()
    val byId = cache.activities.associateBy { it.id }
    val rows = records.groupBy { it.activityId ?: -1 }
        .map { (id, l) -> Triple(byId[id], l.first(), l.sumOf { it.endedAt - it.startedAt }) }
        .sortedByDescending { it.third }
    val total = rows.sumOf { it.third }.coerceAtLeast(1)
    val daysCount = cache.days.keys.count { runCatching { !LocalDate.parse(it).isBefore(firstDay) }.getOrDefault(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Today", "7 days", "30 days", "All synced").forEachIndexed { i, l ->
                Text(
                    l, Modifier.clip(RoundedCornerShape(50)).background(if (i == range) Pal.c.primary.copy(alpha = .2f) else Pal.c.muted)
                        .clickable { range = i }.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Pal.c.foreground, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Total ${shortDur(rows.sumOf { it.third })} · $daysCount day(s) synced", color = Pal.c.mutedForeground, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) Text("Nothing synced for this period yet.", color = Pal.c.mutedForeground, modifier = Modifier.padding(vertical = 24.dp))
        rows.forEach { (a, sample, ms) ->
            val color = (a?.color ?: sample.activityColor ?: 0xFF888888).asColor()
            val name = a?.name ?: sample.activityName ?: "?"
            val frac = ms.toFloat() / total
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).background(color.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(iconText(a?.icon ?: sample.activityIcon, name), fontSize = 16.sp, color = Pal.c.foreground)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(name, Modifier.weight(1f), color = Pal.c.foreground, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(shortDur(ms), color = Pal.c.foreground, fontWeight = FontWeight.SemiBold)
                    Text("  ${(frac * 100).toInt()}%", color = Pal.c.mutedForeground, fontSize = 12.sp)
                }
                Box(Modifier.padding(start = 42.dp, top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Pal.c.muted)) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp).background(color))
                }
            }
        }
    }
}

/** One-line summary for the Home card. */
fun timeTrackerSubtitle(cache: SttCache): String {
    val names = cache.activities.associate { it.id to it.name }
    val running = cache.running.mapNotNull { names[it.id] }
    return when {
        running.isNotEmpty() -> "Running: " + running.joinToString(", ")
        cache.activities.isNotEmpty() -> "Today: " + shortDur(cache.days[LocalDate.now().toString()].orEmpty().sumOf { it.endedAt - it.startedAt })
        else -> "Synced with Simple Time Tracker"
    }
}
