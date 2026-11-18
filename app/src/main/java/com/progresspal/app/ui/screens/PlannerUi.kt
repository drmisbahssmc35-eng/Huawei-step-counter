@file:OptIn(ExperimentalLayoutApi::class)

package com.progresspal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.data.AppData
import com.progresspal.app.data.PlanItem
import com.progresspal.app.data.Planner
import com.progresspal.app.data.SttCache
import com.progresspal.app.data.activeProjects
import com.progresspal.app.data.dailyTasksFor
import com.progresspal.app.ui.components.DatePickerModal
import com.progresspal.app.ui.components.TimePickerModal
import com.progresspal.app.ui.components.toast
import com.progresspal.app.ui.theme.Pal
import com.progresspal.app.ui.theme.color
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val DurationChoices = listOf(5, 10, 15, 20, 25, 30, 45, 60, 90, 120, 180, 240)

private data class Pick(
    val kind: String,
    val refId: String?,
    val title: String,
    val subtitle: String?,
    val color: Long?,
    val activityId: Long? = null,
)

@Composable
fun PlanEditor(initial: PlanItem, isNew: Boolean, app: AppData, stt: SttCache, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val memo = Planner.data.value.lastActivityFor

    var pick by remember {
        mutableStateOf(
            if (isNew && initial.title.isBlank()) null
            else Pick(initial.kind, initial.refId, initial.title, initial.subtitle, initial.color, initial.activityId)
        )
    }
    var activityId by remember { mutableStateOf(initial.activityId) }
    var search by remember { mutableStateOf("") }
    var startMs by remember { mutableLongStateOf(initial.start) }
    var minutes by remember { mutableIntStateOf(((initial.end - initial.start) / 60_000).toInt().coerceAtLeast(5)) }
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    val startZ = Instant.ofEpochMilli(startMs).atZone(zone)
    val date = startZ.toLocalDate()
    val endMs = startMs + minutes * 60_000L
    val hm = DateTimeFormatter.ofPattern("h:mm a")

    fun choose(p: Pick) {
        pick = p
        // Pre-select the activity: the item itself, last used, or one with the same name.
        activityId = p.activityId
            ?: memo["${p.kind}:${p.refId}"]
            ?: stt.activities.firstOrNull { it.name.equals(p.title, true) || it.name.equals(p.subtitle ?: "", true) }?.id
            ?: activityId
    }

    val q = search.trim()
    fun match(s: String) = q.isEmpty() || s.contains(q, ignoreCase = true)
    val tasks = dailyTasksFor(app, date).filter { match(it.task.title) || match(it.project.name) }.take(40)
    val projects = activeProjects(app.projects).filter { match(it.name) }.take(30)
    val acts = stt.activities.filter { match(it.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Plan a block" else "Edit plan", color = Pal.c.foreground) },
        containerColor = Pal.c.card,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Section("When") {
                    val nextMinute = (System.currentTimeMillis() / 60_000L + 1) * 60_000L
                    val lastEnd = Planner.data.value.plans
                        .filter { it.id != initial.id && Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == date }
                        .maxOfOrNull { it.end }
                    if (date == LocalDate.now()) Chip("Now + 1 min", Pal.c.primary, startMs == nextMinute) { startMs = nextMinute }
                    if (lastEnd != null) Chip("After last plan · ${hm.format(Instant.ofEpochMilli(lastEnd).atZone(zone))}", Pal.c.primary, startMs == lastEnd) {
                        startMs = lastEnd
                    }
                    OutlinedButton(onClick = { showDate = true }) { Text(DateTimeFormatter.ofPattern("EEE d MMM").format(date), color = Pal.c.foreground) }
                    OutlinedButton(onClick = { showTime = true }) {
                        Text("Start ${hm.format(startZ)} – ${hm.format(Instant.ofEpochMilli(endMs).atZone(zone))}", color = Pal.c.foreground)
                    }
                }
                Section("Duration · ${fmtMinutes(minutes)}") {
                    DurationChoices.forEach { m ->
                        Chip(fmtMinutes(m), Pal.c.primary, minutes == m) { minutes = m }
                    }
                    Chip("−5", Pal.c.mutedForeground, false) { minutes = (minutes - 5).coerceAtLeast(5) }
                    Chip("+5", Pal.c.mutedForeground, false) { minutes += 5 }
                }

                Spacer(Modifier.height(6.dp))
                // Selected
                pick?.let { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background((p.color ?: 0xFF5B8DEF).asC().copy(alpha = .18f)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).background((p.color ?: 0xFF5B8DEF).asC(), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.title, color = Pal.c.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            p.subtitle?.let { Text(it, color = Pal.c.mutedForeground, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    label = { Text("Search tasks, projects, activities") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (q.isNotEmpty()) TextButton(onClick = { choose(Pick("custom", null, q, null, null)) }) {
                    Text("Use \"$q\" as a custom plan")
                }

                if (tasks.isNotEmpty()) Section("Tasks · ${DateTimeFormatter.ofPattern("EEE d MMM").format(date)}") {
                    tasks.forEach { e ->
                        val c = e.project.color.color()
                        Chip(e.task.title, c, pick?.kind == "task" && pick?.refId == e.task.id, done = e.completed) {
                            choose(Pick("task", e.task.id, e.task.title, e.project.name, c.toArgbLong()))
                        }
                    }
                }
                if (projects.isNotEmpty()) Section("Projects") {
                    projects.forEach { p ->
                        val c = p.color.color()
                        Chip(p.name, c, pick?.kind == "project" && pick?.refId == p.id) {
                            choose(Pick("project", p.id, p.name, p.target, c.toArgbLong()))
                        }
                    }
                }
                if (acts.isNotEmpty()) Section("Simple Time Tracker activities") {
                    acts.forEach { a ->
                        Chip(a.name, a.color.asC(), pick?.kind == "activity" && pick?.refId == a.id.toString()) {
                            choose(Pick("activity", a.id.toString(), a.name, null, a.color, a.id))
                        }
                    }
                }

                if (stt.activities.isNotEmpty()) Section("Counts as done when I track") {
                    stt.activities.forEach { a ->
                        Chip(a.name, a.color.asC(), activityId == a.id) { activityId = if (activityId == a.id) null else a.id }
                    }
                }

                if (!isNew) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { Planner.delete(initial.id); onDismiss() }) {
                        Text("Delete plan", color = Pal.c.destructive)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = pick
                if (p == null) { toast(ctx, "Pick a task, project or activity"); return@TextButton }
                val actName = stt.activities.firstOrNull { it.id == activityId }?.name
                Planner.save(
                    initial.copy(
                        start = startMs, end = endMs, title = p.title, kind = p.kind, refId = p.refId,
                        subtitle = p.subtitle, activityId = activityId, activityName = actName,
                        color = p.color ?: stt.activities.firstOrNull { it.id == activityId }?.color,
                    )
                )
                if (activityId == null) toast(ctx, "Tip: pick an activity under \"Counts as done\" so the plan can be checked")
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showTime) TimePickerModal(startZ.toLocalTime(), { showTime = false }) { t: LocalTime ->
        startMs = date.atTime(t).atZone(zone).toInstant().toEpochMilli()
        showTime = false
    }
    if (showDate) DatePickerModal(date, { showDate = false }) { d: LocalDate ->
        startMs = d.atTime(startZ.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        showDate = false
    }
}

fun fmtMinutes(m: Int): String = when {
    m < 60 -> "${m}m"
    m % 60 == 0 -> "${m / 60}h"
    else -> "${m / 60}h ${m % 60}m"
}

private fun Long.asC() = Color(this.toInt())
private fun Color.toArgbLong(): Long =
    ((alpha * 255).toLong() shl 24) or ((red * 255).toLong() shl 16) or ((green * 255).toLong() shl 8) or (blue * 255).toLong()

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Text(title, color = Pal.c.mutedForeground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun Chip(text: String, color: Color, selected: Boolean, done: Boolean = false, onClick: () -> Unit) {
    Text(
        (if (done) "✓ " else "") + text,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else color.copy(alpha = .14f))
            .border(1.dp, color.copy(alpha = if (selected) 1f else .4f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = if (selected) Color.White else Pal.c.foreground,
        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}
