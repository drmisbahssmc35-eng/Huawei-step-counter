package com.progresspal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.data.AppData
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.Challenge
import com.progresspal.app.data.ChallengeStatus
import com.progresspal.app.data.ChallengeTask
import com.progresspal.app.data.Difficulty
import com.progresspal.app.data.calculateDifficulty
import com.progresspal.app.data.dailyTasksFor
import com.progresspal.app.data.difficulty
import com.progresspal.app.data.endedAt
import com.progresspal.app.data.format
import com.progresspal.app.data.millis
import com.progresspal.app.data.pad2
import com.progresspal.app.data.today
import com.progresspal.app.ui.components.AppCard
import com.progresspal.app.ui.components.BarProgress
import com.progresspal.app.ui.components.ChipButton
import com.progresspal.app.ui.components.DatePickerModal
import com.progresspal.app.ui.components.LinkCard
import com.progresspal.app.ui.components.Pill
import com.progresspal.app.ui.components.Ring
import com.progresspal.app.ui.components.ScreenHeader
import com.progresspal.app.ui.components.Sounds
import com.progresspal.app.ui.components.TimePickerModal
import com.progresspal.app.ui.components.rememberNow
import com.progresspal.app.ui.components.toast
import com.progresspal.app.ui.theme.Brand
import com.progresspal.app.ui.theme.NumberStyle
import com.progresspal.app.ui.theme.Pal
import androidx.compose.ui.graphics.Brush
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class DurationOption(val label: String, val minutes: Int, val icon: String)

private val DURATIONS = listOf(
    DurationOption("30 Min", 30, "⚡"), DurationOption("1 Hour", 60, "🔥"),
    DurationOption("2 Hours", 120, "💪"), DurationOption("3 Hours", 180, "🏆"),
    DurationOption("4 Hours", 240, "🎯"), DurationOption("6 Hours", 360, "⚔️"),
    DurationOption("10 Hours", 600, "🔱"), DurationOption("12 Hours", 720, "👑"),
)

private fun clock(totalSec: Long): String {
    val s = max(0L, totalSec)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "$h:${pad2(m)}:${pad2(sec)}" else "$m:${pad2(sec)}"
}

/** Effective elapsed ms of a running challenge, excluding pauses. */
private fun Challenge.elapsedMs(now: Long): Long = (pausedAt ?: now) - startedAt - totalPausedMs

@Composable
fun DifficultyBadge(d: Difficulty, showScore: Boolean = true) {
    Pill(
        if (showScore) "${d.level} · ${d.score}" else d.level,
        bg = Color(d.bg), fg = Color(d.fg), icon = Icons.Filled.Shield,
    )
}

