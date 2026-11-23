package com.progresspal.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progresspal.app.data.OExam
import com.progresspal.app.data.OQuestion
import com.progresspal.app.data.ORTHO_CHAPTERS
import com.progresspal.app.data.ORTHO_RANKS
import com.progresspal.app.data.OrthoCloud
import com.progresspal.app.data.OrthoExam
import com.progresspal.app.data.SimpleTimeTracker
import com.progresspal.app.ui.components.Sounds
import com.progresspal.app.ui.components.toast
import com.progresspal.app.ui.theme.Pal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val Teal = Color(0xFF14B8A6)
private val TealDark = Color(0xFF0F766E)
private val Amber = Color(0xFFF59E0B)
private val Good = Color(0xFF22C55E)
private val Bad = Color(0xFFEF4444)

/** Orthopaedic Genius (OrthoPrep): AI MCQ exams at FRCS / MS / FCPS standard. */
@Composable
fun OrthoGeniusScreen(onBack: () -> Unit) {
    var view by rememberSaveable { mutableStateOf("home") }
    var examId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewId by rememberSaveable { mutableStateOf<String?>(null) }
    val goHome = { view = "home" }
    // Time spent here is recorded as "Study with Orthopaedic Genius App" in Simple Time Tracker.
    SttRecording(LocalContext.current, "Orthopaedic Genius")

    when (view) {
        "exam" -> examId?.let { ExamView(it) { view = "home" } } ?: goHome()
        "review" -> { BackHandler { goHome() }; reviewId?.let { ReviewView(it, goHome) } ?: goHome() }
        "analytics" -> { BackHandler { goHome() }; OrthoAnalytics(goHome) }
        "settings" -> { BackHandler { goHome() }; ChapterSettings(goHome) }
        else -> Dashboard(
            onBack = onBack,
            onExam = { examId = it; view = "exam" },
            onReview = { reviewId = it; view = "review" },
            onAnalytics = { view = "analytics" },
            onSettings = { view = "settings" },
        )
    }
}

// ---------------------------------------------------------------- Dashboard

