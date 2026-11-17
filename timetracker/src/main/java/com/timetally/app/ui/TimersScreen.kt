@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.timetally.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetally.app.data.ActivityType
import com.timetally.app.data.AppData
import com.timetally.app.data.EmojiChoices
import com.timetally.app.data.Palette
import com.timetally.app.data.Running
import com.timetally.app.data.Store
import com.timetally.app.data.fmtDateTime
import com.timetally.app.data.fmtDur
import com.timetally.app.data.fmtTimer
import com.timetally.app.data.startMillis
import com.timetally.app.data.totalsIn
import java.time.LocalDate

@Composable
fun TimersScreen(data: AppData) {
    val now = rememberNow()
    var editing by remember { mutableStateOf<ActivityType?>(null) }
    var editingRunning by remember { mutableStateOf<Running?>(null) }
    val today = LocalDate.now()
    val todayTotals = totalsIn(data, today.startMillis(), today.plusDays(1).startMillis(), now)
    val trackedToday = todayTotals.values.sum()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text("TimeTally", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Tracked today: ${fmtDur(trackedToday)}  ·  tap to start or stop, hold to edit",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (data.running.isNotEmpty()) {
            items(data.running, key = { "run-${it.typeId}" }, span = { GridItemSpan(maxLineSpan) }) { r ->
                val t = data.types.firstOrNull { it.id == r.typeId } ?: return@items
                RunningCard(t, r, now, onStop = { Store.stop(t.id) }, onEdit = { editingRunning = r })
            }
            if (data.running.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedButton(onClick = { Store.stopAll() }) {
                        Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop all")
                    }
                }
            }
        }

        items(data.types, key = { it.id }) { t ->
            val running = data.running.firstOrNull { it.typeId == t.id }
            TypeTile(t, running, now, todayTotals[t.id] ?: 0L,
                onClick = { Store.toggle(t.id) },
                onLongClick = { editing = t })
        }
        item(key = "add") { AddTile { editing = ActivityType(0, "", EmojiChoices.first(), Palette[(data.types.size * 3) % Palette.size]) } }
    }

    editing?.let { t -> TypeEditor(t, isNew = data.types.none { it.id == t.id }) { editing = null } }
    editingRunning?.let { r ->
        val t = data.types.firstOrNull { it.id == r.typeId }
        if (t == null) editingRunning = null else RunningEditor(t, r) { editingRunning = null }
    }
}

@Composable
private fun RunningCard(t: ActivityType, r: Running, now: Long, onStop: () -> Unit, onEdit: () -> Unit) {
    val bg = t.color.asColor()
    val fg = contentOn(bg)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .combinedClickable(onClick = onStop, onLongClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t.emoji, fontSize = 30.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "since ${fmtDateTime(r.start)}" + if (r.comment.isNotBlank()) " · ${r.comment}" else "",
                color = fg.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(fmtTimer(now - r.start), color = fg, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(34.dp).background(fg.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Stop, "Stop", tint = fg)
        }
    }
}

@Composable
private fun TypeTile(t: ActivityType, running: Running?, now: Long, today: Long, onClick: () -> Unit, onLongClick: () -> Unit) {
    val color = t.color.asColor()
    val isOn = running != null
    val bg = if (isOn) color else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (isOn) contentOn(color) else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(2.dp, color.copy(alpha = if (isOn) 1f else 0.55f), RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isOn) Text(t.emoji, fontSize = 30.sp) else EmojiBadge(t.emoji, color.copy(alpha = 0.22f), 46.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            t.name, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Text(
            if (running != null) fmtTimer(now - running.start) else if (today > 0) fmtDur(today) else " ",
            color = fg.copy(alpha = if (isOn) 1f else 0.6f),
            fontSize = if (isOn) 15.sp else 12.sp,
            fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(
        Modifier
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Add activity", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeEditor(initial: ActivityType, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var color by remember { mutableStateOf(initial.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New activity" else "Edit activity") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(emoji, color.asColor(), 52.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(40) },
                        label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Colour", fontWeight = FontWeight.SemiBold)
                FlowRow {
                    Palette.forEach { c -> ColorDot(c.asColor(), c == color) { color = c } }
                }
                Spacer(Modifier.height(10.dp))
                Text("Icon", fontWeight = FontWeight.SemiBold)
                FlowRow {
                    EmojiChoices.forEach { e ->
                        Box(
                            Modifier
                                .padding(2.dp)
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (e == emoji) color.asColor().copy(alpha = 0.35f) else Color.Transparent)
                                .combinedClickable(onClick = { emoji = e }),
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
                OutlinedTextField(
                    value = emoji, onValueChange = { if (it.isNotBlank()) emoji = it.take(8) },
                    label = { Text("Or type any emoji") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                if (!isNew) {
                    Spacer(Modifier.height(10.dp))
                    Row {
                        TextButton(onClick = { Store.moveType(initial.id, -1) }) { Text("◀ Move earlier") }
                        TextButton(onClick = { Store.moveType(initial.id, 1) }) { Text("Move later ▶") }
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete activity", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { toast(ctx, "Enter a name"); return@TextButton }
                Store.saveType(initial.copy(name = name.trim(), emoji = emoji.trim(), color = color))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete) {
        val count = Store.data.records.count { it.typeId == initial.id }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.name}?") },
            text = { Text("This also deletes its $count record(s). This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { Store.deleteType(initial.id); confirmDelete = false; onDismiss() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RunningEditor(t: ActivityType, r: Running, onDismiss: () -> Unit) {
    var start by remember { mutableStateOf(r.start) }
    var comment by remember { mutableStateOf(r.comment) }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${t.emoji} ${t.name} (running)") },
        text = {
            Column {
                Text("Started", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { pickDateTime(ctx, start) { start = it } }) { Text(fmtDateTime(start)) }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment") })
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { Store.cancelRunning(t.id); onDismiss() }) {
                    Text("Discard this timer", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (start > System.currentTimeMillis()) { toast(ctx, "Start can't be in the future"); return@TextButton }
                Store.updateRunning(t.id, start, comment.trim()); onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
