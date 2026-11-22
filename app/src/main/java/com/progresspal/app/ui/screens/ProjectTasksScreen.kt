package com.progresspal.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progresspal.app.data.AppData
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.Subtasks
import com.progresspal.app.data.deepProgress
import com.progresspal.app.ui.components.Sounds
import com.progresspal.app.ui.theme.Pal

/** Bright gradients, one per depth: tasks, subtasks, sub-subtasks … */
private val LevelGradients = listOf(
    listOf(Color(0xFF00E676), Color(0xFF00B0FF)),
    listOf(Color(0xFFFFEA00), Color(0xFFFF6D00)),
    listOf(Color(0xFFFF4081), Color(0xFF7C4DFF)),
    listOf(Color(0xFF18FFFF), Color(0xFF2979FF)),
)
private fun gradient(level: Int) = LevelGradients[level % LevelGradients.size]

/** One step of the path: the project (level 0 lists its tasks) or a task / subtask (lists its subtasks). */
private data class Crumb(val id: String, val title: String)

/** A row: id, title, done, and how to tick it. */
private data class Node(val id: String, val title: String, val done: Boolean, val toggle: () -> Unit, val rename: (String) -> Unit, val delete: () -> Unit)

/**
 * A project's tasks, each with a tick box and a progress bar; tapping one opens its subtasks, and
 * so on to any depth. Add, rename and delete at every level.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectTasksScreen(app: AppData, projectId: String, onBack: () -> Unit) {
    val subs by Subtasks.data.collectAsStateWithLifecycle()
    val project = app.projects.firstOrNull { it.id == projectId }
    var path by remember { mutableStateOf(listOf<Crumb>()) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Node?>(null) }
    var editingProject by remember { mutableStateOf(false) }

    if (project == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }
    val level = path.size
    val back: () -> Unit = { if (path.isEmpty()) onBack() else path = path.dropLast(1) }
    BackHandler(onBack = back)

    // Rows on this level.
    val nodes: List<Node> = if (level == 0) project.tasks.map { t ->
        Node(
            t.id, t.title, t.completed,
            toggle = { if (!t.completed) Sounds.done(); AppStore.toggleTask(project.id, t.id) },
            rename = { n -> AppStore.editTask(project.id, t.id) { it.copy(title = n) } },
            delete = { AppStore.deleteTask(project.id, t.id); Subtasks.deleteUnder(t.id) },
        )
    } else Subtasks.children(path.last().id, subs).map { s ->
        Node(
            s.id, s.title, s.done,
            toggle = { if (!s.done) Sounds.done(); Subtasks.toggle(s.id, AppStore.data.value) },
            rename = { n -> Subtasks.rename(s.id, n) },
            delete = { Subtasks.delete(s.id) },
        )
    }
    val levelFrac = if (level == 0) project.deepProgress(subs)
    else nodes.map { Subtasks.fraction(it.id, it.done, subs) }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
    val doneCount = nodes.count { it.done }
    val what = when (level) { 0 -> "task"; 1 -> "subtask"; else -> "subtask ($level)" }

    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.c.foreground) }
            Column(Modifier.weight(1f)) {
                Text(if (level == 0) project.name else path.last().title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Pal.c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (listOf(project.name) + path.map { it.title }).dropLast(if (level == 0) 0 else 1).joinToString(" › ").ifBlank { project.name },
                    fontSize = 11.sp, color = Pal.c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (level == 0) IconButton(onClick = { editingProject = true }) { Icon(Icons.Filled.Edit, "Edit project", tint = Pal.c.foreground) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            // Summary bar for this level.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.c.card)
                    .border(1.dp, Pal.c.border, RoundedCornerShape(16.dp)).padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(levelFrac * 100).toInt()}%", fontSize = 34.sp, fontWeight = FontWeight.Black, color = gradient(level)[0])
                    Spacer(Modifier.width(8.dp))
                    Text("$doneCount/${nodes.size} ${what}s done", Modifier.padding(bottom = 6.dp), fontSize = 13.sp, color = Pal.c.mutedForeground)
                }
                BrightBar(levelFrac, gradient(level), 14)
            }
            Spacer(Modifier.height(12.dp))

            if (nodes.isEmpty()) Text("No ${what}s yet — add the first one below.", Modifier.padding(vertical = 20.dp), color = Pal.c.mutedForeground)
            nodes.forEach { n ->
                val kids = Subtasks.children(n.id, subs)
                val frac = Subtasks.fraction(n.id, n.done, subs)
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(Pal.c.card)
                        .border(1.dp, gradient(level)[0].copy(alpha = .35f), RoundedCornerShape(14.dp))
                        .combinedClickable(onClick = { path = path + Crumb(n.id, n.title) }, onLongClick = { editing = n })
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val g = gradient(level)
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(if (n.done) Brush.linearGradient(g) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                .border(2.dp, g[0], CircleShape).clickable { n.toggle() },
                            contentAlignment = Alignment.Center,
                        ) { if (n.done) Text("✓", color = Color.White, fontWeight = FontWeight.Black) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                n.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = if (n.done) Pal.c.mutedForeground else Pal.c.foreground,
                                textDecoration = if (n.done) TextDecoration.LineThrough else null,
                            )
                            Text(
                                if (kids.isEmpty()) "Tap to add subtasks" else "${kids.count { it.done }}/${kids.size} subtasks · ${(frac * 100).toInt()}%",
                                fontSize = 11.sp, color = Pal.c.mutedForeground,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open", tint = Pal.c.mutedForeground)
                    }
                    Spacer(Modifier.height(8.dp))
                    BrightBar(frac, gradient(level + 1), 8)
                }
            }
            Spacer(Modifier.height(80.dp))
        }

        Button(
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Add $what")
        }
    }

    if (adding) NameDialog("New $what", "") { name ->
        if (name != null && name.isNotBlank()) {
            if (level == 0) AppStore.addTask(project.id, name.trim()) else Subtasks.add(path.last().id, name.trim())
        }
        adding = false
    }
    editing?.let { n ->
        NameDialog("Edit", n.title, onDelete = { n.delete(); editing = null }) { name ->
            if (name != null && name.isNotBlank()) n.rename(name.trim())
            editing = null
        }
    }
    if (editingProject) NameDialog("Project name", project.name, deleteLabel = "Delete project", onDelete = {
        project.tasks.forEach { Subtasks.deleteUnder(it.id) }
        AppStore.deleteProject(project.id)
        editingProject = false
        onBack()
    }) { name ->
        if (name != null && name.isNotBlank()) AppStore.editProject(project.id, name.trim(), project.target)
        editingProject = false
    }
}

/** A thick, bright gradient progress bar. */
@Composable
fun BrightBar(frac: Float, colors: List<Color>, heightDp: Int) {
    Box(Modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(heightDp.dp)).background(Pal.c.muted)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(heightDp.dp)).background(Brush.horizontalGradient(colors)))
    }
}

/** Name input; returns null on cancel. Optional delete button. */
@Composable
fun NameDialog(title: String, initial: String, deleteLabel: String = "Delete", onDelete: (() -> Unit)? = null, onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = Pal.c.card,
        title = { Text(title, color = Pal.c.foreground) },
        text = {
            Column {
                OutlinedTextField(text, { text = it.take(120) }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Name") })
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text(deleteLabel, color = Pal.c.destructive) }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
    if (confirmDelete && onDelete != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = Pal.c.card,
        title = { Text("$deleteLabel?", color = Pal.c.foreground) },
        text = { Text("\"$initial\" and everything under it will be deleted.", color = Pal.c.foreground) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Pal.c.destructive) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
