package com.progresspal.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progresspal.app.data.SimpleTimeTracker
import com.progresspal.app.data.Zikir
import com.progresspal.app.data.ZikirData
import com.progresspal.app.data.Zikr
import com.progresspal.app.ui.components.Sounds
import com.progresspal.app.ui.theme.Pal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

private val ZGreen = Color(0xFF10B981)
private val ZGold = Color(0xFFF5C542)
private val ZDeep = Color(0xFF064E3B)

/** Zikir tracker: tasbih counter per zikir with hourly…yearly bar charts. */
@Composable
fun ZikirScreen(onBack: () -> Unit) {
    val d by Zikir.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ZikirStt()

    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.c.foreground) }
            Column(Modifier.weight(1f)) {
                Text("Zikir Tracker", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Pal.c.foreground)
                Text("Today ${"%,d".format(Zikir.todayFor(d, null))} · Lifetime ${"%,d".format(d.hourly.values.sum())}", fontSize = 12.sp, color = Pal.c.mutedForeground)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tasbih", "Progress").forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (tab == i) ZGreen else Pal.c.muted)
                        .clickable { tab = i }.padding(vertical = 8.dp),
                    color = if (tab == i) Color.White else Pal.c.foreground, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) Counter(d) else ZikirProgress(d)
    }
}

/** Runs the Zikir activity in Simple Time Tracker from the first bead until leaving the screen. */
@Composable
private fun ZikirStt() {
    val act = remember {
        val acts = SimpleTimeTracker.cache.value.activities
        listOf("zikir", "zikr", "dhikr", "zikar", "jikir", "tasbih", "tasbeeh")
            .firstNotNullOfOrNull { w -> acts.firstOrNull { it.name.contains(w, ignoreCase = true) } }
    }
    val startedByUs = remember { mutableStateOf(false) }
    val d by Zikir.data.collectAsStateWithLifecycle()
    val first = remember { d.hourly.values.sum() }
    val total = d.hourly.values.sum()
    androidx.compose.runtime.LaunchedEffect(total) {
        if (act != null && !startedByUs.value && total > first && !SimpleTimeTracker.isRunning(act.id)) {
            SimpleTimeTracker.start(act, "Tasbih"); startedByUs.value = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (startedByUs.value && act != null && SimpleTimeTracker.isRunning(act.id)) SimpleTimeTracker.toggle(act) }
    }
}