@Composable
private fun Dashboard(onBack: () -> Unit, onExam: (String) -> Unit, onReview: (String) -> Unit, onAnalytics: () -> Unit, onSettings: () -> Unit) {
    val d by OrthoExam.data.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var generating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<OExam?>(null) }
    LaunchedEffect(Unit) { OrthoExam.openExam() }
    val cloud by OrthoCloud.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { OrthoCloud.sync() }
    val open = d.exams.firstOrNull { !it.completed && it.questions.isNotEmpty() && System.currentTimeMillis() - it.startedAt < OrthoExam.EXAM_SECONDS * 1000L }

    val answered = OrthoExam.answered(d)
    val total = answered.size
    val unique = answered.map { it.hash }.toSet().size
    val daily = OrthoExam.daily(d)
    val avgPerDay = if (daily.isEmpty()) 0 else total / daily.size
    val today = daily[LocalDate.now()] ?: 0
    val done = d.exams.filter { it.completed && it.questions.isNotEmpty() }
    val avgScore = if (done.isEmpty()) 0 else done.sumOf { it.scorePct } / done.size

    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(TealDark, Color(0xFF134E4A)))).padding(start = 4.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text("🩺", fontSize = 22.sp)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text("Orthopaedic Genius", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Text("AI MCQs · FRCS, MS & FCPS standard", fontSize = 11.sp, color = Color.White.copy(alpha = .7f))
                }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Chapters", tint = Color.White) }
                IconButton(onClick = onAnalytics) { Icon(Icons.Filled.BarChart, "Analytics", tint = Color.White) }
            }
            Box(Modifier.padding(horizontal = 12.dp)) { RankBadge(total) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountCard(cloud, ctx)
            DailyGoalCard(today)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OStat("✅", "$total", "Total solved", Modifier.weight(1f))
                OStat("🧠", "$unique", "Unique", Modifier.weight(1f))
                OStat("📅", "$avgPerDay", "Avg / day", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OStat("📝", "${done.size}", "Exams", Modifier.weight(1f))
                OStat("🎯", "$avgScore%", "Avg score", Modifier.weight(1f))
                OStat("📚", "${OrthoExam.selectedChapters(d).size}/${ORTHO_CHAPTERS.size}", "Chapters", Modifier.weight(1f), onSettings)
            }

            if (open != null) {
                BigButton("▶  Resume Exam #${open.number}", Amber) { onExam(open.id) }
            }
            BigButton(if (generating) "Generating questions…" else "＋  Generate New Exam", Teal, enabled = !generating) {
                generating = true
                scope.launch {
                    try {
                        val e = OrthoExam.generate()
                        onExam(e.id)
                    } catch (t: Throwable) {
                        toast(ctx, "Couldn't generate questions: ${t.message ?: "check your internet"}")
                    } finally { generating = false }
                }
            }
            if (generating) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Teal, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("AI is creating FRCS/MS standard MCQs…", fontSize = 12.sp, color = Pal.c.mutedForeground)
            }

            Text("Exam History", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Pal.c.foreground, modifier = Modifier.padding(top = 6.dp))
            if (done.isEmpty()) Text("No exams yet. Start your first exam!", Modifier.fillMaxWidth().padding(vertical = 24.dp), color = Pal.c.mutedForeground, textAlign = TextAlign.Center)
            done.forEach { e -> ExamCard(e, { onReview(e.id) }, if (OrthoCloud.signedIn) null else ({ deleting = e })) }
            Spacer(Modifier.height(24.dp))
        }
    }

    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null }, containerColor = Pal.c.card,
            title = { Text("Delete Exam #${e.number}?", color = Pal.c.foreground) },
            text = { Text("Its ${e.questions.count { it.answeredAt != null }} answers will no longer count in your stats.", color = Pal.c.foreground) },
            confirmButton = { TextButton(onClick = { OrthoExam.deleteExam(e.id); deleting = null }) { Text("Delete", color = Pal.c.destructive) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccountCard(st: OrthoCloud.Status, ctx: Context) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!OrthoCloud.signedIn) {
            Column(Modifier.weight(1f)) {
                Text("Use your OrthoPrep account", fontWeight = FontWeight.SemiBold, color = Pal.c.foreground, fontSize = 14.sp)
                Text("Sign in to get your web exams, answers and stats here and keep both in sync", fontSize = 11.sp, color = Pal.c.mutedForeground)
            }
            Text(
                "G  Sign in", Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Color(0xFF747775), RoundedCornerShape(10.dp))
                    .clickable { OrthoCloud.startSignIn(ctx) }.padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color(0xFF1F1F1F), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            )
        } else {
            Column(Modifier.weight(1f)) {
                Text("☁ ${st.email ?: "Signed in"}", fontWeight = FontWeight.SemiBold, color = Pal.c.foreground, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        st.syncing -> "Syncing…"
                        st.error != null -> "Sync failed: ${st.error.take(60)}"
                        st.lastSync > 0 -> "Synced with the OrthoPrep web app"
                        else -> "Synced with your Google account"
                    },
                    fontSize = 11.sp, color = if (st.error != null && !st.syncing) Bad else Pal.c.mutedForeground, maxLines = 2,
                )
            }
            if (st.syncing) CircularProgressIndicator(Modifier.size(18.dp), color = Teal, strokeWidth = 2.dp)
            else TextButton(onClick = { OrthoCloud.sync() }) { Text("Sync", fontSize = 12.sp) }
            TextButton(onClick = { OrthoCloud.signOut() }) { Text("Sign out", fontSize = 12.sp, color = Pal.c.mutedForeground) }
        }
    }
}

