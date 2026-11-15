package com.progresspal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.data.AppData
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.Project
import com.progresspal.app.data.Repeat
import com.progresspal.app.data.format
import com.progresspal.app.data.formatRemaining
import com.progresspal.app.data.formatTime12
import com.progresspal.app.data.millis
import com.progresspal.app.data.progress
import com.progresspal.app.data.timeProgress
import com.progresspal.app.data.toLocalDateTime
import com.progresspal.app.data.DAY_MS
import com.progresspal.app.ui.components.AppCard
import com.progresspal.app.ui.components.BarProgress
import com.progresspal.app.ui.components.ChipButton
import com.progresspal.app.ui.components.CircularProgress
import com.progresspal.app.ui.components.ConfirmDeleteDialog
import com.progresspal.app.ui.components.DatePickerModal
import com.progresspal.app.ui.components.EditProjectDialog
import com.progresspal.app.ui.components.IconTile
import com.progresspal.app.ui.components.Pill
import com.progresspal.app.ui.components.RepeatSelector
import com.progresspal.app.ui.components.ScreenHeader
import com.progresspal.app.ui.components.StatBox
import com.progresspal.app.ui.components.TaskRow
import com.progresspal.app.ui.components.TimePickerModal
import com.progresspal.app.ui.components.rememberNow
import com.progresspal.app.ui.components.toast
import com.progresspal.app.ui.theme.Brand
import com.progresspal.app.ui.theme.MonoStyle
import com.progresspal.app.ui.theme.NumberStyle
import com.progresspal.app.ui.theme.Pal
import com.progresspal.app.ui.theme.color
import java.time.LocalDate
import java.time.LocalTime

/** Scrollable, centred page container used by every screen. */
@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Pal.c.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 32.dp),
            content = content,
        )
    }
}

@Composable
fun ProjectDetailScreen(data: AppData, projectId: String, onBack: () -> Unit) {
    val project = data.projects.find { it.id == projectId }
    if (project == null) {
        Screen {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Project not found", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Pal.c.foreground)
                TextButton(onClick = onBack) { Text("Go back") }
            }
        }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val color = project.color.color()
    val pending = project.tasks.filter { !it.completed }
    val completed = project.tasks.filter { it.completed }

    Screen {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.c.foreground) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Edit project", tint = Pal.c.mutedForeground) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete project", tint = Pal.c.mutedForeground) }
        }

        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(project.name, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Pal.c.foreground, textAlign = TextAlign.Center)
            project.target?.let { Text("Target: $it", fontSize = 14.sp, color = Pal.c.mutedForeground) }
            project.deadline?.let {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.Schedule, null, tint = Pal.c.mutedForeground, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Deadline: ${it.format("MMM d, yyyy")}", fontSize = 14.sp, color = Pal.c.mutedForeground)
                }
            }
        }

        if (project.deadline != null) TimeRemainingCard(project)

        Box(Modifier.fillMaxWidth().padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
            CircularProgress(project.progress(), color)
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBox("${project.tasks.size}", "Total", color, Modifier.weight(1f))
            StatBox("${completed.size}", "Done", Pal.c.primary, Modifier.weight(1f))
            StatBox("${pending.size}", "Pending", Pal.c.foreground, Modifier.weight(1f))
        }

        AddTaskForm(project)

        AppCard(Modifier.fillMaxWidth(), padding = 8.dp) {
            if (project.tasks.isEmpty()) {
                Text(
                    "No tasks yet. Add one above!", fontSize = 14.sp, color = Pal.c.mutedForeground,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
            (pending + completed).forEachIndexed { i, t ->
                if (i == pending.size && pending.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Pal.c.border))
                }
                TaskRow(
                    task = t, completed = t.completed, color = color,
                    onToggle = { AppStore.toggleTask(project.id, t.id) },
                    onDelete = { AppStore.deleteTask(project.id, t.id) },
                    onEdit = { updated -> AppStore.editTask(project.id, t.id) { updated } },
                )
            }
        }
    }

    if (editing) EditProjectDialog(project) { editing = false }
    if (confirmDelete) ConfirmDeleteDialog("Delete \"${project.name}\"?", "This removes the project and all its tasks.", { confirmDelete = false }) {
        AppStore.deleteProject(project.id)
        onBack()
    }
}

