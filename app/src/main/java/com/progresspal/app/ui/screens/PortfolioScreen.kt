@file:OptIn(ExperimentalLayoutApi::class)

package com.progresspal.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progresspal.app.data.AppData
import com.progresspal.app.data.Portfolio
import com.progresspal.app.data.PortfolioEntry
import com.progresspal.app.data.PortfolioPhase
import com.progresspal.app.data.Project
import com.progresspal.app.data.portfolioRange
import com.progresspal.app.data.progress
import com.progresspal.app.ui.components.DatePickerModal
import com.progresspal.app.ui.theme.Pal
import com.progresspal.app.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val RowH = 66.dp
private val LeftW = 150.dp
private val HeadH = 48.dp

/** Colours to pick from, bright like a project dashboard. */
private val PortfolioColors = listOf(
    0xFF1565C0, 0xFFFF5A4E, 0xFF3BA9E0, 0xFFFF8F00, 0xFFFFC107, 0xFF7EC8F5,
    0xFF2BB673, 0xFF8E24AA, 0xFFE91E63, 0xFF26A69A, 0xFF5C6BC0, 0xFF795548,
)

/** Arrow-shaped bar: flat on the left, pointed on the right. */
private val ArrowShape = GenericShape { size, _ ->
    val tip = minOf(size.height * 0.45f, size.width * 0.3f)
    moveTo(0f, 0f); lineTo(size.width - tip, 0f); lineTo(size.width, size.height / 2)
    lineTo(size.width - tip, size.height); lineTo(0f, size.height); close()
}

