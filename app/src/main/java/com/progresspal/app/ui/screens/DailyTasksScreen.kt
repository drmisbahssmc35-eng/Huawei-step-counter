package com.progresspal.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.data.AppData
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.DailyEntry
import com.progresspal.app.data.Repeat
import com.progresspal.app.data.Task
import com.progresspal.app.data.activeProjects
import com.progresspal.app.data.dailyTasksFor
import com.progresspal.app.data.format
import com.progresspal.app.data.formatTime12
import com.progresspal.app.data.today
import com.progresspal.app.ui.components.AppCard
import com.progresspal.app.ui.components.ChipButton
import com.progresspal.app.ui.components.DayAndTaskBars
import com.progresspal.app.ui.components.DayChips
import com.progresspal.app.ui.components.Pill
import com.progresspal.app.ui.components.ProjectCard
import com.progresspal.app.ui.components.RepeatSelector
import com.progresspal.app.ui.components.ScreenHeader
import com.progresspal.app.ui.components.TaskRow
import com.progresspal.app.ui.components.TimePickerModal
import com.progresspal.app.ui.components.WeekStrip
import com.progresspal.app.ui.theme.NumberStyle
import com.progresspal.app.ui.theme.Pal
import com.progresspal.app.ui.theme.color
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyTasksScreen(data: AppData, initialDate: LocalDate, onBack: () -> Unit, onOpenProject: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(initialDate) }
    val today = today()
    val isToday = selected == today
    val isFuture = selected.isAfter(today)
    val entries = dailyTasksFor(data, selected)
    val done = entries.count { it.completed }
    val total = entries.size
    val pct = if (total > 0) ((done * 100.0) / total).roundToInt() else 0

    var title by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf(Repeat.DAILY) }
    var dueTime by remember { mutableStateOf(LocalTime.NOON) }
    var days by remember { mutableStateOf(listOf<String>()) }
    var pickTime by remember { mutableStateOf(false) }
    var editChoice by remember { mutableStateOf<Pair<DailyEntry, Task>?>(null) }
    var deleteChoice by remember { mutableStateOf<DailyEntry?>(null) }

    fun submit() {
        if (title.isBlank()) return
        val effective = if (isFuture) Repeat.ONCE else repeat
        if (effective == Repeat.CUSTOM && days.isEmpty()) return
        val deadline = if (effective == Repeat.ONCE) selected.atTime(dueTime).atZone(com.progresspal.app.data.zone()).toInstant().toEpochMilli() else null
        AppStore.addTask(
            AppStore.dailyProjectId(), title.trim(), deadline, effective, dueTime.toString(),
            if (effective == Repeat.CUSTOM) days else null,
        )
        title = ""; dueTime = LocalTime.NOON; days = emptyList()
    }

    Screen {
        ScreenHeader("Daily Tasks", selected.format("EEEE, MMMM d, yyyy"), Icons.Filled.CalendarMonth, Pal.c.primary, onBack)
        WeekStrip(selected) { selected = it }

        AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Progress", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Pal.c.foreground, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$pct", style = NumberStyle, fontSize = 24.sp, color = Pal.c.primary)
                        Text("%", fontSize = 14.sp, color = Pal.c.mutedForeground)
                    }
                    Text("$done/$total done", fontSize = 10.sp, color = Pal.c.mutedForeground)
                }
            }
            DayAndTaskBars(selected, done, total)
        }

        if (isToday || isFuture) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        title, { title = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text(if (isFuture) "Add a task for this date..." else "Add a repeating task...") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { submit() }, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Filled.Add, "Add task") }
                }
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isToday) {
                        RepeatSelector(repeat, listOf(Repeat.ONCE, Repeat.DAILY, Repeat.WEEKLY, Repeat.MONTHLY, Repeat.CUSTOM), Icons.Filled.Repeat) {
                            repeat = it
                            if (it != Repeat.CUSTOM) days = emptyList()
                        }
                    } else {
                        Pill("Once", icon = Icons.Filled.EventAvailable, fontSize = 12.sp)
                    }
                    ChipButton(formatTime12(dueTime.toString()), Icons.Filled.Schedule) { pickTime = true }
                }
                if (isToday && repeat == Repeat.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    DayChips(days) { d -> days = if (d in days) days - d else days + d }
                }
            }
        }

        AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("TASKS", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Pal.c.mutedForeground, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                if (!isToday) Text("$total task${if (total != 1) "s" else ""} scheduled", fontSize = 10.sp, color = Pal.c.mutedForeground)
            }
            if (entries.isEmpty()) {
                Text(
                    "No tasks for ${if (isToday) "today" else selected.format("MMMM d, yyyy")}",
                    fontSize = 14.sp, color = Pal.c.mutedForeground, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
            entries.forEachIndexed { i, e ->
                TaskRow(
                    task = e.task, completed = e.completed, color = e.project.color.color(),
                    badge = e.project.name,
                    onToggle = {
                        if (e.isRecurring) AppStore.toggleDateTask(e.task.id, selected) else AppStore.toggleTask(e.project.id, e.task.id)
                        syncChallenge(e.task.id, !e.completed)
                    },
                    onDelete = {
                        if (e.isRecurring) deleteChoice = e else AppStore.deleteTask(e.project.id, e.task.id)
                    },
                    onEdit = { updated ->
                        if (e.isRecurring) editChoice = e to updated else AppStore.editTask(e.project.id, e.task.id) { updated }
                    },
                )
                if (i < entries.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(Pal.c.border.copy(alpha = .5f)))
            }
        }

        if (!isToday) {
            val active = activeProjects(data.projects)
            if (active.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    active.forEach { p -> ProjectCard(p) { onOpenProject(p.id) } }
                }
            }
        }
    }

    if (pickTime) TimePickerModal(dueTime, { pickTime = false }) { dueTime = it }

    editChoice?.let { (e, updated) ->
        ChoiceDialog(
            title = "Edit Repeating Task",
            onDismiss = { editChoice = null },
            first = Choice(Icons.Filled.EventAvailable, Pal.c.primary, "Edit only for this day", "Creates a one-time override for ${selected.format("MMM d")}") {
                AppStore.duplicateTaskForDate(e.project.id, e.task.id, selected, updated.title, updated.dueTime)
            },
            second = Choice(Icons.Filled.Repeat, Pal.c.foreground, "Edit this & future", "Changes apply to all future occurrences") {
                AppStore.editTask(e.project.id, e.task.id) { updated }
            },
        )
    }
    deleteChoice?.let { e ->
        ChoiceDialog(
            title = "Delete Repeating Task",
            onDismiss = { deleteChoice = null },
            first = Choice(Icons.Filled.EventAvailable, Pal.c.primary, "Delete only for this day", "Hides this task on ${selected.format("MMM d")} only") {
                AppStore.duplicateTaskForDate(e.project.id, e.task.id, selected, null, null, hidden = true)
            },
            second = Choice(Icons.Filled.Delete, Pal.c.destructive, "Delete this & future", "Permanently removes this recurring task") {
                AppStore.deleteTask(e.project.id, e.task.id)
            },
        )
    }
}

/** Keeps an imported task in the running challenge in sync when it's ticked from the daily list. */
private fun syncChallenge(taskId: String, completed: Boolean) {
    AppStore.data.value.challenges
        .filter { it.status == com.progresspal.app.data.ChallengeStatus.ACTIVE && it.tasks.any { t -> t.sourceTaskId == taskId } }
        .forEach { c ->
            AppStore.updateChallenge(c.id) { ch ->
                ch.copy(tasks = ch.tasks.map { if (it.sourceTaskId == taskId) it.copy(completed = completed) else it })
            }
        }
}

data class Choice(val icon: ImageVector, val tint: Color, val title: String, val subtitle: String, val action: () -> Unit)

@Composable
fun ChoiceDialog(title: String, onDismiss: () -> Unit, first: Choice, second: Choice) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(first, second).forEach { c ->
                    AppCard(Modifier.fillMaxWidth(), onClick = { c.action(); onDismiss() }, padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(c.icon, null, tint = c.tint, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(c.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (c.tint == Pal.c.destructive) c.tint else Pal.c.foreground)
                                Text(c.subtitle, fontSize = 11.sp, color = Pal.c.mutedForeground)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
