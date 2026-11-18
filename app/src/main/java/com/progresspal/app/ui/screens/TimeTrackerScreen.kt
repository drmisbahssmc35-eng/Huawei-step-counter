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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import com.progresspal.app.data.AppData
import com.progresspal.app.data.PlanItem
import com.progresspal.app.data.PlanState
import com.progresspal.app.data.Planner
import com.progresspal.app.data.planState
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
import com.progresspal.app.ui.components.SttIcon
import com.progresspal.app.ui.components.toast
import com.progresspal.app.ui.components.TLItem
import com.progresspal.app.ui.components.ZoomTimeline
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
        (name ?: "?").replace(Regex("^\\d+\\s*[.)\\-:]?\\s*"), "").ifBlank { name ?: "?" }.take(1).uppercase()
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
fun TimeTrackerScreen(app: AppData, onBack: () -> Unit) {
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
            listOf("Timeline", "Activity List", "Stats").forEachIndexed { i, label ->
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
                0 -> TimelineTab(cache, app, dayEpoch, onOpenTimers = { tab = 1 }) { dayEpoch = it }
                1 -> TimersTab(cache)
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
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (cache.running.size > 1) item {
            OutlinedButton(onClick = { SimpleTimeTracker.stopAll() }) { Text("Stop all (${cache.running.size} running)", color = Pal.c.foreground) }
        }
        if (cache.activities.isEmpty()) item {
            Text(
                "No activities yet. They appear here after the first sync with Simple Time Tracker.",
                Modifier.padding(16.dp), color = Pal.c.mutedForeground,
            )
        }
        // One full-width coloured bar per activity, like Simple Time Tracker. Tap = start / stop.
        items(cache.activities, key = { it.id }) { a ->
            val run = cache.running.firstOrNull { it.id == a.id }
            val c = a.color.asColor()
            val fg = Color.White // Simple Time Tracker always uses white on the activity colour
            val shape = RoundedCornerShape(8.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(shape)
                    .background(c)
                    .then(if (run != null) Modifier.border(3.dp, Color.White, shape) else Modifier)
                    .clickable { SimpleTimeTracker.tap(a) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SttIcon(a.icon, a.name, fg, 34.dp)
                Spacer(Modifier.width(18.dp))
                Text(
                    a.name, Modifier.weight(1f), color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (run != null) {
                    Text(timer(now - run.startedAt), color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Stop, "Stop", tint = fg)
                } else todayTotals[a.id]?.let {
                    Text(shortDur(it), color = fg.copy(alpha = .85f), fontSize = 13.sp)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.c.muted)
                    .clickable { SimpleTimeTracker.setSwitchMode(!cache.switchMode) }.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Switch mode", color = Pal.c.foreground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        if (cache.switchMode) "Tap an activity to switch to it (the running one stops)"
                        else "Tap an activity to run it alongside the others",
                        color = Pal.c.mutedForeground, fontSize = 12.sp,
                    )
                }
                Switch(checked = cache.switchMode, onCheckedChange = { SimpleTimeTracker.setSwitchMode(it) })
            }
        }
        item {
            val ctx = LocalContext.current
            val missing = remember(cache.activities, cache.linkedTasks) { SimpleTimeTracker.dailyTasksMissingInStt() }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.c.muted)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { SimpleTimeTracker.setSyncDailyTasks(!cache.syncDailyTasks) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sync Daily Tasks", color = Pal.c.foreground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Pairs Daily Tasks with activities of the same name. Add them from the suggestions in Daily Tasks.",
                            color = Pal.c.mutedForeground, fontSize = 12.sp,
                        )
                    }
                    Switch(checked = cache.syncDailyTasks, onCheckedChange = { SimpleTimeTracker.setSyncDailyTasks(it) })
                }
                if (cache.syncDailyTasks) {
                    Text("Tick the Daily Task when tracked for at least", color = Pal.c.foreground, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 5, 15, 30, 0).forEach { m ->
                            val sel = cache.autoCompleteMinutes == m
                            Text(
                                if (m == 0) "Off" else "${m}m",
                                Modifier.clip(RoundedCornerShape(50))
                                    .background(if (sel) Pal.c.primary else Pal.c.card)
                                    .clickable { SimpleTimeTracker.setAutoCompleteMinutes(m) }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                color = if (sel) Color.White else Pal.c.foreground, fontSize = 13.sp,
                            )
                        }
                    }
                }
                var showMissing by remember { mutableStateOf(false) }
                if (cache.syncDailyTasks && missing.isNotEmpty() && cache.activities.isNotEmpty()) TextButton(onClick = { showMissing = !showMissing }) {
                    Text(
                        "${missing.size} Daily Task${if (missing.size == 1) " has" else "s have"} no activity in Simple Time Tracker " +
                            if (showMissing) "▲" else "▼",
                        color = Pal.c.mutedForeground, fontSize = 12.sp,
                    )
                }
                if (showMissing && cache.syncDailyTasks && missing.isNotEmpty() && cache.activities.isNotEmpty()) {
                    Text(
                        missing.joinToString(", ") +
                            ". Simple Time Tracker doesn't let other apps create activities — add them there with the same name and they will pair automatically.",
                        color = Pal.c.mutedForeground, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp),
                    )
                    TextButton(onClick = { SimpleTimeTracker.openApp(ctx) }) { Text("Open Simple Time Tracker") }
                }
            }
        }
    }
}

