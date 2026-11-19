package com.progresspal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progresspal.app.data.AppData
import com.progresspal.app.data.DayLevel
import com.progresspal.app.data.Planner
import com.progresspal.app.data.PointEvent
import com.progresspal.app.data.SimpleTimeTracker
import com.progresspal.app.data.byDay
import com.progresspal.app.data.productivityEvents
import com.progresspal.app.data.weekStart
import com.progresspal.app.ui.components.AppCard
import com.progresspal.app.ui.components.DatePickerModal
import com.progresspal.app.ui.theme.Pal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class LevelMode(val label: String) { HOUR("Hour"), DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year"), LIFE("Lifetime") }

private data class LBar(val label: String, val sub: String?, val value: Int, val current: Boolean, val date: LocalDate?)

// Bright, professional palette.
private val Violet = Color(0xFF7C4DFF)
private val Cyan = Color(0xFF00E5FF)
private val Gold = Color(0xFFFFC400)
private val Coral = Color(0xFFFF5A7A)
private val Mint = Color(0xFF00E676)
private val SalatGreen = Color(0xFF2BD48C)
private val TaskBlue = Color(0xFF3D8BFF)
private val PlanPink = Color(0xFFFF4FA3)

private val ModeBrush = mapOf(
    LevelMode.HOUR to listOf(Color(0xFF00E5FF), Color(0xFF2979FF)),
    LevelMode.DAY to listOf(Color(0xFFB388FF), Color(0xFF651FFF)),
    LevelMode.WEEK to listOf(Color(0xFF69F0AE), Color(0xFF00BFA5)),
    LevelMode.MONTH to listOf(Color(0xFFFFD740), Color(0xFFFF6D00)),
    LevelMode.YEAR to listOf(Color(0xFFFF80AB), Color(0xFFF50057)),
    LevelMode.LIFE to listOf(Color(0xFF84FFFF), Color(0xFF7C4DFF)),
)

/** Productivity Level card for Analytics: today, totals, a column chart by period, and any day's level. */
@Composable
fun ProductivityLevelSection(app: AppData) {
    val planner by Planner.data.collectAsStateWithLifecycle()
    val stt by SimpleTimeTracker.cache.collectAsStateWithLifecycle()
    val events = remember(app, planner, stt) { productivityEvents(app, planner, stt) }
    val daily = remember(events) { events.byDay() }
    val today = LocalDate.now()

    fun level(d: LocalDate) = daily[d]?.total ?: 0
    fun sum(a: LocalDate, b: LocalDate) = daily.entries.filter { !it.key.isBefore(a) && !it.key.isAfter(b) }.sumOf { it.value.total }

    val todayL = level(today)
    val yesterdayL = level(today.minusDays(1))
    val weekL = sum(today.weekStart(), today)
    val monthL = sum(today.withDayOfMonth(1), today)
    val yearL = sum(today.withDayOfYear(1), today)
    val lifeL = daily.values.sumOf { it.total }
    val best = daily.maxByOrNull { it.value.total }
    val activeDays = daily.count { it.value.total > 0 }
    val avg = if (activeDays == 0) 0 else (lifeL.toDouble() / activeDays).roundToInt()

    var modeName by rememberSaveable { mutableStateOf(LevelMode.DAY.name) }
    val mode = LevelMode.valueOf(modeName)
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var pickedEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val picked = LocalDate.ofEpochDay(pickedEpoch)
    var showPicker by remember { mutableStateOf(false) }

    AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        // ── Header + today ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Violet, Cyan))).padding(6.dp),
            ) { Icon(Icons.Filled.Bolt, null, tint = Color.White) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Productivity Level", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Pal.c.foreground)
                Text("Each salat = 10 · each task = 1 · each plan done = 1", fontSize = 11.sp, color = Pal.c.mutedForeground)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$todayL", color = Cyan, fontSize = 52.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.padding(bottom = 10.dp)) {
                Text("today", fontSize = 13.sp, color = Pal.c.mutedForeground)
                val diff = todayL - yesterdayL
                Text(
                    (if (diff >= 0) "▲ +$diff" else "▼ $diff") + " vs yesterday",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (diff >= 0) Mint else Coral,
                )
            }
        }
        Breakdown(daily[today] ?: DayLevel())

        // ── Totals ──
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("This week", "$weekL", Color(0xFF00BFA5), Modifier.weight(1f))
            Tile("This month", "$monthL", Color(0xFFFF6D00), Modifier.weight(1f))
            Tile("This year", "$yearL", Color(0xFFF50057), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Lifetime", "$lifeL", Violet, Modifier.weight(1f))
            Tile("Best day", best?.let { "${it.value.total}" } ?: "–", Gold, Modifier.weight(1f),
                sub = best?.key?.format(DateTimeFormatter.ofPattern("d MMM yy")))
            Tile("Daily avg", "$avg", Color(0xFF2979FF), Modifier.weight(1f))
        }

        // ── Chart ──
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LevelMode.entries.forEach { m ->
                val sel = m == mode
                Text(
                    m.label,
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) Brush.horizontalGradient(ModeBrush.getValue(m)) else Brush.horizontalGradient(listOf(Pal.c.muted, Pal.c.muted)))
                        .clickable { modeName = m.name; offset = 0 }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    color = if (sel) Color.White else Pal.c.foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        val window = buildWindow(mode, offset, picked, today, daily, events)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (window.canMove) IconButton(onClick = { offset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier", tint = Pal.c.foreground) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(window.title, color = Pal.c.foreground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                val total = window.bars.sumOf { it.value }
                val change = window.previousTotal?.let { prev ->
                    if (prev == 0) null else ((total - prev) * 100.0 / prev).roundToInt()
                }
                Text(
                    "Total ${if (mode == LevelMode.LIFE) lifeL else total}" +
                        (change?.let { "  ·  ${if (it >= 0) "▲" else "▼"} ${kotlin.math.abs(it)}% vs previous" } ?: ""),
                    fontSize = 12.sp, color = if ((change ?: 0) >= 0) Pal.c.mutedForeground else Coral,
                )
            }
            if (window.canMove) IconButton(onClick = { if (offset < 0) offset++ }, enabled = offset < 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later", tint = if (offset < 0) Pal.c.foreground else Pal.c.mutedForeground)
            }
        }
        ColumnChart(window.bars, ModeBrush.getValue(mode)) { b -> b.date?.let { pickedEpoch = it.toEpochDay() } }

        // ── Any day ──
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.c.muted)
                .clickable { showPicker = true }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CalendarMonth, null, tint = Gold)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(picked.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), color = Pal.c.foreground, fontWeight = FontWeight.SemiBold)
                Text("Tap to pick any day · or tap a bar", fontSize = 11.sp, color = Pal.c.mutedForeground)
            }
            Text("${level(picked)}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Gold)
        }
        Breakdown(daily[picked] ?: DayLevel())
    }

    if (showPicker) DatePickerModal(picked, { showPicker = false }) { d ->
        pickedEpoch = d.toEpochDay()
        showPicker = false
    }
    LaunchedEffect(Unit) { SimpleTimeTracker.syncSoon() }
}