@Composable
private fun RankBadge(total: Int) {
    val achieved = ORTHO_RANKS.lastOrNull { total >= it.target }
    val next = ORTHO_RANKS.firstOrNull { total < it.target }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .12f)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${achieved?.emoji ?: "🩺"} ${achieved?.title ?: "Medical Student"}", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (next != null) Text("Next: ${next.emoji} ${next.title}", color = Color.White.copy(alpha = .6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (next != null) {
            val prev = achieved?.target ?: 0
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BrightBar((total - prev).toFloat() / (next.target - prev), listOf(Amber, Color(0xFFFFD54F)), 8) }
                Spacer(Modifier.width(8.dp))
                Text("$total/${next.target}", color = Color.White.copy(alpha = .7f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun DailyGoalCard(today: Int) {
    val d by OrthoExam.data.collectAsStateWithLifecycle()
    val goal = d.dailyGoal
    var editing by remember { mutableStateOf(false) }
    var temp by remember { mutableIntStateOf(goal) }
    val pct = if (goal > 0) (today * 100 / goal).coerceAtMost(100) else 0
    val complete = today >= goal
    val streak = OrthoExam.streak(d)
    LaunchedEffect(streak) { OrthoExam.recordBestStreak(streak) }
    val color = if (complete) Amber else Teal

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (complete) "🔥" else "🎯", fontSize = 20.sp)
            Spacer(Modifier.width(6.dp))
            Text("Today's Goal", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Pal.c.foreground)
            if (!editing) TextButton(onClick = { temp = goal; editing = true }) { Text("Edit goal", fontSize = 12.sp) }
            else {
                TextButton(onClick = { temp = (temp - 10).coerceAtLeast(10) }) { Text("−") }
                Text("$temp", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
                TextButton(onClick = { temp += 10 }) { Text("+") }
                TextButton(onClick = { OrthoExam.setGoal(temp); editing = false }) { Text("✓") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                val track = Pal.c.muted
                Canvas(Modifier.size(70.dp)) {
                    val st = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(track, 0f, 360f, false, style = st)
                    drawArc(color, -90f, 360f * pct / 100f, false, style = st)
                }
                Text("$pct%", fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$today", fontWeight = FontWeight.Black, fontSize = 26.sp, color = Pal.c.foreground)
                    Spacer(Modifier.weight(1f))
                    Text("/ $goal questions", fontSize = 13.sp, color = Pal.c.mutedForeground)
                }
                BrightBar(pct / 100f, listOf(color, color.copy(alpha = .7f)), 10)
                Text(
                    if (complete) "🎉 Goal reached! ${if (today > goal) "+${today - goal} bonus" else "Great job!"}" else "${goal - today} more to go — keep going! 💪",
                    fontSize = 12.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val hot = streak >= 3
            Text(
                "⚡ $streak day streak " + when { streak == 0 -> "— start today!"; streak < 3 -> "— keep it up!"; streak < 7 -> "🔥"; streak < 30 -> "🚀"; else -> "👑" },
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (hot) Amber.copy(alpha = .15f) else Pal.c.muted).padding(8.dp),
                color = if (hot) Amber else Pal.c.foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            Text("🏆 ${maxOf(d.bestStreak, streak)} best", Modifier.clip(RoundedCornerShape(10.dp)).background(Pal.c.muted).padding(8.dp), color = Pal.c.foreground, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ExamCard(e: OExam, onReview: () -> Unit, onDelete: (() -> Unit)?) {
    val pct = e.scorePct
    val col = when { pct >= 70 -> Good; pct >= 50 -> Amber; else -> Bad }
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Text("$pct%", color = col, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Exam #${e.number}", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
            Text(Instant.ofEpochMilli(e.startedAt).atZone(ZoneId.systemDefault()).format(fmt), fontSize = 11.sp, color = Pal.c.mutedForeground)
            Text("✓ ${e.correctCount}   ✗ ${e.wrongCount}   ⏱ ${mmss(e.timeTakenS ?: 0)}", fontSize = 12.sp, color = Pal.c.foreground)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (e.wrongCount > 0) TextButton(onClick = onReview) { Text("Review wrong", fontSize = 12.sp) }
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", fontSize = 11.sp, color = Pal.c.mutedForeground) }
        }
    }
}

// ---------------------------------------------------------------- Exam

@Composable
private fun ExamView(examId: String, onExit: () -> Unit) {
    val d by OrthoExam.data.collectAsStateWithLifecycle()
    val exam = d.exams.firstOrNull { it.id == examId } ?: run { LaunchedEffect(Unit) { onExit() }; return }
    val ctx = LocalContext.current
    var idx by rememberSaveable { mutableIntStateOf(exam.questions.indexOfFirst { it.userAnswer == null }.let { if (it < 0) exam.questions.size - 1 else it }) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var confirmQuit by remember { mutableStateOf(false) }
    var celebration by remember { mutableStateOf<Pair<String, String>?>(null) }
    val left = (OrthoExam.EXAM_SECONDS - ((now - exam.startedAt) / 1000).toInt()).coerceAtLeast(0)

    LaunchedEffect(exam.completed) {
        while (!exam.completed) {
            now = System.currentTimeMillis()
            if (now - exam.startedAt >= OrthoExam.EXAM_SECONDS * 1000L) { OrthoExam.complete(exam.id); break }
            delay(500)
        }
    }
    BackHandler { if (exam.completed) onExit() else confirmQuit = true }

    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        if (exam.completed) { ResultView(exam, onExit); return@Column }
        val q = exam.questions.getOrNull(idx) ?: return@Column
        val answered = q.userAnswer != null
        val doneCount = exam.questions.count { it.userAnswer != null }

        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmQuit = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.c.foreground) }
            Text("Question ${idx + 1}/${exam.questions.size}", Modifier.weight(1f), color = Pal.c.mutedForeground, fontWeight = FontWeight.Medium)
            Text("⏱ ${mmss(left)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (left < 60) Bad else Pal.c.foreground, modifier = Modifier.padding(end = 12.dp))
        }
        Column(Modifier.padding(horizontal = 14.dp)) {
            val timePct = (OrthoExam.EXAM_SECONDS - left) * 100 / OrthoExam.EXAM_SECONDS
            LabeledBar("Time", timePct, if (left < 60) Bad else Amber)
            LabeledBar("Progress", doneCount * 100 / exam.questions.size, Teal)
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("✓ ${exam.correctCount}", color = Good, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Text("✗ ${exam.wrongCount}", color = Bad, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                if (q.topic.isNotBlank()) Text(q.topic, Modifier.clip(RoundedCornerShape(50)).background(Teal.copy(alpha = .15f)).padding(horizontal = 10.dp, vertical = 3.dp), color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(q.text, color = Pal.c.foreground, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp)
            }
            listOf("A", "B", "C", "D").forEach { k ->
                OptionRow(k, q.option(k), q, enabled = !answered) {
                    val right = OrthoExam.answer(exam.id, idx, k)
                    if (right) Sounds.done(ctx) else Sounds.uncheck()
                    celebration = checkCelebration()
                }
            }
            if (answered) {
                Explanation(q, ctx)
                BigButton(if (idx + 1 >= exam.questions.size) "Finish Exam  →" else "Next Question  →", Teal) {
                    if (idx + 1 >= exam.questions.size) OrthoExam.complete(exam.id) else idx++
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmQuit) AlertDialog(
        onDismissRequest = { confirmQuit = false }, containerColor = Pal.c.card,
        title = { Text("Leave the exam?", color = Pal.c.foreground) },
        text = { Text("Finish now to save the score, or keep it open and resume later (the 10-minute timer keeps running).", color = Pal.c.foreground) },
        confirmButton = { TextButton(onClick = { confirmQuit = false; OrthoExam.complete(exam.id) }) { Text("Finish exam") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmQuit = false; onExit() }) { Text("Resume later") }
                TextButton(onClick = { confirmQuit = false }) { Text("Stay") }
            }
        },
    )
    celebration?.let { (title, sub) ->
        AlertDialog(
            onDismissRequest = { celebration = null }, containerColor = Pal.c.card,
            title = { Text(title, color = Amber, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { Text(sub, color = Pal.c.foreground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { celebration = null }) { Text("Keep going!") } },
        )
    }
}

/** Daily goal just reached or a career rank unlocked → (title, subtitle), once each. */
private fun checkCelebration(): Pair<String, String>? {
    val d = OrthoExam.data.value
    val today = OrthoExam.todaySolved(d)
    val key = "daily_${LocalDate.now()}"
    if (today >= d.dailyGoal && key !in d.celebrated) {
        OrthoExam.markCelebrated(key); Sounds.won()
        return "🎯 Daily Goal Reached!" to "You solved $today questions today!"
    }
    val total = OrthoExam.answered(d).size
    ORTHO_RANKS.lastOrNull { total >= it.target }?.let { r ->
        val k = "milestone_${r.target}"
        if (k !in d.celebrated) {
            OrthoExam.markCelebrated(k); Sounds.won()
            return "${r.emoji} ${r.title} Unlocked!" to "%,d questions solved!".format(r.target)
        }
    }
    return null
}

@Composable
private fun OptionRow(k: String, text: String, q: OQuestion, enabled: Boolean, onPick: () -> Unit) {
    val answered = q.userAnswer != null
    val (border, bg) = when {
        answered && k == q.correct -> Good to Good.copy(alpha = .10f)
        answered && k == q.userAnswer -> Bad to Bad.copy(alpha = .10f)
        else -> Pal.c.border to Pal.c.card
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).border(2.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onPick).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val badge = when { answered && k == q.correct -> Good; answered && k == q.userAnswer -> Bad; else -> Pal.c.muted }
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(badge), contentAlignment = Alignment.Center) {
            Text(k, fontWeight = FontWeight.Bold, color = if (badge == Pal.c.muted) Pal.c.foreground else Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f).padding(top = 4.dp), color = Pal.c.foreground.copy(alpha = if (answered && k != q.correct && k != q.userAnswer) .5f else 1f), fontSize = 14.sp, lineHeight = 20.sp)
        if (answered && k == q.correct) Text("✓", color = Good, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (answered && k == q.userAnswer && k != q.correct) Text("✗", color = Bad, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun Explanation(q: OQuestion, ctx: Context) {
    val right = q.userAnswer == q.correct
    val col = if (right) Good else Bad
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = .07f)).border(1.dp, col.copy(alpha = .3f), RoundedCornerShape(12.dp)).padding(14.dp)) {
        if (q.userAnswer != null) Text(if (right) "✅ Correct!" else "❌ Incorrect — answer ${q.correct}", fontWeight = FontWeight.SemiBold, color = Pal.c.foreground)
        Spacer(Modifier.height(4.dp))
        Text(q.explanation, color = Pal.c.mutedForeground, fontSize = 13.sp, lineHeight = 19.sp)
        Row(Modifier.padding(top = 6.dp)) {
            val topic = if (q.topic.isNotBlank()) q.topic + " " else ""
            TextButton(onClick = { openUrl(ctx, "https://www.google.com/search?tbm=isch&q=" + Uri.encode(topic + q.text.take(80) + " orthopedics")) }) { Text("🖼️ Google Images", fontSize = 12.sp, color = Teal) }
            TextButton(onClick = { openUrl(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(topic + q.text.take(60) + " orthopedics")) }) { Text("▶️ YouTube Videos", fontSize = 12.sp, color = Bad) }
        }
    }
}

@Composable
private fun ResultView(e: OExam, onExit: () -> Unit) {
    val pct = e.scorePct
    val col = when { pct >= 70 -> Good; pct >= 50 -> Amber; else -> Bad }
    LaunchedEffect(e.id) { Sounds.done() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$pct%", fontSize = 60.sp, fontWeight = FontWeight.Black, color = col)
        Text("Exam #${e.number} Complete!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Pal.c.foreground)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("✅", fontSize = 28.sp); Text("${e.correctCount}", fontWeight = FontWeight.Bold, color = Pal.c.foreground, fontSize = 18.sp); Text("Correct", fontSize = 12.sp, color = Pal.c.mutedForeground) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("❌", fontSize = 28.sp); Text("${e.wrongCount}", fontWeight = FontWeight.Bold, color = Pal.c.foreground, fontSize = 18.sp); Text("Wrong", fontSize = 12.sp, color = Pal.c.mutedForeground) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("⏱", fontSize = 28.sp); Text(mmss(e.timeTakenS ?: 0), fontWeight = FontWeight.Bold, color = Pal.c.foreground, fontSize = 18.sp); Text("Time", fontSize = 12.sp, color = Pal.c.mutedForeground) }
        }
        Spacer(Modifier.height(24.dp))
        BigButton("Back to Dashboard", Teal, onClick = onExit)
    }
}

/**
 * Runs the "Study with Orthopaedic Genius App" activity in Simple Time Tracker while Orthopaedic Genius
 * is open (falls back to one named with Ortho / MCQ / Exam), and stops it on leaving.
 */
@Composable
private fun SttRecording(ctx: Context, comment: String, active: Boolean = true) {
    val act = remember {
        val acts = SimpleTimeTracker.cache.value.activities
        listOf("orthopaedic genius", "orthopedic genius", "genius", "ortho", "mcq", "exam", "question").firstNotNullOfOrNull { w -> acts.firstOrNull { it.name.contains(w, ignoreCase = true) } }
    }
    var startedByUs by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (act == null) toast(ctx, "Tip: add a \"Study with Orthopaedic Genius App\" activity in Simple Time Tracker to record study time") }
    LaunchedEffect(active) {
        if (active && act != null && !SimpleTimeTracker.isRunning(act.id)) { SimpleTimeTracker.start(act, comment); startedByUs = true }
        if (!active && startedByUs && act != null && SimpleTimeTracker.isRunning(act.id)) { SimpleTimeTracker.toggle(act); startedByUs = false }
    }
    DisposableEffect(Unit) {
        onDispose { if (startedByUs && act != null && SimpleTimeTracker.isRunning(act.id)) SimpleTimeTracker.toggle(act) }
    }
}

// ---------------------------------------------------------------- Review

@Composable
private fun ReviewView(examId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val exam = OrthoExam.exam(examId)
    val wrong = exam?.questions?.filter { it.isCorrect == false }.orEmpty()
    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        Header("Review Wrong Answers", exam?.let { "Exam #${it.number}" } ?: "", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (wrong.isEmpty()) Text("No wrong answers to review.", color = Pal.c.mutedForeground)
            wrong.forEachIndexed { i, q ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (q.topic.isNotBlank()) Text(q.topic, color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("${i + 1}. ${q.text}", color = Pal.c.foreground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    listOf("A", "B", "C", "D").forEach { k -> OptionRow(k, q.option(k), q, enabled = false) {} }
                    Explanation(q, ctx)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- Analytics

@Composable
private fun OrthoAnalytics(onBack: () -> Unit) {
    val d by OrthoExam.data.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableIntStateOf(0) }
    val daily = OrthoExam.daily(d)
    val total = OrthoExam.answered(d).size
    val exams = d.exams.filter { it.completed && it.questions.isNotEmpty() }.sortedBy { it.number }
    val weak = OrthoExam.weakTopics(d)

    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        Header("Analytics", "Orthopaedic Genius", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DailyGoalCard(daily[LocalDate.now()] ?: 0)

            Card {
                Text("Questions Solved", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Daily", "Weekly", "Monthly", "Yearly").forEachIndexed { i, l ->
                        Text(l, Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (period == i) Teal else Pal.c.muted).clickable { period = i }.padding(vertical = 6.dp),
                            color = if (period == i) Color.White else Pal.c.foreground, textAlign = TextAlign.Center, fontSize = 12.sp)
                    }
                }
                val today = LocalDate.now()
                val bars: List<Triple<String, Float, Color>> = when (period) {
                    0 -> (13 downTo 0).map { today.minusDays(it.toLong()) }.map { Triple(it.format(DateTimeFormatter.ofPattern("d MMM")), (daily[it] ?: 0).toFloat(), Teal) }
                    1 -> daily.entries.groupBy { it.key.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY)) }.toSortedMap().entries.toList().takeLast(8)
                        .map { (k, v) -> Triple(k.format(DateTimeFormatter.ofPattern("d MMM")), v.sumOf { it.value }.toFloat(), Teal) }
                    2 -> daily.entries.groupBy { it.key.withDayOfMonth(1) }.toSortedMap().entries.toList().takeLast(12)
                        .map { (k, v) -> Triple(k.format(DateTimeFormatter.ofPattern("MMM yy")), v.sumOf { it.value }.toFloat(), Teal) }
                    else -> daily.entries.groupBy { it.key.year }.toSortedMap().map { (k, v) -> Triple("$k", v.sumOf { it.value }.toFloat(), Teal) }
                }
                Bars(bars, maxOverride = null)
            }

            Card {
                Text("Exam Performance Trend", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
                Spacer(Modifier.height(8.dp))
                Bars(exams.takeLast(30).map { e -> Triple("#${e.number}", e.scorePct.toFloat(), when { e.scorePct >= 70 -> Good; e.scorePct >= 50 -> Amber; else -> Bad }) }, maxOverride = 100f, suffix = "%")
            }

            Card {
                Text("Time Spent", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
                val secs = exams.sumOf { it.timeTakenS ?: 0 }
                val avg = if (exams.isEmpty()) 0 else secs / exams.size
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OStat("⏱", "${secs / 3600}h ${(secs % 3600) / 60}m", "Total in exams", Modifier.weight(1f))
                    OStat("📝", mmss(avg), "Avg per exam", Modifier.weight(1f))
                    OStat("⚡", if (total == 0) "–" else "${secs / total.coerceAtLeast(1)}s", "Per question", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Bars(exams.takeLast(30).map { Triple("#${it.number}", (it.timeTakenS ?: 0) / 60f, Amber) }, maxOverride = 10f, suffix = "m")
            }

            Card {
                Text("🏆 Career Milestones", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
                Spacer(Modifier.height(6.dp))
                ORTHO_RANKS.forEach { r ->
                    val got = total >= r.target
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = if (got) Pal.c.foreground else Pal.c.mutedForeground, fontWeight = if (got) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                            BrightBar((total.toFloat() / r.target).coerceAtMost(1f), if (got) listOf(Amber, Color(0xFFFFD54F)) else listOf(Teal, Teal), 6)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (got) "✓" else "%,d".format(r.target), color = if (got) Good else Pal.c.mutedForeground, fontSize = 12.sp)
                    }
                }
            }

            Card {
                Text("⚠️ Weak Topics", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
                Spacer(Modifier.height(6.dp))
                if (weak.isEmpty()) Text("No wrong answers yet — keep it up! 🎉", color = Pal.c.mutedForeground)
                weak.take(10).forEach { w ->
                    Row(Modifier.padding(top = 6.dp)) {
                        Text(w.topic, Modifier.weight(1f), color = Pal.c.foreground, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${w.wrong}/${w.total} wrong (${w.pct}%)", color = Pal.c.mutedForeground, fontSize = 11.sp)
                    }
                    BrightBar(w.pct / 100f, listOf(Bad, Color(0xFFFF8A80)), 6)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- Chapters

@Composable
private fun ChapterSettings(onBack: () -> Unit) {
    val d by OrthoExam.data.collectAsStateWithLifecycle()
    val sel = OrthoExam.selectedChapters(d)
    fun toggle(ids: List<String>, on: Boolean) = OrthoExam.setChapters(if (on) sel + ids else sel - ids.toSet())
    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        Header("Chapters", "${sel.size} of ${ORTHO_CHAPTERS.size} selected · questions come only from these", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            listOf(true to "Apley & Solomon's System of Orthopaedics and Trauma (10th Ed.)", false to "Campbell's Operative Orthopaedics (14th Ed.)").forEach { (apley, book) ->
                val chs = ORTHO_CHAPTERS.filter { it.apley == apley }
                val all = chs.all { it.id in sel }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(book, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Teal, fontSize = 14.sp)
                    TextButton(onClick = { toggle(chs.map { it.id }, !all) }) { Text(if (all) "Clear" else "Select all", fontSize = 12.sp) }
                }
                chs.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { toggle(listOf(c.id), c.id !in sel) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = c.id in sel, onCheckedChange = { toggle(listOf(c.id), it) })
                        Text(c.name, color = Pal.c.foreground, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- Bits

@Composable
private fun Header(title: String, sub: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.c.foreground) }
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Pal.c.foreground)
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Pal.c.mutedForeground)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(14.dp)).padding(14.dp)) { content() }
}

@Composable
private fun OStat(icon: String, value: String, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Teal.copy(alpha = .15f), Pal.c.card)))
            .border(1.dp, Teal.copy(alpha = .3f), RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 18.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Pal.c.foreground, maxLines = 1)
        Text(label, fontSize = 10.sp, color = Pal.c.mutedForeground, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BigButton(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (enabled) color else color.copy(alpha = .5f))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 15.dp),
        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
    )
}

@Composable
private fun LabeledBar(label: String, pct: Int, color: Color) {
    Row(Modifier.padding(top = 4.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 11.sp, color = Pal.c.mutedForeground)
        Text("$pct%", fontSize = 11.sp, color = Pal.c.mutedForeground)
    }
    BrightBar(pct / 100f, listOf(color, color.copy(alpha = .7f)), 7)
}

/** Simple vertical bar chart with value labels; scrolls sideways when long. */
@Composable
private fun Bars(bars: List<Triple<String, Float, Color>>, maxOverride: Float?, suffix: String = "") {
    if (bars.isEmpty() || bars.all { it.second == 0f }) {
        Text("No data yet. Take some exams!", Modifier.fillMaxWidth().padding(vertical = 30.dp), color = Pal.c.mutedForeground, textAlign = TextAlign.Center)
        return
    }
    val max = maxOverride ?: bars.maxOf { it.second }.coerceAtLeast(1f)
    val track = Pal.c.muted
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { (label, v, col) ->
            Column(Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v == 0f) "" else (if (v % 1f == 0f) "${v.toInt()}" else "%.1f".format(v)) + suffix, fontSize = 9.sp, color = Pal.c.foreground, maxLines = 1)
                Canvas(Modifier.width(22.dp).height(140.dp)) {
                    drawRoundRect(track, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
                    val h = size.height * (v / max).coerceIn(0f, 1f)
                    drawRoundRect(col, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
                }
                Text(label, fontSize = 9.sp, color = Pal.c.mutedForeground, maxLines = 1)
            }
        }
    }
}

private fun mmss(s: Int) = "%d:%02d".format(s / 60, s % 60)

private fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