@Composable
fun ChallengeScreen(data: AppData, onBack: () -> Unit, onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val now by rememberNow()
    val active = data.challenges.firstOrNull { it.status == ChallengeStatus.ACTIVE }
    val scheduled = data.challenges.filter { it.status == ChallengeStatus.SCHEDULED }.sortedBy { it.startedAt }
    var resultId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val result = resultId?.let { id -> data.challenges.find { it.id == id } }

    // Activate scheduled challenges whose time arrived, and finish expired ones.
    LaunchedEffect(now / 1000) {
        if (active == null) {
            scheduled.firstOrNull { it.startedAt <= now }?.let { ch ->
                AppStore.updateChallenge(ch.id) { it.copy(status = ChallengeStatus.ACTIVE) }
                Sounds.start()
                toast(context, "🚀 Scheduled challenge has started!")
            }
        } else if (active.pausedAt == null && active.elapsedMs(now) >= active.durationMinutes * 60_000L) {
            val won = active.tasks.all { it.completed }
            AppStore.updateChallenge(active.id) {
                it.copy(status = if (won) ChallengeStatus.COMPLETED else ChallengeStatus.FAILED, completedAt = System.currentTimeMillis())
            }
            resultId = active.id
            if (won) Sounds.won() else Sounds.lost()
            toast(context, if (won) "🎉 Challenge Completed!" else "⏰ Time's up! Challenge failed.")
        }
    }

    Screen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                ScreenHeader("Time vs Task", "Beat the clock, complete your tasks!", Icons.Filled.SportsMartialArts, Brand.coral, onBack)
            }
        }

        if (active != null) {
            ActiveChallenge(active, now, onFinished = { resultId = it })
            Spacer(Modifier.height(16.dp))
            AppCard(
                Modifier.fillMaxWidth(), borderColor = Pal.c.destructive.copy(alpha = .3f), background = Pal.c.destructive.copy(alpha = .05f),
                onClick = {
                    AppStore.updateChallenge(active.id) { it.copy(status = ChallengeStatus.FAILED, completedAt = System.currentTimeMillis()) }
                    resultId = active.id
                    Sounds.lost()
                    toast(context, "Challenge abandoned.")
                },
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, null, tint = Pal.c.destructive, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Abandon & Start Fresh", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Pal.c.destructive)
                }
            }
        } else if (result != null) {
            ResultCard(result) { resultId = null }
        }

        if (scheduled.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                scheduled.forEach { ch -> ScheduledCard(ch, now) }
            }
        }

        if (active == null && result == null) {
            TimeWithoutChallenge(data.challenges, now)
            Spacer(Modifier.height(16.dp))
            if (!showCreate) {
                Button(
                    onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start New Challenge", fontWeight = FontWeight.SemiBold)
                }
            } else {
                CreateChallenge(data, onClose = { showCreate = false })
            }
        }

        Spacer(Modifier.height(16.dp))
        LinkCard(Icons.Filled.History, Brand.purple, "Challenge History", "${data.challenges.count { it.status == ChallengeStatus.COMPLETED || it.status == ChallengeStatus.FAILED }} finished · stats & past results", onOpenHistory)
    }
}

@Composable
private fun ActiveChallenge(ch: Challenge, now: Long, onFinished: (String) -> Unit) {
    val context = LocalContext.current
    val paused = ch.pausedAt != null
    val totalSec = ch.durationMinutes * 60L
    val elapsedSec = ch.elapsedMs(now) / 1000
    val taskPct = if (ch.tasks.isEmpty()) 0 else ((ch.tasks.count { it.completed } * 100.0) / ch.tasks.size).roundToInt()
    val timePct = min(100, ((elapsedSec * 100.0) / totalSec).roundToInt())

    AppCard(Modifier.fillMaxWidth(), borderColor = Brand.coral.copy(alpha = .3f)) {
        Text(ch.name, fontWeight = FontWeight.Bold, color = Pal.c.foreground)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            DifficultyBadge(ch.difficulty())
            Spacer(Modifier.width(8.dp))
            Text("${ch.durationMinutes} min challenge" + if (paused) " · Paused" else "", fontSize = 12.sp, color = if (paused) Brand.amber else Pal.c.mutedForeground)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                if (paused) {
                    val extra = System.currentTimeMillis() - (ch.pausedAt ?: System.currentTimeMillis())
                    AppStore.updateChallenge(ch.id) { it.copy(pausedAt = null, totalPausedMs = it.totalPausedMs + extra) }
                    toast(context, "▶️ Challenge resumed!")
                } else {
                    AppStore.updateChallenge(ch.id) { it.copy(pausedAt = System.currentTimeMillis()) }
                    toast(context, "⏸ Challenge paused.")
                }
            }) {
                Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, tint = if (paused) Pal.c.primary else Brand.amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (paused) "Resume" else "Pause", color = if (paused) Pal.c.primary else Brand.amber, fontSize = 12.sp)
            }
            TextButton(onClick = {
                AppStore.updateChallenge(ch.id) { it.copy(status = ChallengeStatus.FAILED, completedAt = System.currentTimeMillis()) }
                onFinished(ch.id)
                Sounds.lost()
                toast(context, "Challenge abandoned.")
            }) {
                Icon(Icons.Filled.Stop, null, tint = Pal.c.destructive, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Abandon", color = Pal.c.destructive, fontSize = 12.sp)
            }
        }

        LabeledBar("Time", Icons.Filled.Schedule, timePct, Brush.horizontalGradient(listOf(Brand.coral, Brand.amber)))
        Spacer(Modifier.height(8.dp))
        LabeledBar("Tasks", Icons.Filled.Check, taskPct, Brush.horizontalGradient(listOf(Brand.green, Brand.blue)))

        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            val remaining = max(0L, totalSec - elapsedSec)
            val frac = remaining.toFloat() / totalSec
            val c = when {
                frac <= .1f -> Pal.c.destructive
                frac <= .25f -> Brand.amber
                else -> Brand.coral
            }
            Ring(frac, c, 110.dp, 7.dp, track = Pal.c.border) {
                Text(clock(remaining), style = NumberStyle, fontSize = 16.sp, color = Pal.c.foreground)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ch.tasks.forEach { t -> ChallengeTaskRow(ch, t, now, onFinished) }
        }
    }
}