@Composable
private fun Breakdown(l: DayLevel) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill2("🕌 ${l.salat} salat = ${l.salat * 10}", SalatGreen)
        Pill2("✓ ${l.tasks} task${if (l.tasks == 1) "" else "s"}", TaskBlue)
        if (l.plans > 0) Pill2("◆ ${l.plans} plan${if (l.plans == 1) "" else "s"}", PlanPink)
    }
}

@Composable
private fun Pill2(text: String, c: Color) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = .16f)).border(1.dp, c.copy(alpha = .5f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Tile(label: String, value: String, c: Color, modifier: Modifier, sub: String? = null) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(c.copy(alpha = .28f), c.copy(alpha = .08f))))
            .border(1.dp, c.copy(alpha = .45f), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Pal.c.foreground)
        Text(label, fontSize = 11.sp, color = Pal.c.mutedForeground)
        if (sub != null) Text(sub, fontSize = 10.sp, color = c)
    }
}

private data class Window(val title: String, val bars: List<LBar>, val previousTotal: Int?, val canMove: Boolean)

private fun buildWindow(
    mode: LevelMode, offset: Int, picked: LocalDate, today: LocalDate,
    daily: Map<LocalDate, DayLevel>, events: List<PointEvent>,
): Window {
    fun level(d: LocalDate) = daily[d]?.total ?: 0
    fun sum(a: LocalDate, b: LocalDate) = daily.entries.filter { !it.key.isBefore(a) && !it.key.isAfter(b) }.sumOf { it.value.total }
    val f = { p: String -> DateTimeFormatter.ofPattern(p) }
    return when (mode) {
        LevelMode.HOUR -> {
            val d = today.plusDays(offset.toLong())
            val perHour = IntArray(24).also { a -> events.filter { it.date == d }.forEach { a[it.hour] += it.points } }
            val nowH = java.time.LocalTime.now().hour
            Window(
                if (offset == 0) "Today by hour" else d.format(f("EEE, d MMM yyyy")),
                (0..23).map { h ->
                    LBar(if (h % 3 == 0) (if (h == 0) "12a" else if (h < 12) "${h}a" else if (h == 12) "12p" else "${h - 12}p") else "", null, perHour[h], offset == 0 && h == nowH, null)
                },
                level(d.minusDays(1)), true,
            )
        }
        LevelMode.DAY -> {
            val start = today.weekStart().plusWeeks(offset.toLong())
            val days = (0..6).map { start.plusDays(it.toLong()) }
            Window(
                "${start.format(f("d MMM"))} – ${start.plusDays(6).format(f("d MMM yyyy"))}",
                days.map { LBar(it.format(f("EEE")), it.dayOfMonth.toString(), level(it), it == today, it) },
                sum(start.minusDays(7), start.minusDays(1)), true,
            )
        }
        LevelMode.WEEK -> {
            val end = today.weekStart().plusWeeks(offset.toLong() * 12)
            val weeks = (11 downTo 0).map { end.minusWeeks(it.toLong()) }
            Window(
                "12 weeks to ${end.plusDays(6).format(f("d MMM yyyy"))}",
                weeks.map { w -> LBar(w.format(f("d MMM")), null, sum(w, w.plusDays(6)), w == today.weekStart(), null) },
                sum(weeks.first().minusWeeks(12), weeks.first().minusDays(1)), true,
            )
        }
        LevelMode.MONTH -> {
            val year = today.year + offset
            val months = (1..12).map { LocalDate.of(year, it, 1) }
            Window(
                "$year",
                months.map { m -> LBar(m.format(f("MMM")), null, sum(m, m.withDayOfMonth(m.lengthOfMonth())), m.year == today.year && m.month == today.month, null) },
                sum(LocalDate.of(year - 1, 1, 1), LocalDate.of(year - 1, 12, 31)), true,
            )
        }
        LevelMode.YEAR -> {
            val first = (daily.keys.minOrNull() ?: today).year
            val years = (minOf(first, today.year - 4)..today.year).toList()
            Window(
                "By year",
                years.map { y -> LBar("$y", null, sum(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)), y == today.year, null) },
                null, false,
            )
        }
        LevelMode.LIFE -> {
            // Running total, month by month, since the first point.
            val first = (daily.keys.minOrNull() ?: today).withDayOfMonth(1)
            val months = generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(today) }.toList().takeLast(24)
            var running = sum(LocalDate.MIN, months.first().minusDays(1))
            Window(
                "Lifetime growth",
                months.map { m ->
                    running += sum(m, m.withDayOfMonth(m.lengthOfMonth()))
                    LBar(m.format(f("MMM")), m.format(f("yy")), running, m.year == today.year && m.month == today.month, null)
                },
                null, false,
            )
        }
    }
}