@Composable
fun PortfolioScreen(app: AppData, onBack: () -> Unit) {
    val pf by Portfolio.data.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Project?>(null) }
    val today = LocalDate.now()

    val projects = app.projects
        .filter { (pf.showCompleted || it.completedAt == null) && !(pf.entries[it.id]?.hidden ?: false) }
        .sortedWith(compareBy({ pf.entries[it.id]?.order ?: Int.MAX_VALUE }, { it.createdAt }))
    val hiddenCount = app.projects.count { pf.entries[it.id]?.hidden == true }
    val ranges = projects.associate { it.id to portfolioRange(it, pf.entries[it.id] ?: PortfolioEntry()) }

    // Timeline: from a week before the first start to two weeks after the last end, always including today.
    var first = (ranges.values.map { it.first } + today).min().minusDays(7).let { it.minusDays(((it.dayOfWeek.value - 1).toLong())) }
    var last = (ranges.values.map { it.second } + today).max().plusDays(14)
    // Fine zooms draw a shorter stretch around today so the page stays light.
    when (pf.zoom) {
        "hour" -> { if (first.isBefore(today.minusDays(7))) first = today.minusDays(7); if (last.isAfter(today.plusDays(21))) last = today.plusDays(21) }
        "day" -> { if (first.isBefore(today.minusDays(60))) first = today.minusDays(60); if (last.isAfter(today.plusDays(150))) last = today.plusDays(150) }
    }
    val days = ChronoUnit.DAYS.between(first, last).toInt() + 1
    val dayW: Dp = when (pf.zoom) { "hour" -> 480.dp; "day" -> 96.dp; "week" -> 22.dp; "quarter" -> 3.dp; else -> 8.dp }
    val nowFrac = java.time.LocalTime.now().toSecondOfDay() / 86_400f
    fun x(d: LocalDate): Dp = dayW * ChronoUnit.DAYS.between(first, d).toFloat()

    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(pf.zoom, first) {
        val px = with(density) { (x(today) + dayW * nowFrac - 80.dp).toPx() }.toInt().coerceAtLeast(0)
        hScroll.scrollTo(px)
    }

    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.c.foreground) }
            Column(Modifier.weight(1f)) {
                Text("Your Project Portfolio", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Pal.c.foreground)
                Text("Tap a project to change its dates, colour or phases", fontSize = 11.sp, color = Pal.c.mutedForeground)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("hour" to "Hours", "day" to "Days", "week" to "Weeks", "month" to "Months", "quarter" to "Quarters").forEach { (k, l) ->
                PChip(l, pf.zoom == k) { Portfolio.setScale(k) }
            }
            Spacer(Modifier.width(6.dp))
            Text("Completed", fontSize = 12.sp, color = Pal.c.mutedForeground)
            Switch(checked = pf.showCompleted, onCheckedChange = { Portfolio.setShowCompleted(it) })
        }
        if (hiddenCount > 0) Text(
            "$hiddenCount hidden — tap here to show all",
            Modifier.padding(horizontal = 16.dp, vertical = 2.dp).clickable {
                app.projects.forEach { p -> pf.entries[p.id]?.takeIf { it.hidden }?.let { Portfolio.save(p.id, it.copy(hidden = false)) } }
            },
            fontSize = 12.sp, color = Pal.c.primary,
        )
        Spacer(Modifier.height(8.dp))

        if (projects.isEmpty()) {
            Text("No ongoing projects yet. Create one on Home.", Modifier.padding(24.dp), color = Pal.c.mutedForeground)
            return@Column
        }

        Box(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp))
                .background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState()),
        ) {
            Row {
                // Left: projects + health
                Column(Modifier.width(LeftW)) {
                    Row(Modifier.height(HeadH).fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.Bottom) {
                        Text("Projects", Modifier.weight(1f).padding(bottom = 6.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pal.c.foreground)
                        Text("Health", Modifier.padding(bottom = 6.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pal.c.foreground)
                    }
                    projects.forEach { p ->
                        val c = projColor(p, pf.entries[p.id])
                        Row(
                            Modifier.height(RowH).fillMaxWidth().clickable { editing = p }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).background(c, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(p.name, Modifier.weight(1f), fontSize = 13.sp, color = Pal.c.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            HealthRing(p.progress(), c)
                        }
                    }
                }
                Box(Modifier.width(1.dp).height(HeadH + RowH * projects.size).background(Pal.c.border))

                // Right: scrollable timeline
                Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    val totalW = dayW * days.toFloat()
                    val totalH = HeadH + RowH * projects.size
                    Box(Modifier.width(totalW).height(totalH)) {
                        // Week lines and calendar-date labels (every day when zoomed to weeks).
                        val weekStep = if (pf.zoom == "quarter") 4 else 1
                        var w = 0
                        var d = first
                        while (!d.isAfter(last)) {
                            if (w % weekStep == 0 && pf.zoom != "day" && pf.zoom != "hour") {
                                Box(Modifier.offset(x = x(d), y = HeadH).width(1.dp).height(RowH * projects.size).background(Pal.c.border.copy(alpha = .5f)))
                                if (pf.zoom != "week") Text(
                                    d.format(DateTimeFormatter.ofPattern("d MMM")),
                                    Modifier.offset(x = x(d) + 3.dp, y = 26.dp), fontSize = 10.sp,
                                    color = if (!d.isAfter(today) && today.isBefore(d.plusWeeks(weekStep.toLong()))) Color(0xFF1565C0) else Pal.c.mutedForeground,
                                    maxLines = 1,
                                )
                            }
                            w++; d = d.plusWeeks(1)
                        }
                        // Days view: weekday + date per day, faint lines every 6 hours.
                        if (pf.zoom == "day" || pf.zoom == "hour") {
                            var dd = first
                            while (!dd.isAfter(last)) {
                                val isToday = dd == today
                                Box(Modifier.offset(x = x(dd), y = HeadH).width(1.dp).height(RowH * projects.size).background(Pal.c.border.copy(alpha = .7f)))
                                Text(
                                    dd.format(DateTimeFormatter.ofPattern(if (pf.zoom == "hour") "EEE d MMM" else "EEE d")),
                                    Modifier.offset(x = x(dd) + 4.dp, y = if (pf.zoom == "hour") 6.dp else 24.dp),
                                    fontSize = 11.sp, maxLines = 1,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isToday) Color(0xFF1565C0) else if (dd.dayOfWeek.value >= 6) Pal.c.mutedForeground else Pal.c.foreground,
                                )
                                val step = if (pf.zoom == "hour") 1 else 6
                                for (h in step until 24 step step) {
                                    val hx = x(dd) + dayW * (h / 24f)
                                    Box(Modifier.offset(x = hx, y = HeadH).width(1.dp).height(RowH * projects.size).background(Pal.c.border.copy(alpha = if (h % 6 == 0) .35f else .18f)))
                                    if (pf.zoom == "hour" || h % 6 == 0) Text(
                                        when { h == 12 -> "12p"; h < 12 -> "${h}a"; else -> "${h - 12}p" },
                                        Modifier.offset(x = hx + 2.dp, y = if (pf.zoom == "hour") 28.dp else 38.dp),
                                        fontSize = if (pf.zoom == "hour") 10.sp else 8.sp, color = Pal.c.mutedForeground, maxLines = 1,
                                    )
                                }
                                dd = dd.plusDays(1)
                            }
                        }
                        if (pf.zoom == "week") {
                            var dd = first
                            while (!dd.isAfter(last)) {
                                Text(
                                    "${dd.dayOfMonth}",
                                    Modifier.offset(x = x(dd), y = 26.dp).width(dayW),
                                    fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                                    fontWeight = if (dd == today) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        dd == today -> Color(0xFF1565C0)
                                        dd.dayOfWeek.value >= 6 -> Pal.c.mutedForeground.copy(alpha = .6f)
                                        else -> Pal.c.mutedForeground
                                    },
                                )
                                dd = dd.plusDays(1)
                            }
                        }
                        var m = first.withDayOfMonth(1).let { if (it.isBefore(first)) it.plusMonths(1) else it }
                        if (pf.zoom == "day") m = first // the month name sits at the start of the view too
                        while (pf.zoom != "hour" && !m.isAfter(last)) {
                            Text(
                                m.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                Modifier.offset(x = x(m) + 3.dp, y = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                color = Pal.c.foreground, maxLines = 1,
                            )
                            m = m.withDayOfMonth(1).plusMonths(1)
                        }

                        // Bars
                        projects.forEachIndexed { i, p ->
                            val e = pf.entries[p.id] ?: PortfolioEntry()
                            val c = projColor(p, e)
                            val top = HeadH + RowH * i + 12.dp
                            val bars = if (e.phases.isNotEmpty()) e.phases.mapNotNull { ph ->
                                val s = runCatching { LocalDate.parse(ph.start) }.getOrNull() ?: return@mapNotNull null
                                val en = runCatching { LocalDate.parse(ph.end) }.getOrNull() ?: return@mapNotNull null
                                Triple(ph.name, s, en)
                            } else listOf(ranges.getValue(p.id).let { Triple(p.name, it.first, it.second) })
                            bars.forEachIndexed { k, (name, s0, en0) ->
                                if (en0.isBefore(first) || s0.isAfter(last)) return@forEachIndexed
                                val s = if (s0.isBefore(first)) first else s0
                                val en = if (en0.isAfter(last)) last else en0
                                val left = x(s); val width = (x(en.plusDays(1)) - left - 3.dp).coerceAtLeast(18.dp)
                                // Phases alternate a lighter shade so neighbours stand apart.
                                val shade = if (e.phases.isNotEmpty() && k % 2 == 1) c.copy(alpha = .85f) else c
                                Box(
                                    Modifier.offset(x = left, y = top).width(width).height(RowH - 24.dp)
                                        .clip(ArrowShape).background(shade).clickable { editing = p }
                                        .padding(start = 8.dp, end = 14.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Column {
                                        Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (width > 90.dp) Text(
                                            "${s.format(DateTimeFormatter.ofPattern("d MMM"))} – ${en.format(DateTimeFormatter.ofPattern("d MMM"))}",
                                            color = Color.White.copy(alpha = .85f), fontSize = 10.sp, maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }

                        // Today line + pill
                        val tx = x(today) + if (pf.zoom == "hour" || pf.zoom == "day") dayW * nowFrac else dayW / 2
                        Canvas(Modifier.offset(x = tx).width(2.dp).height(totalH)) {
                            drawLine(
                                Color(0xFF1565C0), Offset(0f, 18.dp.toPx()), Offset(0f, size.height),
                                strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                            )
                        }
                        Text(
                            "Today",
                            Modifier.offset(x = tx - 22.dp, y = 0.dp).clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1565C0)).padding(horizontal = 6.dp, vertical = 1.dp),
                            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    editing?.let { p ->
        PortfolioEditor(p, Portfolio.entry(p.id), projects.map { it.id }) { editing = null }
    }
}

private fun projColor(p: Project, e: PortfolioEntry?): Color = e?.color?.let { Color(it.toInt()) } ?: p.color.color()

@Composable
private fun HealthRing(pct: Int, c: Color) {
    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(34.dp)) {
            val st = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(c.copy(alpha = .18f), 0f, 360f, false, style = st)
            drawArc(c, -90f, 360f * pct / 100f, false, style = st)
        }
        Text("$pct%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Pal.c.foreground)
    }
}

@Composable
private fun PChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Pal.c.primary else Pal.c.muted)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (selected) Color.White else Pal.c.foreground, fontSize = 13.sp,
    )
}

/** Change a project's dates, colour, phases, place in the list, or hide it. */
@Composable
private fun PortfolioEditor(p: Project, initial: PortfolioEntry, order: List<String>, onDismiss: () -> Unit) {
    val (s0, e0) = portfolioRange(p, initial)
    var start by remember { mutableStateOf(s0) }
    var end by remember { mutableStateOf(e0) }
    var color by remember { mutableStateOf(initial.color) }
    var phases by remember { mutableStateOf(initial.phases) }
    var hidden by remember { mutableStateOf(initial.hidden) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var phaseEdit by remember { mutableStateOf<PortfolioPhase?>(null) }
    val f = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun entry() = initial.copy(start = start.toString(), end = end.toString(), color = color, phases = phases, hidden = hidden)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.c.card,
        title = { Text(p.name, color = Pal.c.foreground) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Label2("Start – end")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { pickStart = true }) { Text(start.format(f), color = Pal.c.foreground, fontSize = 13.sp) }
                    OutlinedButton(onClick = { pickEnd = true }) { Text(end.format(f), color = Pal.c.foreground, fontSize = 13.sp) }
                }
                Label2("Colour")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(p.color.color())
                            .border(if (color == null) 3.dp else 0.dp, Color.White, CircleShape).clickable { color = null },
                    )
                    PortfolioColors.forEach { c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(c.toInt()))
                                .border(if (color == c) 3.dp else 0.dp, Color.White, CircleShape).clickable { color = c },
                        )
                    }
                }
                Label2("Phases (optional) — e.g. Planning, Operations, Sign off")
                phases.sortedBy { it.start }.forEach { ph ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Pal.c.muted)
                            .clickable { phaseEdit = ph }.padding(10.dp),
                    ) {
                        Text(ph.name, Modifier.weight(1f), color = Pal.c.foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${LocalDate.parse(ph.start).format(DateTimeFormatter.ofPattern("d MMM"))} – ${LocalDate.parse(ph.end).format(DateTimeFormatter.ofPattern("d MMM"))}", color = Pal.c.mutedForeground, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = {
                    val from = phases.maxOfOrNull { LocalDate.parse(it.end) }?.plusDays(1) ?: start
                    phaseEdit = PortfolioPhase(name = "", start = from.toString(), end = from.plusDays(13).let { cand -> val lim = if (end.isAfter(from)) end else from; if (cand.isAfter(lim)) lim else cand }.toString())
                }) { Text("+ Add phase") }

                Label2("Place in the list")
                Row {
                    TextButton(onClick = { Portfolio.move(order, p.id, -1) }) { Text("▲ Move up") }
                    TextButton(onClick = { Portfolio.move(order, p.id, 1) }) { Text("▼ Move down") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide from portfolio", Modifier.weight(1f), color = Pal.c.foreground, fontSize = 14.sp)
                    Switch(checked = hidden, onCheckedChange = { hidden = it })
                }
                TextButton(onClick = {
                    Portfolio.save(p.id, PortfolioEntry(order = initial.order)); onDismiss()
                }) { Text("Reset to project dates", color = Pal.c.destructive) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Keep the order the user set (it may have been changed with Move up/down meanwhile).
                Portfolio.save(p.id, entry().copy(order = Portfolio.entry(p.id).order))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickStart) DatePickerModal(start, { pickStart = false }) { start = it; if (end.isBefore(it)) end = it; pickStart = false }
    if (pickEnd) DatePickerModal(end, { pickEnd = false }) { end = if (it.isBefore(start)) start else it; pickEnd = false }
    phaseEdit?.let { ph ->
        PhaseEditor(ph, isNew = phases.none { it.id == ph.id },
            onSave = { np -> phases = phases.filterNot { it.id == np.id } + np; phaseEdit = null },
            onDelete = { phases = phases.filterNot { it.id == ph.id }; phaseEdit = null },
            onDismiss = { phaseEdit = null },
        )
    }
}

