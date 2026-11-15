package com.progresspal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.data.AppData
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.format
import com.progresspal.app.data.salatOn
import com.progresspal.app.data.salatBeads
import androidx.compose.ui.graphics.asImageBitmap
import com.progresspal.app.data.salatSlot
import com.progresspal.app.data.salatStats
import com.progresspal.app.data.salatTasks
import com.progresspal.app.data.today
import com.progresspal.app.ui.components.AppCard
import com.progresspal.app.ui.components.IconTile
import com.progresspal.app.ui.components.SectionLabel
import com.progresspal.app.ui.theme.Brand
import com.progresspal.app.ui.theme.MonoStyle
import com.progresspal.app.ui.theme.NumberStyle
import com.progresspal.app.ui.theme.Pal
import kotlin.math.max
import kotlin.math.roundToInt

/** Salat streaks: current/longest streak, streak history chart, last-30-days chart, per-prayer consistency. */
@Composable
fun SalatAnalytics(data: AppData) {
    val stats = data.salatStats()
    if (!stats.enabled) {
        AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), corner = 18.dp, borderColor = Brand.green.copy(alpha = .25f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.Brightness3, Brand.green)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Salat streak", fontWeight = FontWeight.SemiBold, color = Pal.c.foreground)
                    Text("No prayer tasks found. Tap Start to add Fajr–Isha to Daily Tasks", fontSize = 12.sp, color = Pal.c.mutedForeground)
                }
                TextButton(onClick = { AppStore.enableSalat() }) { Text("Start") }
            }
        }
        return
    }
    val tasks = data.salatTasks()
    val today = today()

    // ── Header + hero numbers ──
    AppCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), corner = 18.dp, borderColor = Brand.green.copy(alpha = .3f), background = Brand.green.copy(alpha = .06f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            IconTile(Icons.Filled.Brightness3, Brand.green, size = 32.dp)
            Spacer(Modifier.width(8.dp))
            Text("Salat Streak", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Pal.c.foreground, modifier = Modifier.weight(1f))
            Text("Today ${stats.todayCount}/${stats.total}", fontSize = 12.sp, color = Pal.c.mutedForeground)
        }
        // The streak as a string of rainbow beads (same drawing as the widget).
        val beadInfo = data.salatBeads()
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            val wPx = constraints.maxWidth
            val bmp = androidx.compose.runtime.remember(beadInfo, wPx) {
                com.progresspal.app.ui.BeadRenderer.render(
                    com.progresspal.app.ui.BeadRenderer.Spec(beadInfo.done, beadInfo.pending, beadInfo.labels), wPx, density, maxRows = 8,
                )
            }
            androidx.compose.foundation.Image(
                bmp.asImageBitmap(), "Salat streak beads", Modifier.fillMaxWidth(),
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SalatStat(Icons.Filled.LocalFireDepartment, Brand.coral, "${stats.current}", "Current streak", Modifier.weight(1f))
            SalatStat(Icons.Filled.WorkspacePremium, Brand.purple, "${stats.longest?.length ?: 0}", "Longest streak", Modifier.weight(1f))
            SalatStat(Icons.Filled.CheckCircle, Brand.green, "${stats.perfectDays}", "Perfect days", Modifier.weight(1f))
        }
        stats.longest?.let { l ->
            Text(
                "🏆 Longest streak: ${l.length} prayer${if (l.length == 1) "" else "s"} in a row · ${l.start.format("MMM d, yyyy")} – ${l.end.format("MMM d, yyyy")}" +
                    if (stats.current > 0 && stats.current >= l.length) "  (that's now — keep going!)" else "",
                fontSize = 12.sp, color = Brand.purple, modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (stats.longest != null && stats.current < stats.longest.length) {
            val need = stats.longest.length - stats.current
            Text("$need more prayer${if (need == 1) "" else "s"} without a skip to beat your record", fontSize = 11.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(top = 2.dp))
        }
    }

    // ── Current vs best streak (horizontal bars on the same scale) ──
    AppCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), corner = 18.dp, padding = 14.dp) {
        SectionLabel("Present vs best streak", Icons.Filled.LocalFireDepartment, Brand.coral)
        val best = stats.longest?.length ?: 0
        val scale = maxOf(best, stats.current, 1).toFloat()
        StreakBar("🔥 Present", stats.current, stats.current / scale, Brush.horizontalGradient(listOf(Brand.coral, Brand.amber)), Brand.coral)
        Spacer(Modifier.height(10.dp))
        StreakBar("👑 Best", best, best / scale, Brush.horizontalGradient(listOf(Brand.purple, Brand.blue)), Brand.purple)
        stats.longest?.let {
            Text(
                "Best: ${it.start.format("MMM d")} – ${it.end.format("MMM d, yyyy")}",
                fontSize = 10.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    // ── Streak history chart: one bar per unbroken run; the longest is highlighted ──
    AppCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), corner = 18.dp, padding = 12.dp) {
        SectionLabel("Streak history · prayers in a row", Icons.Filled.Timeline, Brand.purple)
        if (stats.runs.isEmpty()) {
            Text("Tick your prayers to start your first streak.", fontSize = 12.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        } else {
            val mx = stats.runs.maxOf { it.length }.coerceAtLeast(1)
            val listState = rememberLazyListState()
            LaunchedEffect(stats.runs.size) { listState.scrollToItem(max(0, stats.runs.size - 1)) }
            LazyRow(state = listState, modifier = Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(stats.runs) { r ->
                    val best = r == stats.longest
                    val live = r.end == today || r.end == today.minusDays(1)
                    val c = when {
                        best -> Brand.purple
                        live -> Brand.coral
                        else -> Brand.green
                    }
                    Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (best) Text("👑", fontSize = 11.sp)
                        Text("${r.length}", fontSize = 10.sp, style = MonoStyle, color = c, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier.width(22.dp).fillMaxHeight(max(.04f, r.length.toFloat() / mx) * .62f)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(Brush.verticalGradient(listOf(c, c.copy(alpha = .5f))))
                        )
                        Text(r.start.format("MMM d"), fontSize = 8.sp, color = if (best) c else Pal.c.mutedForeground, maxLines = 1, softWrap = false)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                Legend(Brand.purple, "Longest"); Legend(Brand.coral, "Current"); Legend(Brand.green, "Past")
            }
        }
    }

    // ── Prayers per day, last 30 days ──
    AppCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), corner = 18.dp, padding = 12.dp) {
        SectionLabel("Prayers per day · last 30 days", Icons.Filled.BarChart, Brand.green)
        val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { d ->
                val scheduled = data.salatOn(d)
                val n = scheduled.count { it.completed }
                val full = scheduled.isNotEmpty() && n == scheduled.size
                Box(
                    Modifier.weight(1f).fillMaxHeight(max(.03f, n.toFloat() / scheduled.size.coerceAtLeast(1)))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            when {
                                n == 0 -> Pal.c.muted
                                full -> Brand.green
                                else -> Brand.amber.copy(alpha = .75f)
                            }
                        )
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(days.first().format("MMM d"), fontSize = 9.sp, color = Pal.c.mutedForeground, modifier = Modifier.weight(1f))
            Text("Today", fontSize = 9.sp, color = Pal.c.mutedForeground)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
            Legend(Brand.green, "All prayed"); Legend(Brand.amber, "Some missed"); Legend(Pal.c.muted, "None")
        }
    }

    // ── Per-prayer consistency (last 30 days) ──
    AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), corner = 18.dp, padding = 12.dp) {
        SectionLabel("Each prayer · last 30 days", Icons.Filled.CheckCircle, Brand.blue)
        // Group by prayer (Zahar and Zumma both count as Dhuhr) over the days each was scheduled.
        val last30 = (0..29).map { today.minusDays(it.toLong()) }.map { data.salatOn(it) }
        val names = tasks.groupBy { salatSlot(it.title) }.mapValues { (_, ts) -> ts.first().title }
        names.entries.forEachIndexed { i, (slot, label) ->
            val sched = last30.flatMap { day -> day.filter { salatSlot(it.task.title) == slot } }
            val pct = if (sched.isEmpty()) 0 else (sched.count { it.completed } * 100.0 / sched.size).roundToInt()
            val c = Brand.chartColors[i % Brand.chartColors.size]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Pal.c.foreground, modifier = Modifier.width(64.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(Pal.c.muted)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).clip(CircleShape).background(c))
                }
                Text("$pct%", fontSize = 11.sp, style = MonoStyle, color = Pal.c.mutedForeground, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun SalatStat(icon: ImageVector, color: Color, value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .1f)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(value, style = NumberStyle, fontSize = 24.sp, color = color)
        Text(label.uppercase(), fontSize = 9.sp, letterSpacing = 1.sp, color = Pal.c.mutedForeground)
    }
}

@Composable
private fun StreakBar(label: String, value: Int, fraction: Float, brush: Brush, color: Color) {
    val anim by androidx.compose.animation.core.animateFloatAsState(fraction.coerceIn(0f, 1f), androidx.compose.animation.core.tween(700), label = "streak")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Pal.c.foreground, modifier = Modifier.width(84.dp))
        Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(Pal.c.muted)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(RoundedCornerShape(9.dp)).background(brush))
        }
        Text("$value", style = NumberStyle, fontSize = 15.sp, color = color, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Pal.c.mutedForeground)
    }
}
