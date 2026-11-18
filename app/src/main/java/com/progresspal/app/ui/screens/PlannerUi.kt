@file:OptIn(ExperimentalLayoutApi::class)

package com.progresspal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.progresspal.app.data.AppData
import com.progresspal.app.data.PlanItem
import com.progresspal.app.data.Planner
import com.progresspal.app.data.SttActivity
import com.progresspal.app.data.SttCache
import com.progresspal.app.ui.components.DayChips
import com.progresspal.app.ui.components.SttActivityRow
import com.progresspal.app.ui.components.TimePickerModal
import com.progresspal.app.ui.theme.Pal
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Quick lengths offered for a plan. */
val DurationChoices = listOf(5, 10, 15, 20, 30, 45, 60, 120, 180)

private val Repeats = listOf(
    "NONE" to "Once", "DAILY" to "Daily", "WEEKLY" to "Weekly",
    "MONTHLY" to "Monthly", "YEARLY" to "Yearly", "CUSTOM" to "Custom",
)

/**
 * Planning a block: first the Activity List (like Simple Time Tracker), then a small box with
 * From time, quick durations and repeat.
 */
@Composable
fun PlanEditor(initial: PlanItem, isNew: Boolean, @Suppress("UNUSED_PARAMETER") app: AppData, stt: SttCache, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val series = if (isNew) null else Planner.series(initial.id)
    var activity by remember { mutableStateOf(stt.activities.firstOrNull { it.id == initial.activityId }) }
    var picking by remember { mutableStateOf(activity == null) }
    var fromMs by remember { mutableLongStateOf(initial.start) }
    var minutes by remember { mutableIntStateOf(((initial.end - initial.start) / 60_000).toInt().coerceAtLeast(5)) }
    var repeat by remember { mutableStateOf(series?.repeat ?: "NONE") }
    var days by remember { mutableStateOf(series?.days ?: emptyList()) }
    var showFrom by remember { mutableStateOf(false) }
    var showTo by remember { mutableStateOf(false) }

    if (picking) {
        ActivityPicker(
            activities = stt.activities,
            selectedId = activity?.id,
            onPick = { activity = it; picking = false },
            onDismiss = { if (activity == null) onDismiss() else picking = false },
        )
        return
    }
    val a = activity ?: return

    val fromZ = Instant.ofEpochMilli(fromMs).atZone(zone)
    val toMs = fromMs + minutes * 60_000L
    val hm = DateTimeFormatter.ofPattern("h:mm a")
    val dateLabel = DateTimeFormatter.ofPattern("EEE d MMM").format(fromZ)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.c.card,
        title = { Text(if (isNew) "Plan" else "Edit plan", color = Pal.c.foreground) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // The chosen activity — tap to choose another.
                SttActivityRow(a, onClick = { picking = true }) {
                    Text("Change", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                }

                Label("From · $dateLabel")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showFrom = true }) {
                        Text(hm.format(fromZ).lowercase(), color = Pal.c.foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("  to  ", color = Pal.c.mutedForeground)
                    OutlinedButton(onClick = { showTo = true }) {
                        Text(hm.format(Instant.ofEpochMilli(toMs).atZone(zone)).lowercase(), color = Pal.c.foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Label("For · ${fmtMinutes(minutes)}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DurationChoices.forEach { m -> Chip(fmtMinutes(m), minutes == m) { minutes = m } }
                }

                Label("Repeat")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Repeats.forEach { (key, label) -> Chip(label, repeat == key) { repeat = key } }
                }
                if (repeat == "CUSTOM") {
                    Spacer(Modifier.height(8.dp))
                    DayChips(days) { d -> days = if (d in days) days - d else days + d }
                }

                if (!isNew) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { Planner.delete(initial.id); onDismiss() }) {
                        Text(if (series != null && series.repeat != "NONE") "Delete plan (all days)" else "Delete plan", color = Pal.c.destructive)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // A repeating plan keeps its first day; only the time of day and length change.
                val firstDay = series?.let { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() } ?: fromZ.toLocalDate()
                val start = firstDay.atTime(fromZ.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                Planner.save(
                    (series ?: initial).copy(
                        start = start, end = start + minutes * 60_000L,
                        title = a.name, kind = "activity", refId = a.id.toString(), subtitle = null,
                        activityId = a.id, activityName = a.name, color = a.color,
                        repeat = if (repeat == "CUSTOM" && days.isEmpty()) "NONE" else repeat,
                        days = if (repeat == "CUSTOM") days else emptyList(),
                    )
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showFrom) TimePickerModal(fromZ.toLocalTime(), { showFrom = false }) { t: LocalTime ->
        fromMs = fromZ.toLocalDate().atTime(t).atZone(zone).toInstant().toEpochMilli()
        showFrom = false
    }
    if (showTo) TimePickerModal(Instant.ofEpochMilli(toMs).atZone(zone).toLocalTime(), { showTo = false }) { t: LocalTime ->
        var end = fromZ.toLocalDate().atTime(t).atZone(zone).toInstant().toEpochMilli()
        if (end <= fromMs) end += 24 * 3_600_000L // past midnight
        minutes = ((end - fromMs) / 60_000).toInt().coerceAtLeast(1)
        showTo = false
    }
}

/** Full-screen Activity List, same look as Simple Time Tracker. */
@Composable
private fun ActivityPicker(activities: List<SttActivity>, selectedId: Long?, onPick: (SttActivity) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pal.c.background).padding(top = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Choose an activity", Modifier.weight(1f), color = Pal.c.foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
            if (activities.isEmpty()) {
                Text(
                    "No activities yet. Open Simple Time Tracker once and sync (⟳) so its activities appear here.",
                    Modifier.padding(16.dp), color = Pal.c.mutedForeground,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(activities, key = { it.id }) { a ->
                    SttActivityRow(a, selected = a.id == selectedId, onClick = { onPick(a) })
                }
            }
        }
    }
}

fun fmtMinutes(m: Int): String = when {
    m < 60 -> "${m}m"
    m % 60 == 0 -> "${m / 60}h"
    else -> "${m / 60}h ${m % 60}m"
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, color = Pal.c.mutedForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Pal.c.primary
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c else c.copy(alpha = .12f))
            .border(1.dp, c.copy(alpha = if (selected) 1f else .4f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (selected) Color.White else Pal.c.foreground,
        fontSize = 14.sp,
    )
}