@Composable
private fun LabeledBar(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, pct: Int, brush: Brush) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Pal.c.mutedForeground, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = Pal.c.mutedForeground, modifier = Modifier.weight(1f))
        Text("$pct%", fontSize = 11.sp, color = Pal.c.foreground, fontWeight = FontWeight.SemiBold)
    }
    BarProgress(pct, brush, height = 10.dp)
}

@Composable
private fun ChallengeTaskRow(ch: Challenge, t: ChallengeTask, now: Long, onFinished: (String) -> Unit) {
    val context = LocalContext.current
    val paused = ch.pausedAt != null
    val started = t.startedAt != null
    val elapsed = if (t.startedAt != null) ((if (paused) ch.pausedAt ?: now else now) - t.startedAt) / 1000 else 0L
    val remaining = max(0L, t.timerSeconds - elapsed)
    val pct = when {
        t.completed -> 100
        !started -> 0
        t.timerSeconds > 0 -> min(100, ((elapsed * 100.0) / t.timerSeconds).roundToInt())
        else -> 100
    }
    val expired = started && remaining <= 0 && !t.completed
    val brush = when {
        t.completed -> Brush.horizontalGradient(listOf(Brand.green, Brand.blue))
        !started -> Brush.horizontalGradient(listOf(Brand.blue.copy(alpha = .4f), Brand.purple.copy(alpha = .4f)))
        pct >= 90 -> Brush.horizontalGradient(listOf(Pal.c.destructive, Pal.c.destructive))
        pct >= 75 -> Brush.horizontalGradient(listOf(Brand.amber, Brand.coral))
        else -> Brush.horizontalGradient(listOf(Brand.blue, Brand.purple))
    }
    AppCard(
        Modifier.fillMaxWidth(), padding = 12.dp,
        borderColor = when {
            t.completed -> Pal.c.primary.copy(alpha = .2f)
            expired -> Pal.c.destructive.copy(alpha = .2f)
            else -> Pal.c.border
        },
        background = when {
            t.completed -> Pal.c.primary.copy(alpha = .05f)
            expired -> Pal.c.destructive.copy(alpha = .05f)
            else -> Pal.c.card
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (t.completed) Pal.c.primary else Color.Transparent)
                    .border(2.dp, if (t.completed) Pal.c.primary else Pal.c.mutedForeground.copy(alpha = .3f), CircleShape)
                    .clickable { if (toggleChallengeTask(context, ch, t)) onFinished(ch.id) },
                contentAlignment = Alignment.Center,
            ) { if (t.completed) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(
                t.title, fontSize = 14.sp, modifier = Modifier.weight(1f),
                color = if (t.completed) Pal.c.mutedForeground else Pal.c.foreground,
                textDecoration = if (t.completed) TextDecoration.LineThrough else null,
            )
            if (!t.completed && !started) {
                TextButton(onClick = {
                    if (!paused) AppStore.updateChallenge(ch.id) { c ->
                        c.copy(tasks = c.tasks.map { if (it.id == t.id) it.copy(startedAt = System.currentTimeMillis()) else it })
                    }
                }) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(14.dp))
                    Text("Start", fontSize = 12.sp)
                }
            }
            if (!t.completed && started) {
                IconButton(onClick = {
                    AppStore.updateChallenge(ch.id) { c ->
                        c.copy(tasks = c.tasks.map { if (it.id == t.id) it.copy(startedAt = null, completed = false) else it })
                    }
                }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Refresh, "Reset timer", tint = Pal.c.mutedForeground, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                if (t.completed) "✓" else clock(remaining), style = NumberStyle, fontSize = 12.sp,
                color = when {
                    t.completed -> Pal.c.primary
                    expired -> Pal.c.destructive
                    !started -> Pal.c.mutedForeground.copy(alpha = .5f)
                    else -> Pal.c.mutedForeground
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        BarProgress(pct, brush, height = 6.dp)
    }
}

/** Returns true when this completes the whole challenge. */
private fun toggleChallengeTask(context: android.content.Context, ch: Challenge, t: ChallengeTask): Boolean {
    val newCompleted = !t.completed
    if (newCompleted) Sounds.check() else Sounds.uncheck()
    val updated = ch.tasks.map { if (it.id == t.id) it.copy(completed = newCompleted) else it }
    val allDone = updated.all { it.completed }
    AppStore.updateChallenge(ch.id) {
        it.copy(
            tasks = updated,
            status = if (allDone) ChallengeStatus.COMPLETED else it.status,
            completedAt = if (allDone) System.currentTimeMillis() else it.completedAt,
        )
    }
    // Keep the original daily task in sync.
    t.sourceTaskId?.let { srcId ->
        val data = AppStore.data.value
        val entry = dailyTasksFor(data, today()).find { it.task.id == srcId }
        if (entry != null) {
            if (entry.isRecurring) AppStore.setDateTask(srcId, today(), newCompleted)
            else if (entry.task.completed != newCompleted) AppStore.toggleTask(entry.project.id, srcId)
        }
    }
    if (allDone) {
        Sounds.won()
        toast(context, "🎉 All tasks done! Challenge Completed!")
    }
    return allDone
}

@Composable
private fun ResultCard(ch: Challenge, onContinue: () -> Unit) {
    val won = ch.status == ChallengeStatus.COMPLETED
    val c = if (won) Pal.c.primary else Pal.c.destructive
    AppCard(Modifier.fillMaxWidth(), borderColor = c.copy(alpha = .3f), background = c.copy(alpha = .05f), corner = 18.dp, padding = 20.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (won) "🎉" else "😤", fontSize = 44.sp)
            Text(ch.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Pal.c.foreground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            DifficultyBadge(ch.difficulty())
            Text(if (won) "You crushed it!" else "Better luck next time!", fontSize = 14.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${ch.tasks.count { it.completed }}/${ch.tasks.size}", style = NumberStyle, fontSize = 22.sp, color = c)
                    Text("Tasks Done", fontSize = 11.sp, color = Pal.c.mutedForeground)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${ch.durationMinutes}m", style = NumberStyle, fontSize = 22.sp, color = Pal.c.foreground)
                    Text("Duration", fontSize = 11.sp, color = Pal.c.mutedForeground)
                }
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        }
    }
}