@Composable
private fun PhaseEditor(ph: PortfolioPhase, isNew: Boolean, onSave: (PortfolioPhase) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(ph.name) }
    var s by remember { mutableStateOf(LocalDate.parse(ph.start)) }
    var e by remember { mutableStateOf(LocalDate.parse(ph.end)) }
    var pickS by remember { mutableStateOf(false) }
    var pickE by remember { mutableStateOf(false) }
    val f = DateTimeFormatter.ofPattern("d MMM yyyy")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.c.card,
        title = { Text(if (isNew) "New phase" else "Edit phase", color = Pal.c.foreground) },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name, e.g. Planning") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { pickS = true }) { Text(s.format(f), color = Pal.c.foreground, fontSize = 13.sp) }
                    OutlinedButton(onClick = { pickE = true }) { Text(e.format(f), color = Pal.c.foreground, fontSize = 13.sp) }
                }
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete phase", color = Pal.c.destructive) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(ph.copy(name = name.trim().ifBlank { "Phase" }, start = s.toString(), end = (if (e.isBefore(s)) s else e).toString()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickS) DatePickerModal(s, { pickS = false }) { s = it; if (e.isBefore(it)) e = it; pickS = false }
    if (pickE) DatePickerModal(e, { pickE = false }) { e = if (it.isBefore(s)) s else it; pickE = false }
}

@Composable
private fun Label2(t: String) {
    Spacer(Modifier.height(12.dp))
    Text(t, color = Pal.c.mutedForeground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}
