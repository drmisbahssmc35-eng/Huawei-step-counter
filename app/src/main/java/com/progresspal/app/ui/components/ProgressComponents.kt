package com.progresspal.app.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.DAY_MS
import com.progresspal.app.data.DailyEntry
import com.progresspal.app.data.Project
import com.progresspal.app.data.ProjectColor
import com.progresspal.app.data.Repeat
import com.progresspal.app.data.Task
import com.progresspal.app.data.format
import com.progresspal.app.data.formatRemaining
import com.progresspal.app.data.formatTime12
import com.progresspal.app.data.millis
import com.progresspal.app.data.pad2
import com.progresspal.app.data.parseTime
import com.progresspal.app.data.progress
import com.progresspal.app.data.startMillis
import com.progresspal.app.data.timeProgress
import com.progresspal.app.data.toLocalDate
import com.progresspal.app.data.toLocalDateTime
import com.progresspal.app.data.today
import com.progresspal.app.data.weekStart
import com.progresspal.app.ui.theme.Brand
import com.progresspal.app.ui.theme.MonoStyle
import com.progresspal.app.ui.theme.NumberStyle
import com.progresspal.app.ui.theme.Pal
import com.progresspal.app.ui.theme.color
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

// ───────────────────────── Week strip ─────────────────────────

