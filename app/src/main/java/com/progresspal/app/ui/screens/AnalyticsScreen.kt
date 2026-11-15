package com.progresspal.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.data.AppData
import com.progresspal.app.data.CompletionRecord
import com.progresspal.app.data.format
import com.progresspal.app.data.today
import com.progresspal.app.data.toLocalDateTime
import com.progresspal.app.data.weekStart
import com.progresspal.app.ui.components.AppCard
import com.progresspal.app.ui.components.ScreenHeader
import com.progresspal.app.ui.components.SectionLabel
import com.progresspal.app.ui.theme.Brand
import com.progresspal.app.ui.theme.MonoStyle
import com.progresspal.app.ui.theme.NumberStyle
import com.progresspal.app.ui.theme.Pal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.round

private val DAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

private enum class ChartTab(val label: String) { HOUR("Hour"), DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

private data class Bar(val label: String, val sub: String?, val count: Int, val highlight: Boolean)

/** 0 = Sunday … 6 = Saturday */
private fun LocalDate.sundayIndex() = dayOfWeek.value % 7

@Composable
fun AnalyticsScreen(data: AppData, onBack: () -> Unit) {
    val recs: List<CompletionRecord> = data.completions.filter { it.completed }
    val today = today()
    val byDate: Map<String, Int> = recs.groupingBy { it.date }.eachCount()
    fun countOn(d: LocalDate) = byDate[d.toString()] ?: 0
    fun countBetween(a: LocalDate, b: LocalDate): Int {
        val s = a.toString(); val e = b.toString()
        return recs.count { it.date in s..e }
    }

    val todayCount = countOn(today)
    val weekCount = countBetween(today.weekStart(), today)
    val monthCount = countBetween(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
    val yearCount = countBetween(today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()))

    val dayCounts = IntArray(7).also { arr -> recs.forEach { arr[LocalDate.parse(it.date).sundayIndex()]++ } }
    val maxDayIdx = dayCounts.indices.maxByOrNull { dayCounts[it] } ?: 0

    var currentStreak = 0
    run {
        var d = today
        while (countOn(d) > 0 && currentStreak < 3650) { currentStreak++; d = d.minusDays(1) }
    }
    val streakDays = (currentStreak - 1 downTo 0).map { today.minusDays(it.toLong()) }

    val dates = byDate.keys.map { LocalDate.parse(it) }.sorted()
    var bestLen = 0; var bestStart: LocalDate? = null; var bestEnd: LocalDate? = null
    var len = 0
    var start: LocalDate? = null
    for (i in dates.indices) {
        val d = dates[i]
        if (i > 0 && ChronoUnit.DAYS.between(dates[i - 1], d) == 1L) len++ else { len = 1; start = d }
        if (len > bestLen) { bestLen = len; bestStart = start; bestEnd = d }
    }
    val bestDay = byDate.maxByOrNull { it.value }
    val avgPerDay = if (byDate.isEmpty()) 0.0 else round(recs.size.toDouble() / byDate.size * 10) / 10

    val monthly = (5 downTo 0).map { m ->
        val d = today.minusMonths(m.toLong())
        d to countBetween(d.withDayOfMonth(1), d.withDayOfMonth(d.lengthOfMonth()))
    }
    val trendUp = monthly.size < 2 || monthly.last().second >= monthly[monthly.size - 2].second

    val hourOf = { r: CompletionRecord -> r.createdAt.toLocalDateTime().hour }
    val timeBuckets = linkedMapOf("Morning" to 0, "Afternoon" to 0, "Evening" to 0, "Night" to 0).also { b ->
        recs.forEach {
            val h = hourOf(it)
            val k = when (h) { in 5..11 -> "Morning"; in 12..16 -> "Afternoon"; in 17..20 -> "Evening"; else -> "Night" }
            b[k] = b.getValue(k) + 1
        }
    }

    var tab by rememberSaveable { mutableStateOf(ChartTab.DAY) }
    val bars: List<Bar> = when (tab) {
        ChartTab.HOUR -> {
            val counts = IntArray(24).also { a -> recs.filter { it.date == today.toString() }.forEach { a[hourOf(it)]++ } }
            val nowHour = java.time.LocalTime.now().hour
            (0..23).map { h ->
                val l = when { h == 0 -> "12a"; h < 12 -> "${h}a"; h == 12 -> "12p"; else -> "${h - 12}p" }
                Bar(l, null, counts[h], h == nowHour)
            }
        }
        ChartTab.DAY -> (89 downTo 0).map { i -> today.minusDays(i.toLong()).let { Bar(it.dayOfMonth.toString(), it.format("MMM"), countOn(it), i == 0) } }
        ChartTab.WEEK -> (51 downTo 0).map { w ->
            val end = today.minusWeeks(w.toLong()); val start = end.minusDays(6)
            Bar(start.format("MMM d"), null, countBetween(start, end), w == 0)
        }
        ChartTab.MONTH -> (23 downTo 0).map { m ->
            val d = today.minusMonths(m.toLong())
            Bar(d.format("MMM"), d.format("yy"), countBetween(d.withDayOfMonth(1), d.withDayOfMonth(d.lengthOfMonth())), m == 0)
        }
        ChartTab.YEAR -> (9 downTo 0).map { y ->
            val yr = today.year - y
            Bar("$yr", null, recs.count { it.date.startsWith("$yr-") }, y == 0)
        }
    }
    val tabColor = when (tab) {
        ChartTab.HOUR -> Brand.green; ChartTab.DAY -> Brand.blue; ChartTab.WEEK -> Brand.purple
        ChartTab.MONTH -> Brand.amber; ChartTab.YEAR -> Brand.coral
    }

    Screen {
        ScreenHeader("Analytics", "Your productivity insights", Icons.Filled.BarChart, Pal.c.primary, onBack)

        // ── Today: day progress + task progress ──
        run {
            val todays = com.progresspal.app.data.dailyTasksFor(data, today)
            AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), corner = 18.dp) {
                SectionLabel("Today · ${today.format("EEE, MMM d")}", Icons.Filled.Schedule, Pal.c.primary)
                com.progresspal.app.ui.components.DayAndTaskBars(today, todays.count { it.completed }, todays.size)
            }
        }

