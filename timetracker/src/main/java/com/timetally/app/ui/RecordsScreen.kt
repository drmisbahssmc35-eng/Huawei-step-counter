@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.timetally.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetally.app.data.ActivityType
import com.timetally.app.data.AppData
import com.timetally.app.data.Store
import com.timetally.app.data.TimeRecord
import com.timetally.app.data.fmtClock
import com.timetally.app.data.fmtDateTime
import com.timetally.app.data.fmtDay
import com.timetally.app.data.fmtDur
import com.timetally.app.data.startMillis
import java.time.LocalDate

/** One row in the day list: a saved record, a running timer, or a gap. */
private sealed class Row_ {
    abstract val start: Long
    abstract val end: Long
    data class Rec(val r: TimeRecord, override val start: Long, override val end: Long) : Row_()
    data class Live(val typeId: Long, override val start: Long, override val end: Long) : Row_()
    data class Gap(override val start: Long, override val end: Long) : Row_()
}

@Composable
fun RecordsScreen(data: AppData) {
    val now = rememberNow()
    val ctx = LocalContext.current
    var dayEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayEpoch)
    var editing by remember { mutableStateOf<TimeRecord?>(null) }

    val from = day.startMillis()
    val to = day.plusDays(1).startMillis()
    val dayEnd = minOf(to, now)

    val rows = buildList<Row_> {
        data.records.filter { it.end > from && it.start < to }
            .forEach { add(Row_.Rec(it, maxOf(it.start, from), minOf(it.end, to))) }
        data.running.filter { now > from && it.start < to }
            .forEach { add(Row_.Live(it.typeId, maxOf(it.start, from), minOf(now, to))) }
    }.sortedBy { it.start }

    val withGaps = if (!data.settings.showUntracked || from > now) rows else buildList<Row_> {
        var cursor = from
        rows.forEach { r ->
            if (r.start - cursor >= 60_000) add(Row_.Gap(cursor, r.start))
            add(r)
            cursor = maxOf(cursor, r.end)
        }
        if (dayEnd - cursor >= 60_000) add(Row_.Gap(cursor, dayEnd))
    }
    val shown = withGaps.reversed() // newest on top
    val total = rows.filter { it !is Row_.Gap }.sumOf { it.end - it.start }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PeriodSwitcher(
                title = when (dayEpoch - LocalDate.now().toEpochDay()) {
                    0L -> "Today"; -1L -> "Yesterday"; else -> fmtDay(day)
                },
                onPrev = { dayEpoch-- },
                onNext = { dayEpoch++ },
                onTitle = { pickDate(ctx, day) { dayEpoch = it.toEpochDay() } },
            )
            Text(
                "Tracked: ${fmtDur(total)}",
                Modifier.padding(horizontal = 16.dp), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No records for this day", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown) { row ->
                    when (row) {
                        is Row_.Rec -> {
                            val t = data.types.firstOrNull { it.id == row.r.typeId }
                            RecordRow(t, row.start, row.end, row.r.comment, live = false) { editing = row.r }
                        }
                        is Row_.Live -> {
                            val t = data.types.firstOrNull { it.id == row.typeId }
                            RecordRow(t, row.start, row.end, data.running.firstOrNull { it.typeId == row.typeId }?.comment ?: "", live = true) {}
                        }
                        is Row_.Gap -> GapRow(row.start, row.end) {
                            editing = TimeRecord(0, data.types.firstOrNull()?.id ?: 0, row.start, row.end)
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                val end = if (day == LocalDate.now()) now else to - 1
                editing = TimeRecord(0, data.types.firstOrNull()?.id ?: 0, maxOf(from, end - 3_600_000), end)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, "Add record") }
    }

    editing?.let { r -> RecordEditor(data, r) { editing = null } }
}

@Composable
private fun RecordRow(t: ActivityType?, start: Long, end: Long, comment: String, live: Boolean, onClick: () -> Unit) {
    val color = (t?.color ?: 0xFF9E9E9E).asColor()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(t?.emoji ?: "❓", color, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t?.name ?: "Deleted activity", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${fmtClock(start)} – ${if (live) "now" else fmtClock(end)}",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (comment.isNotBlank()) Text(comment, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(fmtDur(end - start), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun GapRow(start: Long, end: Long, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Untracked", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text("${fmtClock(start)} – ${fmtClock(end)} · tap to fill", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(fmtDur(end - start), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RecordEditor(data: AppData, initial: TimeRecord, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val isNew = initial.id <= 0
    var typeId by remember { mutableStateOf(initial.typeId) }
    var start by remember { mutableStateOf(initial.start) }
    var end by remember { mutableStateOf(initial.end) }
    var comment by remember { mutableStateOf(initial.comment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add record" else "Edit record") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Activity", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.types.forEach { t ->
                        val sel = t.id == typeId
                        val c = t.color.asColor()
                        Text(
                            "${t.emoji} ${t.name}",
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) c else c.copy(alpha = 0.15f))
                                .clickable { typeId = t.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            color = if (sel) contentOn(c) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Start", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { pickDateTime(ctx, start) { start = it } }) { Text(fmtDateTime(start)) }
                Text("End", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { pickDateTime(ctx, end) { end = it } }) { Text(fmtDateTime(end)) }
                Text(
                    "Duration: ${if (end > start) fmtDur(end - start) else "—"}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment") }, modifier = Modifier.fillMaxWidth())
                if (!isNew) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { Store.deleteRecord(initial.id); onDismiss() }) {
                        Text("Delete record", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    data.types.none { it.id == typeId } -> toast(ctx, "Pick an activity")
                    end <= start -> toast(ctx, "End must be after start")
                    else -> {
                        Store.saveRecord(initial.copy(typeId = typeId, start = start, end = end, comment = comment.trim()))
                        onDismiss()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
