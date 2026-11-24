package com.progresspal.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progresspal.app.data.SimpleTimeTracker
import com.progresspal.app.data.Zikir
import com.progresspal.app.data.ZikirData
import com.progresspal.app.data.Zikr
import com.progresspal.app.ui.components.Sounds
import com.progresspal.app.ui.theme.Pal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

private val ZGreen = Color(0xFF10B981)
private val ZGold = Color(0xFFF5C542)
private val ZDeep = Color(0xFF064E3B)

/** Colour themes for the tasbih: background top, background bottom, accent. */
private val ZTHEMES = listOf(
    Triple(Color(0xFF0B5D45), Color(0xFF053B2C), Color(0xFFF5C542)),
    Triple(Color(0xFF14325C), Color(0xFF0A1A33), Color(0xFF7DD3FC)),
    Triple(Color(0xFF3B1F5C), Color(0xFF1E0F33), Color(0xFFE9A8FF)),
    Triple(Color(0xFF5C1F2A), Color(0xFF330F16), Color(0xFFFFC2A8)),
    Triple(Color(0xFF1C1C1C), Color(0xFF000000), Color(0xFFD4AF37)),
)
private val ZTHEME_NAMES = listOf("Green", "Night", "Purple", "Maroon", "Black & Gold")

/** Zikir tracker: tasbih counter, groups, history and hourly…yearly progress. */
@Composable
fun ZikirScreen(onBack: () -> Unit) {
    val d by Zikir.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var full by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    ZikirStt()
    DisposableEffect(Unit) { onDispose { Zikir.setPaused(false); Zikir.endSession() } }

    val export = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Zikir.exportJson().toByteArray()) } }
            .onSuccess { com.progresspal.app.ui.components.toast(ctx, "Zikir backup saved") }
    }
    val import = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = runCatching { ctx.contentResolver.openInputStream(uri)?.use { Zikir.importJson(it.readBytes().decodeToString()) } ?: false }.getOrDefault(false)
            com.progresspal.app.ui.components.toast(ctx, if (ok) "Zikir data restored" else "That file is not a Zikir backup")
        }
    }

    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        if (!(full && tab == 0)) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.c.foreground) }
                Column(Modifier.weight(1f)) {
                    Text("Zikir Tracker", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Pal.c.foreground)
                    Text("Today ${"%,d".format(Zikir.todayFor(d, null))} · Lifetime ${"%,d".format(d.hourly.values.sum())}", fontSize = 12.sp, color = Pal.c.mutedForeground)
                }
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 22.sp, color = Pal.c.foreground) }
                    androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Back up to a file") }, onClick = { menu = false; export.launch("zikir-backup-${LocalDate.now()}.json") })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Restore from a backup") }, onClick = { menu = false; import.launch(arrayOf("application/json", "*/*")) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Tasbih", "Groups", "History", "Progress").forEachIndexed { i, l ->
                    Text(
                        l, Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (tab == i) ZGreen else Pal.c.muted)
                            .clickable { tab = i }.padding(vertical = 8.dp),
                        color = if (tab == i) Color.White else Pal.c.foreground, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        when (tab) {
            0 -> Counter(d, full) { full = it }
            1 -> GroupsTab(d) { tab = 0 }
            2 -> HistoryTab(d)
            else -> ZikirProgress(d)
        }
    }
}

/** Runs the Zikir activity in Simple Time Tracker from the first bead until leaving the screen. */
@Composable
private fun ZikirStt() {
    val act = remember {
        val acts = SimpleTimeTracker.cache.value.activities
        listOf("zikir", "zikr", "dhikr", "zikar", "jikir", "tasbih", "tasbeeh")
            .firstNotNullOfOrNull { w -> acts.firstOrNull { it.name.contains(w, ignoreCase = true) } }
    }
    val startedByUs = remember { mutableStateOf(false) }
    val d by Zikir.data.collectAsStateWithLifecycle()
    val first = remember { d.hourly.values.sum() }
    val total = d.hourly.values.sum()
    androidx.compose.runtime.LaunchedEffect(total) {
        if (act != null && !startedByUs.value && total > first && !SimpleTimeTracker.isRunning(act.id)) {
            SimpleTimeTracker.start(act, "Tasbih"); startedByUs.value = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (startedByUs.value && act != null && SimpleTimeTracker.isRunning(act.id)) SimpleTimeTracker.toggle(act) }
    }
}

