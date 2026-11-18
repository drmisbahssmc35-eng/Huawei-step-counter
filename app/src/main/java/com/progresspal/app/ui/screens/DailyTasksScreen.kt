package com.progresspal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.combinedClickable
import com.progresspal.app.ui.components.toast
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
    var pickActivity by remember { mutableStateOf(false) }
    var barMenu by remember { mutableStateOf<DailyEntry?>(null) }
    var goalFor by remember { mutableStateOf<com.progresspal.app.data.SttActivity?>(null) }
    val sttCache by com.progresspal.app.data.SimpleTimeTracker.cache.collectAsStateWithLifecycle()

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
        com.progresspal.app.data.SimpleTimeTracker.syncSoon(1) // pair it with its activity
    }

    Screen {
        ScreenHeader("Daily Tasks", selected.format("EEEE, MMMM d, yyyy"), Icons.Filled.CalendarMonth, Pal.c.primary, onBack)
        WeekStrip(selected) { selected = it }
        AutoAddedCleanup(data)

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
                        placeholder = { Text(if (isFuture) "Type a task, or tap + for activities" else "Type a task, or tap + for activities") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        // Empty box → choose from the Activity List; typed name → add it as before.
                        onClick = { if (title.isBlank()) pickActivity = true else submit() },
                        shape = RoundedCornerShape(10.dp),
                    ) { Icon(Icons.Filled.Add, "Add task") }
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
            // Drag the ≡ handle to reorder; the order is remembered.
            val order by com.progresspal.app.data.DailyOrder.ids.collectAsStateWithLifecycle()
            val ordered = remember(entries, order) { com.progresspal.app.data.DailyOrder.sort(entries) { it.task.id } }
            val latest by androidx.compose.runtime.rememberUpdatedState(ordered)
            var dragId by remember { mutableStateOf<String?>(null) }
            var dragDy by remember { mutableFloatStateOf(0f) }
            val heights = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
            // Where the dragged task would land, from how far it has moved.
            fun target(): Int {
                val list = latest
                val from = list.indexOfFirst { it.task.id == dragId }
                if (from < 0) return -1
                var to = from; var dy = dragDy
                while (dy > 0 && to < list.lastIndex) {
                    val h = heights[list[to + 1].task.id] ?: break
                    if (dy < h / 2f) break
                    dy -= h; to++
                }
                while (dy < 0 && to > 0) {
                    val h = heights[list[to - 1].task.id] ?: break
                    if (-dy < h / 2f) break
                    dy += h; to--
                }
                return to
            }
            val dragFrom = ordered.indexOfFirst { it.task.id == dragId }
            val dragTo = if (dragId != null) target() else -1
            val dragH = dragId?.let { heights[it] } ?: 0

            ordered.forEachIndexed { i, e ->
                val toggle: () -> Unit = {
                    if (e.isRecurring) AppStore.toggleDateTask(e.task.id, selected) else AppStore.toggleTask(e.project.id, e.task.id)
                    syncChallenge(e.task.id, !e.completed)
                }
                val delete: () -> Unit = { if (e.isRecurring) deleteChoice = e else AppStore.deleteTask(e.project.id, e.task.id) }
                val edit: (Task) -> Unit = { updated ->
                    if (e.isRecurring) editChoice = e to updated else AppStore.editTask(e.project.id, e.task.id) { updated }
                }
                val goal = com.progresspal.app.data.SimpleTimeTracker.goalFor(e.task.id, sttCache)
                val act = goal?.let { g -> sttCache.activities.firstOrNull { it.id == g.activityId } }
                val dragging = e.task.id == dragId
                // Others slide out of the way while one is dragged.
                val shift = when {
                    dragId == null || dragging -> 0f
                    dragFrom < dragTo && i in (dragFrom + 1)..dragTo -> -dragH.toFloat()
                    dragFrom > dragTo && i in dragTo until dragFrom -> dragH.toFloat()
                    else -> 0f
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragDy else shift
                            shadowElevation = if (dragging) 16f else 0f
                        }
                        .onGloballyPositioned { heights[e.task.id] = it.size.height },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.DragHandle, "Drag to reorder",
                        tint = if (dragging) Pal.c.primary else Pal.c.mutedForeground,
                        modifier = Modifier
                            .size(28.dp)
                            .pointerInput(e.task.id) {
                                detectDragGestures(
                                    onDragStart = { dragId = e.task.id; dragDy = 0f },
                                    onDragEnd = {
                                        val list = latest
                                        val from = list.indexOfFirst { it.task.id == dragId }
                                        val to = target()
                                        if (from >= 0 && to >= 0 && from != to) {
                                            val ids = list.map { it.task.id }.toMutableList()
                                            ids.add(to, ids.removeAt(from))
                                            com.progresspal.app.data.DailyOrder.save(ids)
                                        }
                                        dragId = null; dragDy = 0f
                                    },
                                    onDragCancel = { dragId = null; dragDy = 0f },
                                ) { change, amount -> change.consume(); dragDy += amount.y }
                            },
                    )
                    Column(Modifier.weight(1f)) {
                        if (goal != null && act != null) {
                            // Simple Time Tracker-style bar that fills with tracked time towards the goal.
                            GoalBar(e.task, act, goal, selected, e.completed, sttCache, onToggleDone = toggle, onEdit = edit, onLongPress = { barMenu = e })
                            Spacer(Modifier.height(6.dp))
                        } else {
                            TaskRow(
                                task = e.task, completed = e.completed, color = e.project.color.color(),
                                badge = e.project.name, onToggle = toggle, onDelete = delete, onEdit = edit,
                            )
                            if (i < ordered.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(Pal.c.border.copy(alpha = .5f)))
                        }
                    }
                }
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

    if (pickActivity) ActivityPicker(
        activities = sttCache.activities,
        selectedId = null,
        onPick = { goalFor = it; pickActivity = false },
        onDismiss = { pickActivity = false },
    )
    goalFor?.let { a ->
        AddFromActivityDialog(a, selected, isFuture, dueTime, onChange = { pickActivity = true; goalFor = null }) { goalFor = null }
    }
    LaunchedEffect(Unit) {
        while (true) { com.progresspal.app.data.SimpleTimeTracker.syncSoon(1); kotlinx.coroutines.delay(30_000) }
    }
    barMenu?.let { e ->
        val g = com.progresspal.app.data.SimpleTimeTracker.goalFor(e.task.id, sttCache)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { barMenu = null },
            containerColor = Pal.c.card,
            title = { Text(e.task.title, color = Pal.c.foreground) },
            text = {
                Column {
                    Text("Daily goal", fontSize = 13.sp, color = Pal.c.mutedForeground, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    val fromStt = g?.let { com.progresspal.app.data.SimpleTimeTracker.sttGoalMinutes(it.activityId, sttCache) }
                    if (fromStt != null) Text(
                        "${fmtMinutes(fromStt)} — from Simple Time Tracker (change it there)",
                        color = Pal.c.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 20, 30, 45, 60, 90, 120).forEach { m ->
                            GoalChip(if (m < 60) "${m}m" else if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m", g?.minutes == m) {
                                if (g != null) com.progresspal.app.data.SimpleTimeTracker.setTaskGoal(e.task.id, g.activityId, m)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (e.isRecurring) deleteChoice = e else AppStore.deleteTask(e.project.id, e.task.id)
                    barMenu = null
                }) { Text("Delete task", color = Pal.c.destructive) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { barMenu = null }) { Text("Close") } },
        )
    }
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

/** Simple Time Tracker activities that are not a task yet: tap one to fill in the task name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SttSuggestions(data: AppData, typed: String, onPick: (String) -> Unit) {
    val stt by com.progresspal.app.data.SimpleTimeTracker.cache.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }
    val all = remember(stt.activities, data) { com.progresspal.app.data.SimpleTimeTracker.suggestions() }
    if (all.isEmpty()) return
    val q = typed.trim()
    val list = if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) }
    if (list.isEmpty()) return
    val shown = if (showAll || q.isNotEmpty()) list else list.take(10)
    Spacer(Modifier.height(10.dp))
    Text("Suggestions from Simple Time Tracker", fontSize = 12.sp, color = Pal.c.mutedForeground, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { a ->
            val c = androidx.compose.ui.graphics.Color(a.color.toInt())
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(c.copy(alpha = .15f))
                    .border(1.dp, c.copy(alpha = .5f), RoundedCornerShape(50))
                    .clickable { onPick(a.name) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(c, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(a.name, fontSize = 13.sp, color = Pal.c.foreground, maxLines = 1)
            }
        }
        if (!showAll && q.isEmpty() && list.size > shown.size) {
            Text(
                "+${list.size - shown.size} more",
                Modifier.clip(RoundedCornerShape(50)).background(Pal.c.muted).clickable { showAll = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                fontSize = 13.sp, color = Pal.c.primary,
            )
        }
    }
}

/** One-time offer to remove the Daily Tasks an earlier version added from Simple Time Tracker. */
@Composable
private fun AutoAddedCleanup(data: AppData) {
    val stt by com.progresspal.app.data.SimpleTimeTracker.cache.collectAsStateWithLifecycle()
    val found = remember(stt, data) { com.progresspal.app.data.SimpleTimeTracker.autoAddedTasks() }
    if (found.isEmpty()) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var review by remember { mutableStateOf(false) }
    var keep by remember(found) { mutableStateOf(found.map { it.second.id }.toSet()) }

    AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            "${found.size} tasks were added here automatically from Simple Time Tracker by the earlier version.",
            fontSize = 14.sp, color = Pal.c.foreground,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = { review = true }, shape = RoundedCornerShape(10.dp)) { Text("Review & remove") }
            androidx.compose.material3.TextButton(onClick = { com.progresspal.app.data.SimpleTimeTracker.keepAutoAdded() }) { Text("Keep them") }
        }
    }

    if (review) androidx.compose.material3.AlertDialog(
        onDismissRequest = { review = false },
        containerColor = Pal.c.card,
        title = { Text("Remove auto-added tasks", color = Pal.c.foreground) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("Untick any you want to keep. They will still show as suggestions afterwards.", fontSize = 13.sp, color = Pal.c.mutedForeground)
                Spacer(Modifier.height(8.dp))
                found.forEach { (_, t) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { keep = if (t.id in keep) keep - t.id else keep + t.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = t.id in keep, onCheckedChange = { keep = if (it) keep + t.id else keep - t.id })
                        Text(t.title, color = Pal.c.foreground, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val chosen = found.filter { it.second.id in keep }
                com.progresspal.app.data.SimpleTimeTracker.removeAutoAdded(chosen)
                toast(ctx, "Removed ${chosen.size} task${if (chosen.size == 1) "" else "s"}")
                review = false
            }) { Text("Remove ${keep.size}", color = Pal.c.destructive) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { review = false }) { Text("Cancel") } },
    )
}