@Composable
private fun ScheduledCard(ch: Challenge, now: Long) {
    val context = LocalContext.current
    val diff = max(0L, (ch.startedAt - now) / 1000)
    AppCard(Modifier.fillMaxWidth(), borderColor = Brand.purple.copy(alpha = .3f), background = Brand.purple.copy(alpha = .05f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EventRepeat, null, tint = Brand.purple, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("SCHEDULED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Brand.purple, modifier = Modifier.weight(1f))
            DifficultyBadge(ch.difficulty(), showScore = false)
        }
        Text(ch.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Pal.c.foreground, modifier = Modifier.padding(top = 6.dp))
        val dur = if (ch.durationMinutes >= 60) "${ch.durationMinutes / 60.0}".removeSuffix(".0") + "h" else "${ch.durationMinutes}m"
        Text("Starts ${ch.startedAt.format("h:mm a · dd MMM yy")} · ${ch.tasks.size} tasks · $dur", fontSize = 12.sp, color = Pal.c.mutedForeground)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, null, tint = Brand.purple, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            val h = diff / 3600
            Text(
                "Starts in ${if (h > 0) "${h}h " else ""}${pad2((diff % 3600) / 60)}m ${pad2(diff % 60)}s",
                style = NumberStyle, fontSize = 13.sp, color = Brand.purple, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                AppStore.updateChallenge(ch.id) { it.copy(status = ChallengeStatus.FAILED, completedAt = System.currentTimeMillis()) }
                toast(context, "Scheduled challenge cancelled.")
            }) {
                Icon(Icons.Filled.Close, null, tint = Pal.c.destructive, modifier = Modifier.size(14.dp))
                Text("Cancel", color = Pal.c.destructive, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TimeWithoutChallenge(challenges: List<Challenge>, now: Long) {
    val last = challenges.mapNotNull { it.endedAt() }.maxOrNull() ?: return
    val elapsed = max(0L, (now - last) / 1000)
    Spacer(Modifier.height(16.dp))
    AppCard(Modifier.fillMaxWidth(), borderColor = Brand.amber.copy(alpha = .3f), background = Brand.amber.copy(alpha = .05f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(Icons.Filled.Timer, null, tint = Brand.amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Time Without Challenge", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Pal.c.foreground)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(elapsed / 86400 to "Days", (elapsed % 86400) / 3600 to "Hours", (elapsed % 3600) / 60 to "Mins", elapsed % 60 to "Secs").forEach { (v, l) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Pal.c.muted).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(pad2(v), style = NumberStyle, fontSize = 22.sp, color = Brand.amber)
                    Text(l, fontSize = 10.sp, color = Pal.c.mutedForeground)
                }
            }
        }
        Text("Last challenge ended: ${last.format("dd MMM yyyy, hh:mm a")}", fontSize = 11.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(top = 10.dp))
    }
}

private data class PendingTask(val title: String, val seconds: Int, val sourceTaskId: String? = null)

@Composable
private fun CreateChallenge(data: AppData, onClose: () -> Unit) {
    val context = LocalContext.current
    var duration by remember { mutableStateOf(60) }
    var tasks by remember { mutableStateOf(listOf<PendingTask>()) }
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("10") }
    var schedule by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(today().plusDays(1)) }
    var time by remember { mutableStateOf(LocalTime.of(6, 0)) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val todays = dailyTasksFor(data, today())

    AppCard(Modifier.fillMaxWidth(), corner = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Text("New Challenge", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Pal.c.foreground, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Pal.c.mutedForeground) }
        }
        Text("Select Duration", fontSize = 12.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(bottom = 8.dp))
        DURATIONS.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { opt ->
                    val sel = opt.minutes == duration
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Pal.c.primary.copy(alpha = .1f) else Pal.c.card)
                            .border(1.dp, if (sel) Pal.c.primary else Pal.c.border, RoundedCornerShape(12.dp))
                            .clickable { duration = opt.minutes }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(opt.icon, fontSize = 18.sp)
                        Text(opt.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Pal.c.foreground)
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (schedule) Brand.purple.copy(alpha = .1f) else Pal.c.muted.copy(alpha = .3f))
                .clickable { schedule = !schedule }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.EventRepeat, null, tint = if (schedule) Brand.purple else Pal.c.mutedForeground, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Schedule for later", fontSize = 12.sp, color = if (schedule) Pal.c.foreground else Pal.c.mutedForeground, modifier = Modifier.weight(1f))
            Switch(checked = schedule, onCheckedChange = { schedule = it })
        }
        if (schedule) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(date.format("EEE, MMM d"), Icons.Filled.EventRepeat) { pickDate = true }
                ChipButton(time.format(com.progresspal.app.data.fmt("h:mm a")), Icons.Filled.Schedule) { pickTime = true }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Add Tasks", fontSize = 12.sp, color = Pal.c.mutedForeground, modifier = Modifier.weight(1f))
            if (todays.isNotEmpty()) {
                TextButton(onClick = { showImport = !showImport }) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showImport) "Hide" else "Import Today's Tasks", fontSize = 12.sp)
                }
            }
        }
        if (showImport) {
            AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), padding = 8.dp, background = Pal.c.muted.copy(alpha = .3f)) {
                todays.forEach { e ->
                    val added = tasks.any { it.sourceTaskId == e.task.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !added) {
                                tasks = tasks + PendingTask(e.task.title, (minutes.toIntOrNull() ?: 10) * 60, e.task.id)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (added) Icons.Filled.Check else Icons.Filled.Add, null, tint = if (added) Pal.c.primary else Pal.c.mutedForeground, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(e.task.title, fontSize = 13.sp, color = if (added) Pal.c.mutedForeground else Pal.c.foreground, modifier = Modifier.weight(1f))
                        Pill(e.project.name)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(title, { title = it }, placeholder = { Text("Task name") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                minutes, { v -> minutes = v.filter { it.isDigit() }.take(3) }, singleLine = true,
                suffix = { Text("min", fontSize = 11.sp) }, modifier = Modifier.width(84.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(6.dp))
            FilledIconButton(onClick = {
                if (title.isNotBlank()) {
                    tasks = tasks + PendingTask(title.trim(), (minutes.toIntOrNull() ?: 10).coerceAtLeast(1) * 60)
                    title = ""
                }
            }, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Filled.Add, "Add") }
        }

        if (tasks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            tasks.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", fontSize = 12.sp, color = Pal.c.mutedForeground, modifier = Modifier.width(22.dp))
                    Text(t.title, fontSize = 14.sp, color = Pal.c.foreground, modifier = Modifier.weight(1f))
                    Pill("${t.seconds / 60} min", icon = Icons.Filled.Timer)
                    IconButton(onClick = { tasks = tasks.filterIndexed { idx, _ -> idx != i } }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Delete, "Remove", tint = Pal.c.mutedForeground, modifier = Modifier.size(16.dp))
                    }
                }
            }
            val d = calculateDifficulty(duration, tasks.map { it.seconds })
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Difficulty", fontSize = 12.sp, color = Pal.c.mutedForeground, modifier = Modifier.weight(1f))
                DifficultyBadge(d)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (tasks.isEmpty()) { toast(context, "Add at least one task!"); return@Button }
                val start = if (schedule) date.atTime(time).millis() else System.currentTimeMillis()
                if (schedule && start <= System.currentTimeMillis()) { toast(context, "Scheduled time must be in the future!"); return@Button }
                val end = start + duration * 60_000L
                val n = data.challenges.size + 1
                val name = "Time Vs Task Challenge No:$n (${start.format("h:mm a").lowercase()}-${end.format("h:mm a").lowercase()}) ${start.format("dd MMM yy")}"
                AppStore.createChallenge(
                    name, duration, start, schedule,
                    tasks.map { ChallengeTask(id = "", title = it.title, timerSeconds = it.seconds, sourceTaskId = it.sourceTaskId) },
                )
                if (schedule) {
                    toast(context, "📅 Challenge scheduled for ${start.format("h:mm a")} on ${start.format("dd MMM yy")}!")
                } else {
                    Sounds.start()
                    toast(context, "🚀 Challenge Started!")
                }
                onClose()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(if (schedule) Icons.Filled.EventRepeat else Icons.Filled.SportsMartialArts, null)
            Spacer(Modifier.width(8.dp))
            Text(if (schedule) "Schedule Challenge" else "Start Challenge", fontWeight = FontWeight.SemiBold)
        }
    }
    if (pickDate) DatePickerModal(date, { pickDate = false }) { date = it }
    if (pickTime) TimePickerModal(time, { pickTime = false }) { time = it }
}