@Composable
fun WeekStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = today()
    val start = selected.weekStart()
    val days = (0..6).map { start.plusDays(it.toLong()) }
    AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onSelect(selected.minusDays(7)) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronLeft, "Previous week", tint = Pal.c.mutedForeground)
            }
            Text(
                "${days.first().format("MMM d")} – ${days.last().format("MMM d, yyyy")}",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Pal.c.mutedForeground,
            )
            IconButton(onClick = { onSelect(selected.plusDays(7)) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronRight, "Next week", tint = Pal.c.mutedForeground)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { day ->
                val isSel = day == selected
                val isToday = day == today
                val bg = when {
                    isSel -> Pal.c.primary
                    isToday -> Pal.c.primary.copy(alpha = .12f)
                    else -> Color.Transparent
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { onSelect(day) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        day.format("EEE").uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Medium,
                        color = if (isSel) Color.White.copy(alpha = .85f) else Pal.c.mutedForeground,
                    )
                    Text(
                        day.dayOfMonth.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = when {
                            isSel -> Color.White
                            isToday -> Pal.c.primary
                            else -> Pal.c.foreground
                        },
                    )
                }
            }
        }
        if (selected != today) {
            Box(Modifier.fillMaxWidth().background(Pal.c.border).height(1.dp))
            Text(
                "← Back to today", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Pal.c.primary,
                modifier = Modifier.clickable { onSelect(today) }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

// ───────────────────────── Day progress ─────────────────────────

data class DayClock(val percent: Int, val label: String)

fun dayClock(date: LocalDate, now: Long): DayClock {
    val today = today()
    return when {
        date.isBefore(today) -> DayClock(100, "completed")
        date.isAfter(today) -> DayClock(0, "24h:00m:00s")
        else -> {
            val start = date.startMillis()
            val elapsed = now - start
            val remaining = DAY_MS - elapsed
            val h = remaining / 3_600_000
            val m = (remaining % 3_600_000) / 60_000
            val s = (remaining % 60_000) / 1000
            DayClock(((elapsed.toDouble() / DAY_MS) * 100).roundToInt(), "${pad2(h)}h:${pad2(m)}m:${pad2(s)}s left")
        }
    }
}

@Composable
fun DayAndTaskBars(date: LocalDate, done: Int, total: Int) {
    val now by rememberNow()
    val clock = dayClock(date, now)
    val pct = if (total > 0) ((done * 100.0) / total).roundToInt() else 0
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Schedule, null, tint = Pal.c.mutedForeground, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text("Day progress", fontSize = 10.sp, color = Pal.c.mutedForeground, modifier = Modifier.weight(1f))
        Text(clock.label, fontSize = 10.sp, color = Pal.c.mutedForeground, style = MonoStyle)
    }
    BarProgress(clock.percent, Brand.timeGradient)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("Tasks · $done/$total completed", fontSize = 10.sp, color = Pal.c.mutedForeground, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("$pct%", fontSize = 10.sp, color = Pal.c.primary, fontWeight = FontWeight.SemiBold, style = MonoStyle)
    }
    BarProgress(pct, Brand.taskGradient)
}

@Composable
fun DailyTaskCard(date: LocalDate, entries: List<DailyEntry>, onOpen: () -> Unit) {
    val isToday = date == today()
    val done = entries.count { it.completed }
    val total = entries.size
    val pct = if (total > 0) ((done * 100.0) / total).roundToInt() else 0
    val title = if (isToday) "Daily Tasks" else "Tasks · ${date.format("EEE, MMM d")}"
    AppCard(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        borderColor = if (total > 0) Pal.c.primary.copy(alpha = .3f) else Pal.c.border,
        onClick = onOpen,
        padding = 0.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                IconTile(Icons.Filled.CalendarMonth, Pal.c.primary, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Pal.c.foreground)
                    Text(date.format("EEEE, MMM d, yyyy"), fontSize = 11.sp, color = Pal.c.mutedForeground)
                }
                if (total > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$pct", style = NumberStyle, fontSize = 18.sp, color = Pal.c.primary)
                            Text("%", fontSize = 12.sp, color = Pal.c.mutedForeground)
                        }
                        Text("$done/$total done", fontSize = 10.sp, color = Pal.c.mutedForeground)
                    }
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Pal.c.mutedForeground, modifier = Modifier.size(18.dp))
            }
            if (total == 0) {
                Text(
                    "No tasks for ${if (isToday) "today" else date.format("MMMM d, yyyy")}",
                    fontSize = 13.sp, color = Pal.c.mutedForeground,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                DayAndTaskBars(date, done, total)
            }
        }
        Box(Modifier.fillMaxWidth().background(Pal.c.border.copy(alpha = .5f)).height(1.dp))
        Text(
            "Tap to view all tasks →", fontSize = 11.sp, color = Pal.c.mutedForeground,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ───────────────────────── Project card ─────────────────────────

@Composable
fun ProjectCard(project: Project, onOpen: () -> Unit) {
    val now by rememberNow()
    val progress = project.progress()
    val timeProgress = project.timeProgress(now)
    val remainingMs = project.deadline?.let { it - now }
    val urgent = remainingMs != null && remainingMs in 1 until DAY_MS
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppCard(
        Modifier.fillMaxWidth(),
        borderColor = if (urgent) Pal.c.destructive.copy(alpha = .6f) else Pal.c.border,
        onClick = onOpen,
    ) {
        if (urgent) {
            Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
                Pill("🔥 URGENT", bg = Pal.c.destructive, fg = Color.White)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).height(40.dp).clip(CircleShape).background(project.color.color()))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.SemiBold, color = Pal.c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                project.target?.let { Text("Target: $it", fontSize = 12.sp, color = Pal.c.mutedForeground) }
                project.deadline?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null, tint = Pal.c.mutedForeground, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(it.format("MMM d, yyyy"), fontSize = 12.sp, color = Pal.c.mutedForeground)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$progress", style = NumberStyle, fontSize = 20.sp, color = project.color.color())
                    Text("%", fontSize = 12.sp, color = Pal.c.mutedForeground)
                }
                Text("${project.tasks.count { it.completed }}/${project.tasks.size} tasks", fontSize = 12.sp, color = Pal.c.mutedForeground)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, "Actions", tint = Pal.c.mutedForeground, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; editing = true })
                    DropdownMenuItem(
                        text = { Text("Delete", color = Pal.c.destructive) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Pal.c.destructive) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        }
        if (timeProgress != null && remainingMs != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val c = if (urgent) Pal.c.destructive else Pal.c.mutedForeground
                Icon(Icons.Filled.Schedule, null, tint = c, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (urgent) "⚠ Urgent" else "Time", fontSize = 10.sp, color = c, modifier = Modifier.weight(1f))
                Text(formatRemaining(remainingMs, ":"), fontSize = 10.sp, color = c, style = MonoStyle)
            }
            BarProgress(timeProgress, if (urgent) Brand.urgentGradient else Brand.timeGradient, height = 10.dp)
        }
        Spacer(Modifier.height(if (timeProgress != null) 8.dp else 12.dp))
        BarProgress(progress, Brand.taskGradient, height = 10.dp)
    }

    if (editing) EditProjectDialog(project) { editing = false }
    if (confirmDelete) ConfirmDeleteDialog("Delete \"${project.name}\"?", "This removes the project and all its tasks.", { confirmDelete = false }) {
        AppStore.deleteProject(project.id)
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Delete", color = Pal.c.destructive) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ───────────────────────── Project dialogs ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddProjectDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf<LocalDate?>(null) }
    var color by remember { mutableStateOf(ProjectColor.GREEN) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Project", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Project Name") }, placeholder = { Text("e.g. Website Redesign") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(target, { target = it }, label = { Text("Target (optional)") }, placeholder = { Text("e.g. Launch Q2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChipButton(deadline?.format("MMMM d, yyyy") ?: "Pick a deadline (optional)", Icons.Filled.CalendarToday, deadline != null) { pickDate = true }
                Text("Color", fontSize = 13.sp, color = Pal.c.foreground)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProjectColor.entries.forEach { c ->
                        val sel = c == color
                        Box(
                            Modifier
                                .size(if (sel) 36.dp else 32.dp)
                                .clip(CircleShape)
                                .background(c.color().copy(alpha = if (sel) 1f else .6f))
                                .then(if (sel) Modifier.border(2.dp, Pal.c.foreground, CircleShape) else Modifier)
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    AppStore.addProject(name.trim(), color, target.trim().ifBlank { null }, deadline?.atTime(23, 59, 59)?.millis())
                    onDismiss()
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickDate) DatePickerModal(deadline, { pickDate = false }) { deadline = it }
}

@Composable
fun EditProjectDialog(project: Project, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(project.name) }
    var target by remember { mutableStateOf(project.target ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true)
                OutlinedTextField(target, { target = it }, label = { Text("Target (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) AppStore.editProject(project.id, name.trim(), target.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ───────────────────────── Task row ─────────────────────────

@Composable
fun DeadlineCountdown(task: Task) {
    val now by rememberNow()
    val deadline = task.deadline ?: return
    val total = deadline - task.createdAt
    val remaining = (deadline - now).coerceAtLeast(0)
    val fraction = if (total > 0) remaining.toFloat() / total else 0f
    val overdue = remaining == 0L
    val secs = remaining / 1000
    val label = when {
        overdue -> "!"
        secs >= 3600 -> "${secs / 3600}h"
        secs >= 60 -> "${secs / 60}m"
        else -> "${secs}s"
    }
    val color = when {
        overdue -> Pal.c.destructive
        fraction > .5f -> Pal.c.primary
        fraction > .2f -> Brand.amber
        else -> Pal.c.destructive
    }
    Ring(fraction, color, 40.dp, 3.5.dp) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (overdue) Pal.c.destructive else Pal.c.mutedForeground)
    }
}

@Composable
fun TaskRow(
    task: Task,
    completed: Boolean,
    color: Color,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: ((Task) -> Unit)?,
    badge: String? = null,
) {
    var editing by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val overdue = task.deadline != null && !completed && task.deadline < now
    val recurring = task.repeat != Repeat.NONE

    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (completed) color else Color.Transparent)
                .border(2.dp, if (completed) color else Pal.c.border, CircleShape)
                .clickable {
                    if (completed) Sounds.uncheck() else Sounds.check()
                    onToggle()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (completed) Icon(Icons.Filled.Check, "Done", tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(enabled = onEdit != null) { editing = true }) {
            Text(
                task.title, fontSize = 14.sp,
                color = if (completed) Pal.c.mutedForeground else Pal.c.foreground,
                textDecoration = if (completed) TextDecoration.LineThrough else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                if (recurring && task.dueTime != null) {
                    Pill(formatTime12(task.dueTime), bg = Color.Transparent, icon = Icons.Filled.Schedule)
                }
                if (task.deadline != null && !recurring) {
                    Pill(
                        task.deadline.format("MMM d, hh:mm a") + if (overdue) " · Overdue" else "",
                        bg = Color.Transparent, fg = if (overdue) Pal.c.destructive else Pal.c.mutedForeground, icon = Icons.Filled.Schedule,
                    )
                }
                if (recurring) {
                    Pill(
                        if (task.repeat == Repeat.CUSTOM && task.repeatDays != null) task.repeatDays.joinToString(", ") else task.repeat.label,
                        icon = Icons.Filled.Repeat,
                    )
                }
                if (badge != null) Pill(badge)
            }
        }
        if (task.deadline != null && !completed && !recurring) {
            DeadlineCountdown(task)
            Spacer(Modifier.width(4.dp))
        }
        if (onEdit != null) {
            IconButton(onClick = { editing = true }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Edit, "Edit", tint = Pal.c.mutedForeground, modifier = Modifier.size(16.dp))
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Delete, "Delete", tint = Pal.c.mutedForeground, modifier = Modifier.size(17.dp))
        }
    }
    if (editing && onEdit != null) TaskEditDialog(task, { editing = false }, onEdit)
}

/** Edit title, repeat, custom days, and due time (recurring) or deadline date+time (one-off). */
@Composable
fun TaskEditDialog(task: Task, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title by remember { mutableStateOf(task.title) }
    var repeat by remember { mutableStateOf(task.repeat) }
    var days by remember { mutableStateOf(task.repeatDays ?: emptyList()) }
    var dueTime by remember { mutableStateOf(parseTime(task.dueTime)) }
    var date by remember { mutableStateOf(task.deadline?.toLocalDate()) }
    var time by remember { mutableStateOf(task.deadline?.toLocalDateTime()?.toLocalTime()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Task title") }, singleLine = true)
                RepeatSelector(repeat, Repeat.entries, Icons.Filled.Repeat) { repeat = it }
                if (repeat == Repeat.CUSTOM) DayChips(days) { d -> days = if (d in days) days - d else days + d }
                if (repeat != Repeat.NONE) {
                    ChipButton(dueTime?.let { formatTime12(it.toString()) } ?: "Due time", Icons.Filled.Schedule, dueTime != null) { pickDue = true }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipButton(date?.format("MMM d") ?: "Date", Icons.Filled.CalendarToday, date != null) { pickDate = true }
                        ChipButton(time?.let { formatTime12(it.toString()) } ?: "Time", Icons.Filled.Schedule, time != null) { pickTime = true }
                        if (date != null) TextButton(onClick = { date = null; time = null }) { Text("Clear", fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val recurring = repeat != Repeat.NONE
                val deadline = if (recurring) {
                    // "Once" keeps its date; other recurring tasks have no deadline.
                    if (repeat == Repeat.ONCE) task.deadline else null
                } else {
                    date?.let { d -> d.atTime(time ?: LocalTime.of(23, 59, 59)).millis() }
                }
                onSave(
                    task.copy(
                        title = title.trim().ifBlank { task.title },
                        repeat = repeat,
                        repeatDays = if (repeat == Repeat.CUSTOM) days.takeIf { it.isNotEmpty() } else null,
                        dueTime = if (recurring) dueTime?.toString() else null,
                        deadline = deadline,
                    )
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickDate) DatePickerModal(date, { pickDate = false }) { date = it }
    if (pickTime) TimePickerModal(time, { pickTime = false }) { time = it }
    if (pickDue) TimePickerModal(dueTime, { pickDue = false }) { dueTime = it }
}