// ───────────────────────── Counter ─────────────────────────

private fun clickTone() = runCatching { tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 40) }
private val tone by lazy { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 60) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Counter(d: ZikirData, full: Boolean, setFull: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val st = d.settings
    val z = d.zikrs.firstOrNull { it.id == d.selectedId } ?: d.zikrs.firstOrNull()
    var editing by remember { mutableStateOf<Zikr?>(null) }
    var adding by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(Zikir.paused) }
    var black by remember { mutableIntStateOf(0) } // 0 off, 1 with counter, 2 blank
    var recording by remember { mutableStateOf(false) }
    val recTaps = remember { mutableListOf<Long>() }
    var dialog by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }

    if (z == null) { Text("Add a zikir to start counting.", Modifier.padding(16.dp), color = Pal.c.mutedForeground); if (adding) ZikrDialog(null) { adding = false }; return }
    val (bgTop, bgBottom, accent) = ZTHEMES[st.theme.coerceIn(0, ZTHEMES.lastIndex)]
    val round = d.round[z.id] ?: 0
    val rounds = d.rounds[z.id] ?: 0
    val today = Zikir.todayFor(d, z.id)
    val todayAll = Zikir.todayFor(d, null)
    val group = d.groups.firstOrNull { it.id == d.activeGroupId }
    val groupItem = group?.items?.getOrNull(d.groupPos)
    val target = groupItem?.count ?: z.target
    val shown = if (group != null) d.groupCount else round
    val frac = if (target > 0) shown / target.toFloat() else 0f

    val count: () -> Unit = {
        if (!paused) {
            val done = Zikir.add(z.id, st.step)
            if (recording) recTaps += System.currentTimeMillis()
            if (d.vibrate) haptic.performHapticFeedback(if (done) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            if (done) Sounds.done() else if (st.sound) clickTone()
            if (Zikir.groupJustFinished) { Zikir.groupJustFinished = false; auto = false; com.progresspal.app.ui.components.toast(ctx, "✅ ${group?.name ?: "Group"} complete — MashaAllah!") }
        }
    }
    // Autoplay: one bead every autoMs while on.
    androidx.compose.runtime.LaunchedEffect(auto, paused, z.id) {
        while (auto && !paused) { kotlinx.coroutines.delay(st.autoMs.coerceAtLeast(150)); count() }
    }

    // Black screen: the whole screen counts; long-press to leave.
    if (black > 0) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { black = 0 }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = count, onLongClick = { black = 0 }),
                contentAlignment = Alignment.Center,
            ) {
                if (black == 1) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$shown", fontSize = (64 * st.fontScale).sp, color = Color(0xFF3A3A3A), fontWeight = FontWeight.Bold)
                    Text("long-press to exit", fontSize = 11.sp, color = Color(0xFF262626))
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bgTop, bgBottom)))) {
        // Tools
        if (st.tools) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ZTool(if (paused) "▶" else "⏸", if (paused) "Resume" else "Pause", paused, accent) { paused = !paused; Zikir.setPaused(paused); if (paused) auto = false }
                ZTool("−${st.step}", "Decrease", false, accent) { Zikir.add(z.id, -st.step) }
                ZTool("⟲", "Reset", false, accent) { dialog = "reset" }
                ZTool("⏯", "Autoplay", auto, accent) { auto = !auto; if (auto && paused) { paused = false; Zikir.setPaused(false) } }
                ZTool("●", if (recording) "Stop rec" else "Rec speed", recording, accent) {
                    if (!recording) { recTaps.clear(); recording = true; com.progresspal.app.ui.components.toast(ctx, "Recording your speed — count at your normal pace, then tap ● again") }
                    else {
                        recording = false
                        val gaps = recTaps.zipWithNext { a, b -> b - a }.filter { it in 100..10_000 }
                        if (gaps.size >= 3) { val avg = gaps.average().toLong(); Zikir.settings { it.copy(avgMs = avg, autoMs = avg) }; com.progresspal.app.ui.components.toast(ctx, "Average speed %.1f s per bead — autoplay uses it".format(avg / 1000f)) }
                        else com.progresspal.app.ui.components.toast(ctx, "Count a few more beads while recording")
                    }
                }
                ZTool("±", "Step ${st.step}", st.step != 1, accent) { dialog = "step" }
                ZTool("◎", "Target", false, accent) { editing = z }
                ZTool("⛶", if (full) "Exit full" else "Full", full, accent) { setFull(!full) }
                ZTool("◐", "Black+no.", false, accent) { black = 1 }
                ZTool("●", "Black", false, accent) { black = 2 }
                ZTool("📳", "Vibrate", d.vibrate, accent) { Zikir.setVibrate(!d.vibrate) }
                ZTool("🔊", "Sound", st.sound, accent) { Zikir.settings { it.copy(sound = !it.sound) } }
                ZTool("❀", "Beads", st.beads, accent) { Zikir.settings { it.copy(beads = !it.beads) } }
                ZTool("Tт", "Font", false, accent) { Zikir.settings { it.copy(fontScale = if (it.fontScale >= 1.6f) 0.8f else it.fontScale + 0.2f) } }
                ZTool("🎨", ZTHEME_NAMES[st.theme.coerceIn(0, ZTHEMES.lastIndex)], false, accent) { Zikir.settings { it.copy(theme = (it.theme + 1) % ZTHEMES.size) } }
                ZTool("⏱", "${st.autoMs / 1000f}s", false, accent) { dialog = "speed" }
                ZTool("ᐱ", "Hide tools", false, accent) { Zikir.settings { it.copy(tools = false) } }
            }
        } else Text("ᐯ tools", Modifier.align(Alignment.CenterHorizontally).clickable { Zikir.settings { it.copy(tools = true) } }.padding(6.dp), color = Color.White.copy(alpha = .6f), fontSize = 12.sp)

        // Zikir chips: tap to choose, long-press to edit
        if (!full) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            d.zikrs.sortedBy { it.order }.forEach { x ->
                val sel = x.id == z.id
                Text(
                    x.name, Modifier.clip(RoundedCornerShape(50)).background(if (sel) accent else Color.White.copy(alpha = .12f))
                        .combinedClickable(onClick = { if (group != null) Zikir.stopGroup(); Zikir.select(x.id) }, onLongClick = { editing = x })
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    color = if (sel) Color.Black else Color.White, fontSize = 13.sp, maxLines = 1,
                )
            }
            Text("＋ Add", Modifier.clip(RoundedCornerShape(50)).border(1.dp, accent, RoundedCornerShape(50)).clickable { adding = true }.padding(horizontal = 12.dp, vertical = 7.dp), color = accent, fontSize = 13.sp)
        }
        if (group != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("▶ ${group.name} · ${d.groupPos + 1}/${group.items.size}", Modifier.weight(1f), color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Stop group", Modifier.clickable { Zikir.stopGroup() }.padding(4.dp), color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
        }

        // Touch anywhere below to count
        Box(
            Modifier.fillMaxWidth().weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = count),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.weight(.6f))
                if (z.arabic.isNotBlank()) Text(z.arabic, fontSize = (28 * st.fontScale).sp, color = Color.White, textAlign = TextAlign.Center)
                Text(z.name, fontSize = (15 * st.fontScale).sp, color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center, maxLines = 2)
                Spacer(Modifier.weight(.4f))
                if (st.beads) Beads(shown, accent, Modifier.fillMaxWidth().height(110.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size((170 * st.fontScale.coerceAtMost(1.2f)).dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val s = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(Color.White.copy(alpha = .12f), 0f, 360f, false, style = s)
                        drawArc(accent, -90f, 360f * frac.coerceIn(0f, 1f), false, style = s)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$shown", fontSize = (60 * st.fontScale).sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
                        Text("of $target" + if (group == null) " · round ${rounds + 1}" else "", fontSize = 12.sp, color = Color.White.copy(alpha = .75f))
                    }
                }
                val ms = Zikir.sessionMs().also { now }
                Text("%02d:%02d:%02d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60) + if (paused) "  ⏸ paused" else if (auto) "  ⏯ autoplay" else "",
                    fontSize = 14.sp, color = Color.White.copy(alpha = .75f), modifier = Modifier.padding(top = 6.dp))
                Text("📅 Today: $today ${z.name}" + (if (z.dailyGoal > 0) " / ${z.dailyGoal}" else "") + " · all $todayAll", fontSize = 12.sp, color = Color.White.copy(alpha = .6f), textAlign = TextAlign.Center)
                Text(if (paused) "Paused — tap ▶ to resume" else "Touch anywhere to count", fontSize = 11.sp, color = Color.White.copy(alpha = .4f), modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }

    when (dialog) {
        "reset" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = Pal.c.card,
            title = { Text("Reset the counter?", color = Pal.c.foreground) },
            text = { Text("The current round starts again from 0. Counts already made stay in your history and charts.", color = Pal.c.foreground) },
            confirmButton = { TextButton(onClick = { Zikir.resetRound(z.id); dialog = null }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "step", "speed" -> {
            val isStep = dialog == "step"
            var v by remember { mutableStateOf(if (isStep) st.step.toString() else "%.1f".format(st.autoMs / 1000f)) }
            AlertDialog(
                onDismissRequest = { dialog = null }, containerColor = Pal.c.card,
                title = { Text(if (isStep) "Custom step" else "Autoplay speed", color = Pal.c.foreground) },
                text = {
                    Column {
                        Text(if (isStep) "How much each tap adds (− takes the same off)." else "Seconds between beads for autoplay" + if (st.avgMs > 0) " (your recorded speed: %.1f s)".format(st.avgMs / 1000f) else "", color = Pal.c.mutedForeground, fontSize = 13.sp)
                        OutlinedTextField(v, { v = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (isStep) v.toIntOrNull()?.takeIf { it > 0 }?.let { n -> Zikir.settings { it.copy(step = n.coerceAtMost(1000)) } }
                        else v.replace(',', '.').toFloatOrNull()?.takeIf { it > 0 }?.let { s -> Zikir.settings { it.copy(autoMs = (s * 1000).toLong().coerceIn(150, 60_000)) } }
                        dialog = null
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
    }
    if (adding) ZikrDialog(null, onDone = { adding = false })
    editing?.let { e -> ZikrDialog(e, onDone = { editing = null }) }
}

@Composable
private fun ZTool(icon: String, label: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(58.dp).clip(RoundedCornerShape(10.dp)).background(if (on) accent.copy(alpha = .28f) else Color.Transparent).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 18.sp, color = if (on) accent else Color.White, maxLines = 1)
        Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A string of tasbih beads along an arch; one bead slides over with each count. */
@Composable
private fun Beads(count: Int, accent: Color, modifier: Modifier) {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(count) { anim.snapTo(0f); anim.animateTo(1f, androidx.compose.animation.core.tween(220)) }
    Canvas(modifier) {
        val n = 13
        val cx = size.width / 2; val r = size.width * 0.42f; val cy = size.height + r * 0.55f
        val bead = size.height * 0.11f
        for (i in -1 until n) {
            val t = (i + anim.value) / (n - 1)                     // 0..1 along the arch
            val a = Math.toRadians((200 + 140 * t).toDouble())     // left → right over the top
            val x = cx + r * kotlin.math.cos(a).toFloat()
            val y = cy + r * kotlin.math.sin(a).toFloat() * 0.75f
            val fresh = t > 0.5f
            drawCircle(if (fresh) accent else Color.White.copy(alpha = .55f), radius = bead, center = Offset(x, y), style = Stroke(width = 3.dp.toPx()))
        }
    }
}

// ───────────────────────── Groups ─────────────────────────

@Composable
private fun GroupsTab(d: ZikirData, onStarted: () -> Unit) {
    var editing by remember { mutableStateOf<com.progresspal.app.data.ZGroup?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("A group recites several zikir one after another — the counter moves to the next when each count is reached.", fontSize = 12.sp, color = Pal.c.mutedForeground)
        d.groups.forEach { g ->
            ZCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Pal.c.foreground)
                    TextButton(onClick = { editing = g }) { Text("Edit") }
                    TextButton(onClick = { Zikir.startGroup(g.id); onStarted() }) { Text("▶ Start") }
                }
                g.items.forEachIndexed { i, it ->
                    val name = d.zikrs.firstOrNull { z -> z.id == it.zikrId }?.name ?: "(deleted zikir)"
                    Text("${i + 1}. $name × ${it.count}", fontSize = 13.sp, color = Pal.c.foreground)
                }
                val done = d.sessions.count { it.groupId == g.id }
                if (done > 0) Text("Recited in $done sittings", fontSize = 11.sp, color = Pal.c.mutedForeground)
            }
        }
        TextButton(onClick = { editing = com.progresspal.app.data.ZGroup(name = "") }) { Text("＋ New group") }
        Spacer(Modifier.height(24.dp))
    }
    editing?.let { g -> GroupDialog(d, g) { editing = null } }
}

@Composable
private fun GroupDialog(d: ZikirData, g: com.progresspal.app.data.ZGroup, onDone: () -> Unit) {
    var name by remember { mutableStateOf(g.name) }
    val items = remember { androidx.compose.runtime.mutableStateListOf(*g.items.toTypedArray()) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone, containerColor = Pal.c.card,
        title = { Text(if (g.name.isBlank()) "New group" else "Edit group", color = Pal.c.foreground) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                items.forEachIndexed { i, it ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.zikrs.firstOrNull { z -> z.id == it.zikrId }?.name ?: "?", Modifier.weight(1f), color = Pal.c.foreground, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        OutlinedTextField(it.count.toString(), { v -> v.toIntOrNull()?.let { n -> items[i] = it.copy(count = n.coerceAtLeast(1)) } }, singleLine = true, modifier = Modifier.width(80.dp))
                        TextButton(onClick = { items.removeAt(i) }) { Text("✕", color = Pal.c.destructive) }
                    }
                }
                if (picking) d.zikrs.sortedBy { it.order }.forEach { z ->
                    Text("＋ ${z.name}", Modifier.fillMaxWidth().clickable { items.add(com.progresspal.app.data.ZGroupItem(z.id, z.target)); picking = false }.padding(vertical = 6.dp), color = ZGreen, fontSize = 13.sp)
                } else TextButton(onClick = { picking = true }) { Text("＋ Add zikir") }
                if (g.name.isNotBlank()) TextButton(onClick = { Zikir.deleteGroup(g.id); onDone() }) { Text("Delete group", color = Pal.c.destructive) }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank() && items.isNotEmpty()) Zikir.upsertGroup(g.copy(name = name.trim(), items = items.toList())); onDone() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

// ───────────────────────── History ─────────────────────────

@Composable
private fun HistoryTab(d: ZikirData) {
    var byGroup by rememberSaveable { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
    val list = d.sessions.filter { if (byGroup) it.groupId != null else true }.sortedByDescending { it.startedAt }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(false to "Phrases", true to "Groups").forEach { (g, l) ->
                Text(l, Modifier.clip(RoundedCornerShape(50)).background(if (byGroup == g) ZGreen else Pal.c.muted).clickable { byGroup = g }.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = if (byGroup == g) Color.White else Pal.c.foreground, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) Text("No sittings yet — each sitting is saved when you switch zikir, reset, or leave the counter.", color = Pal.c.mutedForeground, fontSize = 13.sp)
        androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list.size) { i ->
                val s = list[i]
                val name = d.zikrs.firstOrNull { it.id == s.zikrId }?.name ?: "(deleted zikir)"
                val gname = s.groupId?.let { id -> d.groups.firstOrNull { it.id == id }?.name }
                val start = java.time.Instant.ofEpochMilli(s.startedAt).atZone(java.time.ZoneId.systemDefault())
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold, color = Pal.c.foreground, fontSize = 14.sp)
                        Text(start.format(fmt) + (gname?.let { " · $it" } ?: ""), fontSize = 11.sp, color = Pal.c.mutedForeground)
                        val secs = s.activeMs / 1000
                        Text("⏱ %d:%02d".format(secs / 60, secs % 60) + if (s.count > 0 && secs > 0) " · %.1f s/bead".format(secs.toFloat() / s.count) else "", fontSize = 11.sp, color = Pal.c.mutedForeground)
                    }
                    Text("%,d".format(s.count), fontWeight = FontWeight.Black, fontSize = 20.sp, color = ZGreen)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ZikrDialog(z: Zikr?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(z?.name ?: "") }
    var arabic by remember { mutableStateOf(z?.arabic ?: "") }
    var target by remember { mutableStateOf((z?.target ?: 33).toString()) }
    var goal by remember { mutableStateOf((z?.dailyGoal ?: 100).toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone, containerColor = Pal.c.card,
        title = { Text(if (z == null) "Add Zikir" else "Edit Zikir", color = Pal.c.foreground) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(arabic, { arabic = it }, label = { Text("Arabic (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Count per round (e.g. 33, 100)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(goal, { goal = it.filter(Char::isDigit) }, label = { Text("Daily goal (0 = none)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (z != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete this zikir", color = Pal.c.destructive) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val t = target.toIntOrNull()?.coerceAtLeast(1) ?: 33
                    val g = goal.toIntOrNull() ?: 0
                    Zikir.upsert((z ?: Zikr(name = name.trim())).copy(name = name.trim(), arabic = arabic.trim(), target = t, dailyGoal = g))
                }
                onDone()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
    if (confirmDelete && z != null) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = Pal.c.card,
        title = { Text("Delete ${z.name}?", color = Pal.c.foreground) },
        text = { Text("It is removed from the list; counts already made stay in your totals.", color = Pal.c.foreground) },
        confirmButton = { TextButton(onClick = { Zikir.delete(z.id); confirmDelete = false; onDone() }) { Text("Delete", color = Pal.c.destructive) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

// ───────────────────────── Progress ─────────────────────────

private val PERIODS = listOf("Hourly", "Daily", "Weekly", "Monthly", "Quarterly", "Yearly")

@Composable
private fun ZikirProgress(d: ZikirData) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableIntStateOf(1) }
    val perHour = remember(d, filter) { Zikir.perHour(d, filter) }
    val byDay = remember(perHour) { perHour.entries.groupBy({ it.key.toLocalDate() }, { it.value }).mapValues { it.value.sum() } }
    val now = LocalDateTime.now()
    val today = now.toLocalDate()

    val bars: List<Pair<String, Int>> = remember(perHour, period) {
        when (period) {
            0 -> (0..23).map { h -> "%02d".format(h) to (perHour[today.atTime(h, 0)] ?: 0) }
            1 -> (29 downTo 0).map { today.minusDays(it.toLong()) }.map { it.format(DateTimeFormatter.ofPattern("d MMM")) to (byDay[it] ?: 0) }
            2 -> (11 downTo 0).map { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).minusWeeks(it.toLong()) }.map { start ->
                start.format(DateTimeFormatter.ofPattern("d MMM")) to (0..6).sumOf { byDay[start.plusDays(it.toLong())] ?: 0 }
            }
            3 -> (11 downTo 0).map { today.withDayOfMonth(1).minusMonths(it.toLong()) }.map { m ->
                m.format(DateTimeFormatter.ofPattern("MMM yy")) to byDay.filterKeys { it.year == m.year && it.month == m.month }.values.sum()
            }
            4 -> (7 downTo 0).map { q ->
                val start = today.withDayOfMonth(1).withMonth(((today.monthValue - 1) / 3) * 3 + 1).minusMonths(3L * q)
                val label = "Q${start.get(IsoFields.QUARTER_OF_YEAR)} ${start.year % 100}"
                label to byDay.filterKeys { !it.isBefore(start) && it.isBefore(start.plusMonths(3)) }.values.sum()
            }
            else -> {
                val first = byDay.keys.minOfOrNull { it.year } ?: today.year
                (minOf(first, today.year - 4)..today.year).map { y -> "$y" to byDay.filterKeys { it.year == y }.values.sum() }
            }
        }
    }

    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
    fun sumFrom(from: LocalDate) = byDay.filterKeys { !it.isBefore(from) }.values.sum()
    val streak = run { var n = 0; var x = if ((byDay[today] ?: 0) > 0) today else today.minusDays(1); while ((byDay[x] ?: 0) > 0) { n++; x = x.minusDays(1) }; n }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf<Zikr?>(null) + d.zikrs.sortedBy { it.order }).forEach { x ->
                val sel = filter == x?.id
                Text(
                    x?.name ?: "All Zikir", Modifier.clip(RoundedCornerShape(50)).background(if (sel) ZGreen else Pal.c.muted)
                        .clickable { filter = x?.id }.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = if (sel) Color.White else Pal.c.foreground, fontSize = 13.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZStat("Today", byDay[today] ?: 0, Modifier.weight(1f))
            ZStat("This week", sumFrom(weekStart), Modifier.weight(1f))
            ZStat("This month", sumFrom(today.withDayOfMonth(1)), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZStat("This year", sumFrom(today.withDayOfYear(1)), Modifier.weight(1f))
            ZStat("Lifetime", byDay.values.sum(), Modifier.weight(1f))
            ZStat("Day streak", streak, Modifier.weight(1f))
        }

        ZCard {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PERIODS.forEachIndexed { i, l ->
                    Text(l, Modifier.clip(RoundedCornerShape(8.dp)).background(if (period == i) ZGreen else Pal.c.muted).clickable { period = i }.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (period == i) Color.White else Pal.c.foreground, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            val total = bars.sumOf { it.second }
            val active = bars.count { it.second > 0 }
            val best = bars.maxByOrNull { it.second }
            Text(
                when (period) { 0 -> "Today by hour"; 1 -> "Last 30 days"; 2 -> "Last 12 weeks"; 3 -> "Last 12 months"; 4 -> "Last 8 quarters"; else -> "By year" } +
                    " · total ${"%,d".format(total)}" + (if (active > 0) " · avg ${"%,d".format(total / active)}" else "") +
                    (if (best != null && best.second > 0) " · best ${best.first} (${"%,d".format(best.second)})" else ""),
                fontSize = 12.sp, color = Pal.c.mutedForeground,
            )
            Spacer(Modifier.height(8.dp))
            ZBars(bars, highlightLast = period != 0, highlightLabel = if (period == 0) "%02d".format(now.hour) else null)
        }

        if (filter == null) ZCard {
            Text("By zikir · lifetime", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
            val totals = d.zikrs.map { it to Zikir.perHour(d, it.id).values.sum() }.sortedByDescending { it.second }
            val max = totals.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            totals.forEach { (x, t) ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(x.name, Modifier.weight(1f), fontSize = 13.sp, color = Pal.c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("%,d".format(t), fontSize = 12.sp, color = Pal.c.mutedForeground)
                }
                BrightBar(t / max.toFloat(), listOf(ZGreen, ZGold), 6)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ZStat(label: String, v: Int, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(ZGreen.copy(alpha = .16f), Pal.c.card)))
            .border(1.dp, ZGreen.copy(alpha = .3f), RoundedCornerShape(12.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("%,d".format(v), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Pal.c.foreground, maxLines = 1)
        Text(label, fontSize = 10.sp, color = Pal.c.mutedForeground)
    }
}

@Composable
private fun ZCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(14.dp)).padding(14.dp)) { content() }
}

/** Vertical bar chart with value labels; scrolls sideways when long. */
@Composable
private fun ZBars(bars: List<Pair<String, Int>>, highlightLast: Boolean, highlightLabel: String?) {
    if (bars.all { it.second == 0 }) {
        Text("No zikir counted in this period yet.", Modifier.fillMaxWidth().padding(vertical = 30.dp), color = Pal.c.mutedForeground, textAlign = TextAlign.Center)
        return
    }
    val max = bars.maxOf { it.second }.coerceAtLeast(1).toFloat()
    val track = Pal.c.muted
    val scroll = rememberScrollState(Int.MAX_VALUE)
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEachIndexed { i, (label, v) ->
            val hot = (highlightLast && i == bars.lastIndex) || label == highlightLabel
            Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v == 0) "" else if (v >= 10_000) "${v / 1000}k" else "$v", fontSize = 9.sp, color = Pal.c.foreground, maxLines = 1)
                Canvas(Modifier.width(20.dp).height(150.dp)) {
                    drawRoundRect(track, size = size, cornerRadius = CornerRadius(6f))
                    val h = size.height * (v / max)
                    drawRoundRect(if (hot) ZGold else ZGreen, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = CornerRadius(6f))
                }
                Text(label, fontSize = 9.sp, color = if (hot) ZGold else Pal.c.mutedForeground, maxLines = 1)
            }
        }
    }
}