private fun totals(records: List<SttRecord>): Map<Long, Long> =
    records.groupBy { it.activityId ?: -1 }.mapValues { (_, l) -> l.sumOf { it.endedAt - it.startedAt } }

/* ---------------- Timeline ---------------- */

private data class Block(val start: Long, val end: Long, val name: String, val icon: String, val color: Color, val live: Boolean, val activityId: Long? = null, val recordId: Long? = null)
private class Placed(val b: Block, val lane: Int) { var lanes = 1 }

@Composable
private fun TimelineTab(cache: SttCache, app: AppData, dayEpoch: Long, onOpenTimers: () -> Unit, onDay: (Long) -> Unit) {
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
                iconText(a?.icon ?: r.activityIcon, a?.name ?: r.activityName), (a?.color ?: r.activityColor ?: 0xFF888888).asColor(), false, r.activityId, r.id))
        }
        cache.running.forEach { r ->
            val a = byId[r.id] ?: return@forEach
            if (r.startedAt < to && now > from) add(Block(maxOf(r.startedAt, from), minOf(now, to), a.name, iconText(a.icon, a.name), a.color.asColor(), true, a.id))
        }
    }
    val tracked = blocks.sumOf { it.end - it.start }
    val planner by Planner.data.collectAsStateWithLifecycle()
    val planOn = planner.enabled
    val plans = planner.plans.filter { it.end > from && it.start < to }
    val states = plans.associate { it.id to planState(it, cache, now) }
    val donePlans = plans.filter { states[it.id]?.first == PlanState.DONE }
    var editing by remember { mutableStateOf<Pair<PlanItem, Boolean>?>(null) }
    var deleting by remember { mutableStateOf<Any?>(null) }

    val items = blocks.map { b ->
        // An actual block that fulfils a plan gets a ✓.
        val fulfils = planOn && donePlans.any { p -> p.activityId != null && p.activityId == b.activityId && b.end > p.start && b.start < p.end }
        TLItem(
            key = b, start = b.start, end = b.end, color = b.color,
            title = "${b.icon} ${b.name}",
            right = (if (b.live) "▶ " else "") + dur(b.end - b.start),
            sub = clock(b.start, is24) + " - " + if (b.live) "now" else clock(b.end, is24),
            live = b.live, column = 0, badge = if (fulfils) "✓" else "",
        )
    } + if (!planOn) emptyList() else plans.map { p ->
        val (st, share) = states[p.id] ?: (PlanState.UPCOMING to 0f)
        val c = (p.color ?: 0xFF5B8DEF).asColor()
        TLItem(
            key = p, start = maxOf(p.start, from), end = minOf(p.end, to), color = c,
            title = p.title,
            right = when (st) {
                PlanState.DONE -> "Done"
                PlanState.MISSED -> "${(share * 100).toInt()}%"
                PlanState.ACTIVE -> "${(share * 100).toInt()}%"
                PlanState.UPCOMING -> fmtMinutes(((p.end - p.start) / 60_000).toInt())
            },
            sub = clock(p.start, is24).substringBeforeLast(":") + " - " + clock(p.end, is24).substringBeforeLast(":") +
                listOfNotNull(p.subtitle, p.activityName?.let { "⏱ $it" }).let { if (it.isEmpty()) "" else "\n" + it.joinToString(" · ") },
            column = 1,
            alpha = when (st) { PlanState.MISSED -> 0.35f; PlanState.UPCOMING -> 0.9f; else -> 1f },
            badge = when (st) { PlanState.DONE -> "✓"; PlanState.MISSED -> "✗"; else -> "" },
            outlined = st == PlanState.UPCOMING || st == PlanState.ACTIVE,
        )
    }

    fun newPlanAt(ms: Long) {
        val step = 5 * 60_000L
        val start = (ms / step) * step
        editing = PlanItem(start = start, end = start + 30 * 60_000L, title = "") to true
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tracked ${shortDur(tracked)}", fontSize = 12.sp, color = Pal.c.mutedForeground)
                if (planOn) {
                    val done = states.values.count { it.first == PlanState.DONE }
                    val missed = states.values.count { it.first == PlanState.MISSED }
                    val left = plans.size - done - missed
                    Text(
                        if (plans.isEmpty()) "Tap the Planner column to plan · double-tap a plan to start it · hold to delete" else "Plan: $done ✓ · $missed ✗ · $left to go",
                        fontSize = 12.sp, color = Pal.c.mutedForeground,
                    )
                }
            }
            if (planOn) TextButton(onClick = {
                // Start right after the last plan of this day, otherwise 1 minute from now (or 9:00 on other days).
                val nextMinute = (now / 60_000L + 1) * 60_000L
                val lastEnd = planner.plans.filter { it.start in from until to }.maxOfOrNull { it.end }
                val isToday = day == LocalDate.now()
                val base = when {
                    lastEnd != null && (!isToday || lastEnd > now) -> lastEnd
                    isToday -> nextMinute
                    else -> day.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                editing = PlanItem(start = base, end = base + 30 * 60_000L, title = "") to true
            }) { Text("+ Plan", color = Pal.c.primary) }
            Text("Planner", fontSize = 12.sp, color = Pal.c.mutedForeground)
            Switch(checked = planOn, onCheckedChange = { Planner.setEnabled(it) }, modifier = Modifier.padding(start = 4.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ZoomTimeline(
                from = from, items = items,
                nowMs = if (now in from until to) now else null,
                is24 = is24,
                lineColor = Pal.c.mutedForeground,
                labelColor = Pal.c.mutedForeground,
                onItem = { item ->
                    val plan = item.key as? PlanItem
                    if (plan != null) editing = plan to false else onOpenTimers()
                },
                onItemLongPress = { item -> deleting = item.key },
                onItemDoubleTap = { item ->
                    val plan = item.key as? PlanItem
                    if (plan != null) {
                        val act = cache.activities.firstOrNull { it.id == plan.activityId }
                            ?: cache.activities.firstOrNull { it.name.equals(plan.title, true) }
                        if (act == null) toast(ctx, "Link this plan to an activity first (tap it → \"Counts as done when I track\")")
                        else if (SimpleTimeTracker.isRunning(act.id)) toast(ctx, "${act.name} is already running")
                        else { SimpleTimeTracker.start(act); toast(ctx, "Started ${act.name}") }
                    }
                },
                onSwipeDay = { d -> onDay(dayEpoch + d) },
                columns = if (planOn) listOf("Actual", "Planner") else emptyList(),
                onEmptyTap = if (planOn) ({ col, ms -> if (col == 1) newPlanAt(ms) }) else null,
            )
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
                val future = false
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
    editing?.let { (p, isNew) -> PlanEditor(p, isNew, app, cache) { editing = null } }
    deleting?.let { key -> DeleteDialog(key, is24, cache) { deleting = null } }
}

/** Long-press menu: delete a plan, or hide / stop an actual block. */
@Composable
private fun DeleteDialog(key: Any, is24: Boolean, cache: SttCache, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val hm: (Long) -> String = { clock(it, is24).substringBeforeLast(":") }
    when (key) {
        is PlanItem -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Pal.c.card,
            title = { Text("Delete plan?", color = Pal.c.foreground) },
            text = { Text("\"${key.title}\" · ${hm(key.start)} – ${hm(key.end)}", color = Pal.c.foreground) },
            confirmButton = {
                TextButton(onClick = { Planner.delete(key.id); toast(ctx, "Plan deleted"); onDismiss() }) {
                    Text("Delete", color = Pal.c.destructive)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        is Block -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Pal.c.card,
            title = { Text(if (key.live) "Stop ${key.name}?" else "Delete ${key.name}?", color = Pal.c.foreground) },
            text = {
                Text(
                    if (key.live) "This activity is running in Simple Time Tracker. Stop it now?"
                    else "${hm(key.start)} – ${hm(key.end)}\n\n" +
                        "Simple Time Tracker does not let other apps delete its records. " +
                        "\"Remove here\" hides it in Progress Pal (timeline, stats and plans). " +
                        "To delete it for good, open Simple Time Tracker and delete it there.",
                    color = Pal.c.foreground, fontSize = 14.sp,
                )
            },
            confirmButton = {
                if (key.live) TextButton(onClick = {
                    cache.activities.firstOrNull { it.id == key.activityId }?.let { SimpleTimeTracker.toggle(it) }
                    onDismiss()
                }) { Text("Stop", color = Pal.c.destructive) }
                else TextButton(onClick = {
                    key.recordId?.let { SimpleTimeTracker.hideRecord(it) }
                    toast(ctx, "Removed from Progress Pal")
                    onDismiss()
                }) { Text("Remove here", color = Pal.c.destructive) }
            },
            dismissButton = {
                Row {
                    if (!key.live) TextButton(onClick = { SimpleTimeTracker.openApp(ctx); onDismiss() }) { Text("Open Simple Time Tracker") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            },
        )
        else -> {}
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