// ───────────────────────── History ─────────────────────────

@Composable
fun ChallengeHistoryScreen(data: AppData, onBack: () -> Unit) {
    val finished = data.challenges
        .filter { it.status == ChallengeStatus.COMPLETED || it.status == ChallengeStatus.FAILED }
        .sortedByDescending { it.startedAt }
    val won = finished.count { it.status == ChallengeStatus.COMPLETED }
    val lost = finished.size - won
    val rate = if (finished.isNotEmpty()) ((won * 100.0) / finished.size).roundToInt() else 0
    var bestRun = 0
    var run = 0
    finished.sortedBy { it.startedAt }.forEach { if (it.status == ChallengeStatus.COMPLETED) { run++; bestRun = max(bestRun, run) } else run = 0 }
    val totalMinutes = finished.sumOf { c -> ((c.endedAt() ?: c.startedAt) - c.startedAt - c.totalPausedMs).coerceAtLeast(0) } / 60_000
    val tasksDone = finished.sumOf { c -> c.tasks.count { it.completed } }
    var confirm by remember { mutableStateOf<Challenge?>(null) }

    Screen {
        ScreenHeader("Challenge History", "${finished.size} challenges", Icons.Filled.History, Brand.purple, onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            MiniStat("$won", "Won", Pal.c.primary, Modifier.weight(1f))
            MiniStat("$lost", "Lost", Pal.c.destructive, Modifier.weight(1f))
            MiniStat("$rate%", "Win rate", Brand.blue, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            MiniStat("$bestRun", "Best win streak", Brand.amber, Modifier.weight(1f))
            MiniStat("$tasksDone", "Tasks done", Brand.green, Modifier.weight(1f))
            MiniStat("${totalMinutes / 60}h ${totalMinutes % 60}m", "Focused", Brand.purple, Modifier.weight(1f))
        }
        if (finished.isEmpty()) {
            Text("No finished challenges yet.", color = Pal.c.mutedForeground, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), textAlign = TextAlign.Center)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            finished.forEach { c ->
                val w = c.status == ChallengeStatus.COMPLETED
                val col = if (w) Pal.c.primary else Pal.c.destructive
                AppCard(Modifier.fillMaxWidth(), borderColor = col.copy(alpha = .25f), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (w) "🏆" else "💀", fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pal.c.foreground)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Pill(if (w) "WON" else "LOST", bg = col.copy(alpha = .15f), fg = col)
                                Spacer(Modifier.width(6.dp))
                                DifficultyBadge(c.difficulty(), showScore = false)
                            }
                            Text(
                                "${c.tasks.count { it.completed }}/${c.tasks.size} tasks · ${c.durationMinutes} min",
                                fontSize = 11.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        IconButton(onClick = { confirm = c }) { Icon(Icons.Filled.Delete, "Delete", tint = Pal.c.mutedForeground, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
    confirm?.let { c ->
        com.progresspal.app.ui.components.ConfirmDeleteDialog("Delete challenge?", c.name, { confirm = null }) { AppStore.deleteChallenge(c.id) }
    }
}

@Composable
private fun MiniStat(value: String, label: String, color: Color, modifier: Modifier) {
    AppCard(modifier, padding = 10.dp, borderColor = color.copy(alpha = .2f), background = color.copy(alpha = .06f)) {
        Text(value, style = NumberStyle, fontSize = 18.sp, color = color, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(label, fontSize = 10.sp, color = Pal.c.mutedForeground, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}