        SalatAnalytics(data)

        // ── Bar chart with tabs ──
        AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), corner = 18.dp, padding = 12.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ChartTab.entries.forEach { t ->
                    val sel = t == tab
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) Pal.c.primary.copy(alpha = .15f) else Color.Transparent)
                            .clickable { tab = t }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(t.label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = if (sel) Pal.c.primary else Pal.c.mutedForeground)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tasks Done", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Pal.c.mutedForeground, modifier = Modifier.weight(1f))
                val tc = if (trendUp) Pal.c.primary else Brand.coral
                Icon(if (trendUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, tint = tc, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (trendUp) "Trending up" else "Trending down", fontSize = 10.sp, color = tc, style = MonoStyle)
            }
            BarChart(bars, tabColor, tab.name)
        }

        // ── Hero stats ──
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            HeroStat(Icons.Filled.LocalFireDepartment, Brand.coral, "$currentStreak", "Current Streak", null, Modifier.weight(1f))
            HeroStat(
                Icons.Filled.WorkspacePremium, Brand.purple, "$bestLen", "Best Streak",
                if (bestStart != null && bestEnd != null) "${bestStart!!.format("MMM d")} – ${bestEnd!!.format("MMM d")}" +
                    (if (currentStreak >= bestLen && currentStreak > 0) "\n🔥 Record!" else "") else null,
                Modifier.weight(1f),
            )
        }
        if (streakDays.isNotEmpty()) {
            AppCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), borderColor = Brand.coral.copy(alpha = .15f), padding = 12.dp) {
                SectionLabel("Streak Days", Icons.Filled.LocalFireDepartment, Brand.coral)
                val mx = streakDays.maxOf { countOn(it) }.coerceAtLeast(1)
                streakDays.takeLast(14).forEach { d ->
                    val isT = d == today
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(d.format("MMM d"), fontSize = 10.sp, style = MonoStyle, color = if (isT) Brand.coral else Pal.c.mutedForeground, modifier = Modifier.width(56.dp))
                        HBar(max(.08f, countOn(d).toFloat() / mx), Brush.horizontalGradient(listOf(Brand.coral.copy(alpha = if (isT) 1f else .6f), Brand.amber.copy(alpha = if (isT) 1f else .4f))), Modifier.weight(1f), 10.dp)
                        Text("${countOn(d)}", fontSize = 10.sp, style = MonoStyle, color = Pal.c.mutedForeground, modifier = Modifier.width(24.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            HeroStat(Icons.Filled.Star, Brand.amber, "${bestDay?.value ?: 0} tasks", "Best Day", bestDay?.let { LocalDate.parse(it.key).format("MMM d, yyyy") }, Modifier.weight(1f))
            HeroStat(Icons.Filled.MonitorHeart, Brand.blue, "$avgPerDay", "Avg/Day", null, Modifier.weight(1f))
        }

        // ── Period cards ──
        val periods = listOf(
            Triple(Icons.Filled.Bolt, Brand.amber, "Today" to todayCount),
            Triple(Icons.Filled.CalendarToday, Brand.blue, "This Week" to weekCount),
            Triple(Icons.AutoMirrored.Filled.TrendingUp, Brand.purple, "This Month" to monthCount),
            Triple(Icons.Filled.GpsFixed, Pal.c.primary, "This Year" to yearCount),
        )
        periods.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                row.forEach { (icon, color, lv) ->
                    AppCard(Modifier.weight(1f), borderColor = color.copy(alpha = .2f), background = color.copy(alpha = .08f), corner = 18.dp, padding = 14.dp) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                        Text("${lv.second}", style = NumberStyle, fontSize = 24.sp, color = color, modifier = Modifier.padding(top = 4.dp))
                        Text(lv.first.uppercase(), fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium, color = Pal.c.mutedForeground)
                    }
                }
            }
        }

        // ── Most productive ──
        AppCard(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp), borderColor = Brand.purple.copy(alpha = .15f), background = Brand.purple.copy(alpha = .08f), corner = 18.dp, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.progresspal.app.ui.components.IconTile(Icons.Filled.WorkspacePremium, Brand.purple)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("MOST PRODUCTIVE", fontSize = 10.sp, letterSpacing = 1.sp, color = Pal.c.mutedForeground)
                    Text(DAY_NAMES[maxDayIdx], fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Pal.c.foreground)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${dayCounts[maxDayIdx]}", style = NumberStyle, fontSize = 20.sp, color = Brand.purple)
                    Text("tasks", fontSize = 9.sp, color = Pal.c.mutedForeground)
                }
            }
        }

        // ── Donuts ──
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            val dayData = DAY_NAMES.indices.map { DAY_NAMES[it].take(3) to dayCounts[it] }.filter { it.second > 0 }
            DonutCard("By Day", Icons.Filled.PieChart, Brand.blue, dayData, 0, Modifier.weight(1f))
            DonutCard("By Time", Icons.Filled.Schedule, Brand.amber, timeBuckets.toList().filter { it.second > 0 }, 3, Modifier.weight(1f))
        }

        // ── Heatmap ──
        AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), corner = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionLabel("Activity Heatmap", Icons.Filled.MonitorHeart, Pal.c.primary) }
                Text("Last 4 weeks", fontSize = 10.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(bottom = 8.dp))
            }
            val weeks = (3 downTo 0).map { w -> (0..6).map { d -> today.minusDays((w * 7 + (6 - d)).toLong()) } }
            val maxHeat = weeks.flatten().maxOf { countOn(it) }.coerceAtLeast(1)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                weeks.first().forEach { d -> Text(d.format("EEE"), fontSize = 8.sp, color = Pal.c.mutedForeground, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
            }
            weeks.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    week.forEach { d -> Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(heatColor(countOn(d), maxHeat))) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Less", fontSize = 8.sp, color = Pal.c.mutedForeground)
                listOf(0, 1, 2, 3, 4).forEach { lvl ->
                    Spacer(Modifier.width(3.dp))
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(heatColor(lvl, 4)))
                }
                Spacer(Modifier.width(4.dp))
                Text("More", fontSize = 8.sp, color = Pal.c.mutedForeground)
            }
        }

        // ── By day of week ──
        AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), corner = 18.dp) {
            SectionLabel("By Day of Week", Icons.Filled.CheckCircle, Brand.green)
            val mx = dayCounts.max().coerceAtLeast(1)
            DAY_NAMES.forEachIndexed { i, name ->
                val isMax = dayCounts[i] == dayCounts.max() && dayCounts[i] > 0
                val c = Brand.chartColors[i % Brand.chartColors.size]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(name.take(3), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isMax) Pal.c.foreground else Pal.c.mutedForeground, modifier = Modifier.width(40.dp))
                    HBar(
                        dayCounts[i].toFloat() / mx,
                        if (isMax) Brush.horizontalGradient(listOf(c, Brand.chartColors[(i + 1) % Brand.chartColors.size])) else Brush.horizontalGradient(listOf(c.copy(alpha = .4f), c.copy(alpha = .4f))),
                        Modifier.weight(1f), 12.dp,
                    )
                    Text("${dayCounts[i]}", fontSize = 12.sp, style = MonoStyle, color = if (isMax) Pal.c.foreground else Pal.c.mutedForeground, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                }
            }
        }

        // ── 6-month trend ──
        AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), corner = 18.dp) {
            SectionLabel("6-Month Trend", Icons.AutoMirrored.Filled.TrendingUp, Pal.c.primary)
            val mx = monthly.maxOf { it.second }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                monthly.forEachIndexed { i, (d, v) ->
                    val c = Brand.chartColors[i % Brand.chartColors.size]
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text("$v", fontSize = 9.sp, style = MonoStyle, color = Pal.c.mutedForeground)
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(max(.04f, v.toFloat() / mx) * .7f)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(Brush.verticalGradient(listOf(c, c.copy(alpha = .55f))))
                        )
                        Text(d.format("MMM"), fontSize = 8.sp, color = Pal.c.mutedForeground)
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TOTAL COMPLETIONS", fontSize = 10.sp, letterSpacing = 1.sp, color = Pal.c.mutedForeground)
            Text("${recs.size}", style = NumberStyle, fontSize = 30.sp, color = Pal.c.foreground)
        }
    }
}