/** Bright gradient columns with the value on top; scrolls sideways when there are many. */
@Composable
private fun ColumnChart(bars: List<LBar>, colors: List<Color>, onTap: (LBar) -> Unit) {
    val max = (bars.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val wide = bars.size > 12
    Row(
        Modifier.fillMaxWidth().height(210.dp).then(if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 4.dp else 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { b ->
            val frac = b.value.toFloat() / max
            Column(
                (if (wide) Modifier.width(26.dp) else Modifier.weight(1f)).fillMaxHeight().clickable { onTap(b) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (b.value > 0) Text("${b.value}", fontSize = 10.sp, color = Pal.c.foreground, fontWeight = FontWeight.Bold, maxLines = 1)
                Box(
                    Modifier.fillMaxWidth()
                        .height((140 * frac).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (b.current) Brush.verticalGradient(listOf(Gold, Color(0xFFFF6D00)))
                            else Brush.verticalGradient(colors)
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(b.label, fontSize = 10.sp, color = if (b.current) Gold else Pal.c.mutedForeground, maxLines = 1, textAlign = TextAlign.Center)
                Text(b.sub ?: " ", fontSize = 9.sp, color = Pal.c.mutedForeground, maxLines = 1)
            }
        }
    }
}

/** Planning Score: how much of the planned time was spent on the planned activity, in %. */
@Composable
fun PlanningScoreSection() {
    val planner by Planner.data.collectAsStateWithLifecycle()
    val stt by SimpleTimeTracker.cache.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    data class Row7(val day: LocalDate, val s: com.progresspal.app.data.PlanScore)
    val todayS = remember(planner, stt) { com.progresspal.app.data.planScoreOn(planner, stt, today, now) }
    val yest = remember(planner, stt) { com.progresspal.app.data.planScoreOn(planner, stt, today.minusDays(1), now) }
    val week = remember(planner, stt) { com.progresspal.app.data.planScoreBetween(planner, stt, today.minusDays(6), today, now) }
    val month = remember(planner, stt) { com.progresspal.app.data.planScoreBetween(planner, stt, today.minusDays(29), today, now) }
    val life = remember(planner, stt) {
        com.progresspal.app.data.firstPlanDay(planner)?.let { com.progresspal.app.data.planScoreBetween(planner, stt, minOf(it, today), today, now) }
            ?: com.progresspal.app.data.PlanScore()
    }
    val last14 = remember(planner, stt) { (13 downTo 0).map { today.minusDays(it.toLong()) }.map { Row7(it, com.progresspal.app.data.planScoreOn(planner, stt, it, now)) } }
    fun pct(s: com.progresspal.app.data.PlanScore) = s.percent?.let { "$it%" } ?: "–"
    fun hm(ms: Long): String { val m = ms / 60_000; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
    val accent = Color(0xFFFF4FA3)

    AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF4FA3), Color(0xFFFFC400)))).padding(horizontal = 9.dp, vertical = 5.dp)) {
                Text("🎯", fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Planning Score", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Pal.c.foreground)
                Text("Share of planned time spent on the planned activity, at the planned time", fontSize = 11.sp, color = Pal.c.mutedForeground)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(pct(todayS), color = accent, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("today", fontSize = 13.sp, color = Pal.c.mutedForeground)
                Text(
                    if (todayS.plannedMs > 0) "${hm(todayS.matchedMs)} of ${hm(todayS.plannedMs)} planned so far" else "No plan has started yet today",
                    fontSize = 12.sp, color = Pal.c.mutedForeground,
                )
            }
        }
        // Score bar for today.
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Pal.c.muted)) {
            Box(
                Modifier.fillMaxWidth(((todayS.percent ?: 0) / 100f)).height(10.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFF4FA3), Color(0xFFFFC400))))
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Yesterday", pct(yest), Color(0xFF2979FF), Modifier.weight(1f))
            Tile("7 days", pct(week), Color(0xFF00BFA5), Modifier.weight(1f))
            Tile("30 days", pct(month), Color(0xFFFF6D00), Modifier.weight(1f))
            Tile("Lifetime", pct(life), Violet, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text("Last 14 days", fontSize = 12.sp, color = Pal.c.mutedForeground, fontWeight = FontWeight.SemiBold)
        ColumnChart(
            last14.map { r -> LBar(r.day.format(DateTimeFormatter.ofPattern("EEE")).take(2), r.day.dayOfMonth.toString(), r.s.percent ?: 0, r.day == today, r.day) },
            listOf(Color(0xFFFFC400), Color(0xFFFF4FA3)),
        ) {}
    }
}