/** After picking an activity: daily goal + repeat, then the task is added and tied to the activity. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFromActivityDialog(
    a: com.progresspal.app.data.SttActivity,
    date: LocalDate,
    isFuture: Boolean,
    dueTime: LocalTime,
    onChange: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sttGoal = com.progresspal.app.data.SimpleTimeTracker.sttGoalMinutes(a.id)
    var goal by remember { mutableStateOf(sttGoal ?: 10) }
    var repeat by remember { mutableStateOf(if (isFuture) Repeat.ONCE else Repeat.DAILY) }
    var days by remember { mutableStateOf(listOf<String>()) }
    val goals = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.c.card,
        title = { Text("Add daily task", color = Pal.c.foreground) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                com.progresspal.app.ui.components.SttActivityRow(a, onClick = onChange) {
                    Text("Change", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .85f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text("Daily goal — done automatically when tracked this long", fontSize = 13.sp, color = Pal.c.mutedForeground, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (sttGoal != null) Text(
                    "${fmtMinutes(sttGoal)} — from Simple Time Tracker (change it there)",
                    color = Pal.c.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    goals.forEach { m -> GoalChip(if (m < 60) "${m}m" else if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m", goal == m) { goal = m } }
                    GoalChip("No goal", goal == 0) { goal = 0 }
                }
                Spacer(Modifier.height(14.dp))
                Text("Repeat", fontSize = 13.sp, color = Pal.c.mutedForeground, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (isFuture) Text("Once · ${date.format("EEE, d MMM")}", color = Pal.c.foreground)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Repeat.DAILY, Repeat.WEEKLY, Repeat.MONTHLY, Repeat.CUSTOM, Repeat.ONCE).forEach { r ->
                        GoalChip(if (r == Repeat.ONCE) "Today only" else r.label, repeat == r) { repeat = r }
                    }
                }
                if (repeat == Repeat.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    DayChips(days) { d -> days = if (d in days) days - d else days + d }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val r = if (repeat == Repeat.CUSTOM && days.isEmpty()) Repeat.DAILY else repeat
                val deadline = if (r == Repeat.ONCE) date.atTime(dueTime).atZone(com.progresspal.app.data.zone()).toInstant().toEpochMilli() else null
                val id = AppStore.addDailyTaskWith(a.name, r, deadline, dueTime.toString(), if (r == Repeat.CUSTOM) days else null)
                com.progresspal.app.data.SimpleTimeTracker.setTaskGoal(id, a.id, goal)
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GoalChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Pal.c.primary
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) c else c.copy(alpha = .12f))
            .border(1.dp, c.copy(alpha = if (selected) 1f else .4f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (selected) androidx.compose.ui.graphics.Color.White else Pal.c.foreground, fontSize = 14.sp,
    )
}

/** "⏱ 6m / 10m" with a bar under a task that has an activity goal. */
@Composable
private fun GoalProgress(g: com.progresspal.app.data.TaskGoal, date: LocalDate, completed: Boolean, stt: com.progresspal.app.data.SttCache) {
    val now by com.progresspal.app.ui.components.rememberNow()
    val ms = com.progresspal.app.data.SimpleTimeTracker.trackedMs(g.activityId, date, stt, now)
    val a = stt.activities.firstOrNull { it.id == g.activityId }
    val color = a?.let { androidx.compose.ui.graphics.Color(it.color.toInt()) } ?: Pal.c.primary
    val mins = (ms / 60_000).toInt()
    val frac = if (g.minutes <= 0) 0f else (ms.toFloat() / (g.minutes * 60_000f)).coerceIn(0f, 1f)
    val running = stt.running.any { it.id == g.activityId }
    Column(Modifier.fillMaxWidth().padding(start = 44.dp, end = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "⏱ " + (if (running) "▶ " else "") + "${mins}m" + (if (g.minutes > 0) " / ${g.minutes}m" else " tracked") +
                    (a?.let { "  ·  ${it.name}" } ?: ""),
                fontSize = 11.sp, color = Pal.c.mutedForeground, modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (!completed && g.minutes > 0 && frac < 1f) Text("tick ✓ to finish early", fontSize = 10.sp, color = Pal.c.mutedForeground)
        }
        if (g.minutes > 0) Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Pal.c.muted)) {
            Box(Modifier.fillMaxWidth(if (completed) 1f else frac).height(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

/**
 * A Daily Task tied to an activity, drawn like Simple Time Tracker's list: the bar starts empty
 * and fills with the activity's colour as time is tracked (here, on the timeline or in Simple
 * Time Tracker). Tap = start / stop the timer (today), circle = tick done by hand, hold = options.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GoalBar(
    task: Task,
    a: com.progresspal.app.data.SttActivity,
    g: com.progresspal.app.data.TaskGoal,
    date: LocalDate,
    completed: Boolean,
    stt: com.progresspal.app.data.SttCache,
    onToggleDone: () -> Unit,
    onEdit: (Task) -> Unit,
    onLongPress: () -> Unit,
) {
    val title = task.title
    var editing by remember { mutableStateOf(false) }
    if (editing) com.progresspal.app.ui.components.TaskEditDialog(task, { editing = false }) { onEdit(it); editing = false }
    val now by com.progresspal.app.ui.components.rememberNow()
    val ms = com.progresspal.app.data.SimpleTimeTracker.trackedMs(g.activityId, date, stt, now)
    val goalMs = (g.minutes.coerceAtLeast(1)) * 60_000L
    val frac = if (completed) 1f else (ms.toFloat() / goalMs).coerceIn(0f, 1f)
    val pct = ((ms * 100) / goalMs).toInt()
    val running = stt.running.any { it.id == a.id }
    val color = androidx.compose.ui.graphics.Color(a.color.toInt())
    val white = androidx.compose.ui.graphics.Color.White
    val shape = RoundedCornerShape(8.dp)
    val isToday = date == LocalDate.now()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.22f))
            .border(if (running) 3.dp else 1.dp, if (running) white else color.copy(alpha = .6f), shape)
            .combinedClickable(
                onClick = {
                    if (isToday) com.progresspal.app.data.SimpleTimeTracker.tap(a)
                    else com.progresspal.app.ui.components.toast(ctx, "Timers can be started for today only")
                },
                onLongClick = onLongPress,
            ),
    ) {
        // Filled part = tracked time / goal.
        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(color))
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            com.progresspal.app.ui.components.SttIcon(a.icon, a.name, white, 32.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = white, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    (if (running) "▶ " else "") + "${fmtMin(ms)} / ${fmtMinutes(g.minutes)} · ${if (completed) "Done" else "${pct.coerceAtMost(999)}%"}",
                    color = white.copy(alpha = .9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
            }
            androidx.compose.material3.IconButton(onClick = { editing = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Edit, "Edit", tint = white, modifier = Modifier.size(18.dp))
            }
            // Tick by hand (e.g. finished in 5 of 10 minutes).
            Box(
                Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (completed) white else white.copy(alpha = .15f))
                    .border(2.dp, white, androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onToggleDone),
                contentAlignment = Alignment.Center,
            ) { if (completed) Text("✓", color = color, fontWeight = FontWeight.Black, fontSize = 16.sp) }
        }
    }
}

private fun fmtMin(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "${s / 3600}h ${(s % 3600) / 60}m" else if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
}