@Composable
private fun TimeRemainingCard(project: Project) {
    val now by rememberNow()
    val deadline = project.deadline ?: return
    val ms = (deadline - now).coerceAtLeast(0)
    val urgent = ms in 1 until DAY_MS
    val timeProgress = project.timeProgress(now) ?: 0
    val progress = project.progress()
    AppCard(
        Modifier.fillMaxWidth().padding(bottom = 24.dp),
        borderColor = if (urgent) Pal.c.destructive.copy(alpha = .4f) else Pal.c.border,
        background = if (urgent) Pal.c.destructive.copy(alpha = .1f) else Pal.c.card,
        borderWidth = if (urgent) 2.dp else 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val c = if (urgent) Pal.c.destructive else Pal.c.mutedForeground
            Icon(Icons.Filled.Schedule, null, tint = c, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (urgent) "⚠ Urgent — Less than 24h remaining!" else "Time Remaining", fontSize = 12.sp, color = c, modifier = Modifier.weight(1f))
            Text(
                "$timeProgress%", style = NumberStyle, fontSize = 14.sp,
                color = if (urgent || timeProgress > progress) Pal.c.destructive else project.color.color(),
            )
        }
        Text(
            formatRemaining(ms), style = MonoStyle, fontSize = 18.sp,
            color = if (urgent) Pal.c.destructive else Pal.c.foreground,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), textAlign = TextAlign.Center,
        )
        BarProgress(timeProgress, if (urgent) Brand.urgentGradient else Brand.timeGradient, height = 12.dp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(project.createdAt.format("MMM d"), fontSize = 10.sp, color = Pal.c.mutedForeground, modifier = Modifier.weight(1f))
            Text(deadline.format("MMM d"), fontSize = 10.sp, color = if (urgent) Pal.c.destructive else Pal.c.mutedForeground)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTaskForm(project: Project) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf(LocalTime.NOON) }
    var repeat by remember { mutableStateOf(Repeat.NONE) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    fun submit() {
        if (title.isBlank()) return
        AppStore.addTask(project.id, title.trim(), date?.atTime(time)?.millis(), repeat)
        title = ""; date = null; time = LocalTime.NOON; repeat = Repeat.NONE
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                title, { title = it }, placeholder = { Text("Add a new task...") }, singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { submit() }, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Filled.Add, "Add task") }
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipButton(date?.format("MMM d") ?: "Deadline", Icons.Filled.CalendarToday, date != null) { pickDate = true }
            if (date != null) ChipButton(formatTime12(time.toString()), Icons.Filled.Schedule) { pickTime = true }
            RepeatSelector(repeat, listOf(Repeat.NONE, Repeat.ONCE, Repeat.DAILY, Repeat.WEEKLY, Repeat.MONTHLY), Icons.Filled.Repeat) { repeat = it }
            if (date != null) TextButton(onClick = { date = null; time = LocalTime.NOON }) { Text("Clear", fontSize = 12.sp, color = Pal.c.mutedForeground) }
        }
    }
    if (pickDate) DatePickerModal(date, { pickDate = false }) { date = it }
    if (pickTime) TimePickerModal(time, { pickTime = false }) { time = it }
}

// ───────────────────────── Completed projects ─────────────────────────

@Composable
fun CompletedScreen(data: AppData, onBack: () -> Unit, onOpenProject: (String) -> Unit) {
    val context = LocalContext.current
    val done = data.projects.filter { it.completedAt != null }.sortedByDescending { it.completedAt }
    var editDateFor by remember { mutableStateOf<Project?>(null) }
    var editTimeFor by remember { mutableStateOf<Project?>(null) }

    Screen {
        ScreenHeader("Completed", null, Icons.Filled.EmojiEvents, Pal.c.primary, onBack)
        if (done.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconTile(Icons.Filled.EmojiEvents, Pal.c.mutedForeground, 64.dp, Pal.c.muted)
                Spacer(Modifier.height(16.dp))
                Text("No completed projects", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Pal.c.foreground)
                Text("Complete all tasks in a project to see it here", fontSize = 14.sp, color = Pal.c.mutedForeground)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            done.forEach { p ->
                AppCard(Modifier.fillMaxWidth(), background = Pal.c.card.copy(alpha = .6f), borderColor = Pal.c.border.copy(alpha = .5f), onClick = { onOpenProject(p.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.CheckCircle, Pal.c.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, color = Pal.c.foreground.copy(alpha = .7f), textDecoration = TextDecoration.LineThrough)
                            Text("${p.tasks.size} tasks", fontSize = 10.sp, color = Pal.c.mutedForeground)
                        }
                        Pill("✓ DONE", bg = Pal.c.primary.copy(alpha = .15f), fg = Pal.c.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    BarProgress(100, Brand.taskGradient, height = 10.dp)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Completed on ${(p.completedAt ?: p.createdAt).format("MMM d, yyyy, hh:mm:ss a")}",
                            fontSize = 11.sp, color = Pal.c.mutedForeground, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { editTimeFor = p }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.Schedule, "Change time", tint = Pal.c.mutedForeground, modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = { editDateFor = p }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.CalendarToday, "Change date", tint = Pal.c.mutedForeground, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }

    editDateFor?.let { p ->
        val current = (p.completedAt ?: p.createdAt).toLocalDateTime()
        DatePickerModal(current.toLocalDate(), { editDateFor = null }) { d ->
            AppStore.updateCompletedAt(p.id, d.atTime(current.toLocalTime()).millis())
            toast(context, "Completion date updated")
        }
    }
    editTimeFor?.let { p ->
        val current = (p.completedAt ?: p.createdAt).toLocalDateTime()
        TimePickerModal(current.toLocalTime(), { editTimeFor = null }) { t ->
            AppStore.updateCompletedAt(p.id, current.toLocalDate().atTime(t).millis())
            toast(context, "Completion time updated")
        }
    }
}