// ───────────────────────── Counter ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Counter(d: ZikirData) {
    val haptic = LocalHapticFeedback.current
    val z = d.zikrs.firstOrNull { it.id == d.selectedId } ?: d.zikrs.firstOrNull()
    var editing by remember { mutableStateOf<Zikr?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Zikir chips: tap to choose, long-press to edit
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            d.zikrs.sortedBy { it.order }.forEach { x ->
                val sel = x.id == z?.id
                Text(
                    x.name, Modifier.clip(RoundedCornerShape(50)).background(if (sel) ZGreen else Pal.c.muted)
                        .combinedClickable(onClick = { Zikir.select(x.id) }, onLongClick = { editing = x })
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    color = if (sel) Color.White else Pal.c.foreground, fontSize = 13.sp, maxLines = 1,
                )
            }
            Text("＋ Add", Modifier.clip(RoundedCornerShape(50)).border(1.dp, ZGreen, RoundedCornerShape(50)).clickable { adding = true }.padding(horizontal = 12.dp, vertical = 7.dp), color = ZGreen, fontSize = 13.sp)
        }

        if (z == null) { Text("Add a zikir to start counting.", color = Pal.c.mutedForeground); return@Column }
        val round = d.round[z.id] ?: 0
        val rounds = d.rounds[z.id] ?: 0
        val today = Zikir.todayFor(d, z.id)
        val frac = if (z.target > 0) round / z.target.toFloat() else 0f

        // The tasbih: tap anywhere on the circle
        Box(
            Modifier.fillMaxWidth(0.86f).aspectRatio(1f).align(Alignment.CenterHorizontally).clip(CircleShape)
                .background(Brush.radialGradient(listOf(ZGreen.copy(alpha = .35f), ZDeep.copy(alpha = .55f))))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = androidx.compose.material3.ripple()) {
                    val done = Zikir.tap(z.id)
                    if (d.vibrate) haptic.performHapticFeedback(if (done) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                    if (done) Sounds.done()
                },
            contentAlignment = Alignment.Center,
        ) {
            val track = Pal.c.muted
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val st = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, style = st)
                drawArc(ZGold, -90f, 360f * frac, false, style = st)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (z.arabic.isNotBlank()) Text(z.arabic, fontSize = 22.sp, color = Color.White, textAlign = TextAlign.Center)
                Text(z.name, fontSize = 14.sp, color = Color.White.copy(alpha = .85f), textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 28.dp))
                Text("$round", fontSize = 72.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("of ${z.target} · round ${rounds + 1}", fontSize = 13.sp, color = Color.White.copy(alpha = .8f))
                Text("Tap to count", fontSize = 11.sp, color = Color.White.copy(alpha = .55f), modifier = Modifier.padding(top = 6.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { Zikir.undo(z.id) }) { Text("↶ Undo") }
            TextButton(onClick = { Zikir.resetRound(z.id) }) { Text("⟲ Reset round") }
            TextButton(onClick = { editing = z }) { Text("✎ Edit") }
        }

        // Today for this zikir
        ZCard {
            Row {
                Text("Today · ${z.name}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Pal.c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (z.dailyGoal > 0) "$today / ${z.dailyGoal}" else "$today", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
            }
            if (z.dailyGoal > 0) { Spacer(Modifier.height(6.dp)); BrightBar(today / z.dailyGoal.toFloat(), listOf(ZGreen, ZGold), 12) }
            Text("$rounds rounds completed in total", fontSize = 11.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(top = 4.dp))
        }

        // All zikir today
        ZCard {
            Text("Today's Zikir", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
            d.zikrs.sortedBy { it.order }.forEach { x ->
                val t = Zikir.todayFor(d, x.id)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { Zikir.select(x.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(x.name, Modifier.weight(1f), fontSize = 13.sp, color = Pal.c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (x.dailyGoal > 0) "$t / ${x.dailyGoal}" else "$t", fontSize = 12.sp, color = if (x.dailyGoal in 1..t) ZGreen else Pal.c.mutedForeground)
                }
                if (x.dailyGoal > 0) BrightBar(t / x.dailyGoal.toFloat(), listOf(ZGreen, ZGold), 6)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vibrate on each bead", Modifier.weight(1f), color = Pal.c.foreground, fontSize = 13.sp)
            Switch(checked = d.vibrate, onCheckedChange = { Zikir.setVibrate(it) })
        }
        Spacer(Modifier.height(24.dp))
    }

    if (adding) ZikrDialog(null, onDone = { adding = false })
    editing?.let { e -> ZikrDialog(e, onDone = { editing = null }) }
}

@Composable
private fun ZikrDialog(z: Zikr?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(z?.name ?: "") }
    var arabic by remember { mutableStateOf(z?.arabic ?: "") }
    var target by remember { mutableStateOf((z?.target ?: 33).toString()) }
    var goal by remember { mutableStateOf((z?.dailyGoal ?: 100).toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone, containerColor = Pal.c.card,
        title = { Text(if (z == null) "Add Zikir" else "Edit Zikir", color = Pal.c.foreground) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(arabic, { arabic = it }, label = { Text("Arabic (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Count per round (e.g. 33, 100)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(goal, { goal = it.filter(Char::isDigit) }, label = { Text("Daily goal (0 = none)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (z != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete this zikir", color = Pal.c.destructive) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val t = target.toIntOrNull()?.coerceAtLeast(1) ?: 33
                    val g = goal.toIntOrNull() ?: 0
                    Zikir.upsert((z ?: Zikr(name = name.trim())).copy(name = name.trim(), arabic = arabic.trim(), target = t, dailyGoal = g))
                }
                onDone()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
    if (confirmDelete && z != null) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = Pal.c.card,
        title = { Text("Delete ${z.name}?", color = Pal.c.foreground) },
        text = { Text("It is removed from the list; counts already made stay in your totals.", color = Pal.c.foreground) },
        confirmButton = { TextButton(onClick = { Zikir.delete(z.id); confirmDelete = false; onDone() }) { Text("Delete", color = Pal.c.destructive) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

// ───────────────────────── Progress ─────────────────────────

private val PERIODS = listOf("Hourly", "Daily", "Weekly", "Monthly", "Quarterly", "Yearly")

@Composable
private fun ZikirProgress(d: ZikirData) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableIntStateOf(1) }
    val perHour = remember(d, filter) { Zikir.perHour(d, filter) }
    val byDay = remember(perHour) { perHour.entries.groupBy({ it.key.toLocalDate() }, { it.value }).mapValues { it.value.sum() } }
    val now = LocalDateTime.now()
    val today = now.toLocalDate()

    val bars: List<Pair<String, Int>> = remember(perHour, period) {
        when (period) {
            0 -> (0..23).map { h -> "%02d".format(h) to (perHour[today.atTime(h, 0)] ?: 0) }
            1 -> (29 downTo 0).map { today.minusDays(it.toLong()) }.map { it.format(DateTimeFormatter.ofPattern("d MMM")) to (byDay[it] ?: 0) }
            2 -> (11 downTo 0).map { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).minusWeeks(it.toLong()) }.map { start ->
                start.format(DateTimeFormatter.ofPattern("d MMM")) to (0..6).sumOf { byDay[start.plusDays(it.toLong())] ?: 0 }
            }
            3 -> (11 downTo 0).map { today.withDayOfMonth(1).minusMonths(it.toLong()) }.map { m ->
                m.format(DateTimeFormatter.ofPattern("MMM yy")) to byDay.filterKeys { it.year == m.year && it.month == m.month }.values.sum()
            }
            4 -> (7 downTo 0).map { q ->
                val start = today.withDayOfMonth(1).withMonth(((today.monthValue - 1) / 3) * 3 + 1).minusMonths(3L * q)
                val label = "Q${start.get(IsoFields.QUARTER_OF_YEAR)} ${start.year % 100}"
                label to byDay.filterKeys { !it.isBefore(start) && it.isBefore(start.plusMonths(3)) }.values.sum()
            }
            else -> {
                val first = byDay.keys.minOfOrNull { it.year } ?: today.year
                (minOf(first, today.year - 4)..today.year).map { y -> "$y" to byDay.filterKeys { it.year == y }.values.sum() }
            }
        }
    }

    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
    fun sumFrom(from: LocalDate) = byDay.filterKeys { !it.isBefore(from) }.values.sum()
    val streak = run { var n = 0; var x = if ((byDay[today] ?: 0) > 0) today else today.minusDays(1); while ((byDay[x] ?: 0) > 0) { n++; x = x.minusDays(1) }; n }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf<Zikr?>(null) + d.zikrs.sortedBy { it.order }).forEach { x ->
                val sel = filter == x?.id
                Text(
                    x?.name ?: "All Zikir", Modifier.clip(RoundedCornerShape(50)).background(if (sel) ZGreen else Pal.c.muted)
                        .clickable { filter = x?.id }.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = if (sel) Color.White else Pal.c.foreground, fontSize = 13.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZStat("Today", byDay[today] ?: 0, Modifier.weight(1f))
            ZStat("This week", sumFrom(weekStart), Modifier.weight(1f))
            ZStat("This month", sumFrom(today.withDayOfMonth(1)), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZStat("This year", sumFrom(today.withDayOfYear(1)), Modifier.weight(1f))
            ZStat("Lifetime", byDay.values.sum(), Modifier.weight(1f))
            ZStat("Day streak", streak, Modifier.weight(1f))
        }

        ZCard {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PERIODS.forEachIndexed { i, l ->
                    Text(l, Modifier.clip(RoundedCornerShape(8.dp)).background(if (period == i) ZGreen else Pal.c.muted).clickable { period = i }.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (period == i) Color.White else Pal.c.foreground, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            val total = bars.sumOf { it.second }
            val active = bars.count { it.second > 0 }
            val best = bars.maxByOrNull { it.second }
            Text(
                when (period) { 0 -> "Today by hour"; 1 -> "Last 30 days"; 2 -> "Last 12 weeks"; 3 -> "Last 12 months"; 4 -> "Last 8 quarters"; else -> "By year" } +
                    " · total ${"%,d".format(total)}" + (if (active > 0) " · avg ${"%,d".format(total / active)}" else "") +
                    (if (best != null && best.second > 0) " · best ${best.first} (${"%,d".format(best.second)})" else ""),
                fontSize = 12.sp, color = Pal.c.mutedForeground,
            )
            Spacer(Modifier.height(8.dp))
            ZBars(bars, highlightLast = period != 0, highlightLabel = if (period == 0) "%02d".format(now.hour) else null)
        }

        if (filter == null) ZCard {
            Text("By zikir · lifetime", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
            val totals = d.zikrs.map { it to Zikir.perHour(d, it.id).values.sum() }.sortedByDescending { it.second }
            val max = totals.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            totals.forEach { (x, t) ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(x.name, Modifier.weight(1f), fontSize = 13.sp, color = Pal.c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("%,d".format(t), fontSize = 12.sp, color = Pal.c.mutedForeground)
                }
                BrightBar(t / max.toFloat(), listOf(ZGreen, ZGold), 6)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ZStat(label: String, v: Int, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(ZGreen.copy(alpha = .16f), Pal.c.card)))
            .border(1.dp, ZGreen.copy(alpha = .3f), RoundedCornerShape(12.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("%,d".format(v), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Pal.c.foreground, maxLines = 1)
        Text(label, fontSize = 10.sp, color = Pal.c.mutedForeground)
    }
}

@Composable
private fun ZCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(14.dp)).padding(14.dp)) { content() }
}

/** Vertical bar chart with value labels; scrolls sideways when long. */
@Composable
private fun ZBars(bars: List<Pair<String, Int>>, highlightLast: Boolean, highlightLabel: String?) {
    if (bars.all { it.second == 0 }) {
        Text("No zikir counted in this period yet.", Modifier.fillMaxWidth().padding(vertical = 30.dp), color = Pal.c.mutedForeground, textAlign = TextAlign.Center)
        return
    }
    val max = bars.maxOf { it.second }.coerceAtLeast(1).toFloat()
    val track = Pal.c.muted
    val scroll = rememberScrollState(Int.MAX_VALUE)
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEachIndexed { i, (label, v) ->
            val hot = (highlightLast && i == bars.lastIndex) || label == highlightLabel
            Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v == 0) "" else if (v >= 10_000) "${v / 1000}k" else "$v", fontSize = 9.sp, color = Pal.c.foreground, maxLines = 1)
                Canvas(Modifier.width(20.dp).height(150.dp)) {
                    drawRoundRect(track, size = size, cornerRadius = CornerRadius(6f))
                    val h = size.height * (v / max)
                    drawRoundRect(if (hot) ZGold else ZGreen, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = CornerRadius(6f))
                }
                Text(label, fontSize = 9.sp, color = if (hot) ZGold else Pal.c.mutedForeground, maxLines = 1)
            }
        }
    }
}