@Composable
private fun heatColor(count: Int, max: Int): Color {
    if (count == 0) return Pal.c.muted
    val i = count.toFloat() / max
    return when {
        i <= .25f -> Brand.green.copy(alpha = .2f)
        i <= .5f -> Brand.green.copy(alpha = .4f)
        i <= .75f -> Brand.blue.copy(alpha = .6f)
        else -> Brand.purple.copy(alpha = .9f)
    }
}

@Composable
private fun HBar(fraction: Float, brush: Brush, modifier: Modifier, height: Dp) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "hbar")
    Box(modifier.padding(horizontal = 6.dp).height(height).clip(CircleShape).background(Pal.c.muted.copy(alpha = .6f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(CircleShape).background(brush))
    }
}

@Composable
private fun HeroStat(icon: ImageVector, color: Color, value: String, label: String, sub: String?, modifier: Modifier) {
    AppCard(modifier, borderColor = color.copy(alpha = .2f), background = color.copy(alpha = .1f), corner = 18.dp, padding = 12.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, style = NumberStyle, fontSize = 22.sp, color = color, modifier = Modifier.padding(top = 4.dp))
            Text(label.uppercase(), fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium, color = Pal.c.mutedForeground)
            if (sub != null) Text(sub, fontSize = 10.sp, color = color, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun DonutCard(title: String, icon: ImageVector, tint: Color, slices: List<Pair<String, Int>>, colorOffset: Int, modifier: Modifier) {
    AppCard(modifier, corner = 18.dp, padding = 12.dp) {
        SectionLabel(title, icon, tint)
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            if (slices.isEmpty()) {
                Text("No data", fontSize = 12.sp, color = Pal.c.mutedForeground)
            } else {
                val total = slices.sumOf { it.second }.toFloat()
                val colors = slices.indices.map { Brand.chartColors[(it + colorOffset) % Brand.chartColors.size] }
                val sweep by animateFloatAsState(1f, tween(800), label = "donut")
                Canvas(Modifier.size(100.dp)) {
                    val stroke = 20.dp.toPx()
                    val d = size.minDimension - stroke
                    var start = -90f
                    slices.forEachIndexed { i, (_, v) ->
                        val ang = 360f * v / total * sweep
                        drawArc(colors[i], start + 1.5f, (ang - 3f).coerceAtLeast(1f), false, Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(stroke))
                        start += ang
                    }
                }
                Text("${total.toInt()}", style = NumberStyle, fontSize = 14.sp, color = Pal.c.foreground)
            }
        }
        Column {
            slices.forEachIndexed { i, (name, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Brand.chartColors[(i + colorOffset) % Brand.chartColors.size]))
                    Spacer(Modifier.width(4.dp))
                    Text("$name · $v", fontSize = 9.sp, color = Pal.c.mutedForeground)
                }
            }
        }
    }
}

@Composable
private fun BarChart(bars: List<Bar>, color: Color, key: String) {
    val state = rememberLazyListState()
    LaunchedEffect(key) { state.scrollToItem(max(0, bars.size - 1)) }
    val mx = bars.maxOf { it.count }.coerceAtLeast(1)
    LazyRow(state = state, modifier = Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(bars) { _, b ->
            Column(Modifier.width(30.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (b.count > 0) Text("${b.count}", fontSize = 9.sp, style = MonoStyle, color = if (b.highlight) color else Pal.c.mutedForeground)
                val frac = if (b.count == 0) .02f else b.count.toFloat() / mx
                Box(
                    Modifier
                        .width(18.dp)
                        .fillMaxHeight(frac * .72f)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (b.count == 0) Brush.verticalGradient(listOf(Pal.c.muted, Pal.c.muted))
                            else Brush.verticalGradient(listOf(color, color.copy(alpha = if (b.highlight) .8f else .45f)))
                        )
                )
                Text(b.label, fontSize = 8.sp, color = if (b.highlight) color else Pal.c.mutedForeground, maxLines = 1, softWrap = false)
                Text(b.sub ?: " ", fontSize = 7.sp, color = Pal.c.mutedForeground, maxLines = 1)
            }
        }
    }
}
