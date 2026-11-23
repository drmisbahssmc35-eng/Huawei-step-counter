package com.progresspal.app.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progresspal.app.data.MILESTONES
import com.progresspal.app.data.WVideo
import com.progresspal.app.data.WatchTracker
import com.progresspal.app.data.currentMilestone
import com.progresspal.app.data.fmtWatch
import com.progresspal.app.data.nextMilestone
import com.progresspal.app.ui.components.toast
import com.progresspal.app.ui.theme.Pal
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Primary = Color(0xFF7C4DFF)
private val Accent = Color(0xFF00E5FF)
private val Done = Color(0xFF00E676)

/** Vinay Watch Tracker: watch Dr. Vinay Kumar Singh's videos, track time, streaks and career ranks. */
@Composable
fun WatchTrackerScreen(onBack: () -> Unit) {
    val d by WatchTracker.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var playing by remember { mutableStateOf<WVideo?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf<WVideo?>(null) }
    var confirmDelete by remember { mutableStateOf<WVideo?>(null) }
    var editTarget by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    val total = d.sessions.sumOf { it.seconds }
    val hours = total / 3600.0
    val rank = currentMilestone(hours)
    val today = WatchTracker.todayS(d)
    val todayPct = (today * 100 / d.dailyTargetS.coerceAtLeast(1)).coerceAtMost(100)

    Column(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.c.foreground) }
            Column(Modifier.weight(1f)) {
                Text("Vinay Watch Tracker", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Pal.c.foreground)
                Text("${rank.icon} ${rank.title} · ${"%.1f".format(hours)} h watched", fontSize = 12.sp, color = Pal.c.mutedForeground)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Library", "Analytics").forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (tab == i) Primary else Pal.c.muted)
                        .clickable { tab = i }.padding(vertical = 8.dp),
                    color = if (tab == i) Color.White else Pal.c.foreground, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (tab == 1) { WatchAnalytics(); return@Column }

        val sorted = when (d.sortBy) {
            "duration-asc" -> d.videos.sortedBy { it.durationS }
            "duration-desc" -> d.videos.sortedByDescending { it.durationS }
            "progress-asc" -> d.videos.sortedBy { it.watchedS.toFloat() / it.durationS.coerceAtLeast(1) }
            "progress-desc" -> d.videos.sortedByDescending { it.watchedS.toFloat() / it.durationS.coerceAtLeast(1) }
            else -> d.videos.sortedBy { it.order }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                // Daily target
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.c.card).border(1.dp, Pal.c.border, RoundedCornerShape(14.dp)).clickable { editTarget = true }.padding(14.dp)) {
                    Row {
                        Text("🎯 Daily Target", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Pal.c.foreground)
                        Text("${fmtWatch(today)} / ${fmtWatch(d.dailyTargetS)} · $todayPct%", fontWeight = FontWeight.Bold, color = Pal.c.foreground, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    BrightBar(todayPct / 100f, listOf(Primary, Accent), 16)
                }
                Spacer(Modifier.height(8.dp))
                // Mini stats
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat(rank.icon, rank.title, "Current rank", Modifier.weight(1f)) { tab = 1 }
                    MiniStat("⏱", fmtWatch(total), "Total watch time", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("🎬", fmtWatch(d.videos.sumOf { it.watchedS.coerceAtMost(it.durationS) }), "of ${fmtWatch(d.videos.sumOf { it.durationS })} total", Modifier.weight(1f))
                    MiniStat("✅", "${d.videos.count { it.durationS > 0 && it.watchedS >= it.durationS }}/${d.videos.size}", "Videos done", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                // Library header: sort + actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▶ Video Library (${d.videos.size})", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Pal.c.foreground)
                    TextButton(onClick = { val n = WatchTracker.loadAll(); toast(ctx, if (n == 0) "All videos are already in your library!" else "Loaded $n videos from Dr. Vinay Singh's channel!") }) { Text("Load All") }
                    TextButton(onClick = { adding = true }) { Text("+ Add") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("default" to "Default", "duration-asc" to "Duration ↑", "duration-desc" to "Duration ↓", "progress-asc" to "Progress ↑", "progress-desc" to "Progress ↓").forEach { (k, l) ->
                        Text(
                            l, Modifier.clip(RoundedCornerShape(50)).background(if (d.sortBy == k) Primary else Pal.c.muted)
                                .clickable { WatchTracker.setSort(k) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (d.sortBy == k) Color.White else Pal.c.foreground, fontSize = 12.sp,
                        )
                    }
                }
                if (d.videos.isEmpty()) Text("No videos yet. Tap Load All to add Dr. Vinay Singh's videos, or + Add your own.", Modifier.padding(vertical = 24.dp), color = Pal.c.mutedForeground)
            }
            itemsIndexed(sorted, key = { _, v -> v.id }) { i, v ->
                VideoRow(i + 1, v, v.id == d.lastWatchedId,
                    onPlay = { if (v.ytId != null) playing = v else toast(ctx, "No YouTube link for this video") },
                    onComplete = { confirmComplete = v }, onRepeat = { WatchTracker.repeat(v.id); toast(ctx, "Video reset — rewatch #${v.repeat + 1} ready!") },
                    onDelete = { confirmDelete = v })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    playing?.let { v -> PlayerDialog(WatchTracker.data.value.videos.firstOrNull { it.id == v.id } ?: v) { playing = null } }
    if (adding) AddVideoDialog { adding = false }
    confirmComplete?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmComplete = null }, containerColor = Pal.c.card,
            title = { Text("Mark as complete?", color = Pal.c.foreground) },
            text = { Text("This logs the remaining watch time for \"${v.title}\" and counts it towards your daily target and stats.", color = Pal.c.foreground) },
            confirmButton = { TextButton(onClick = { WatchTracker.complete(v.id); com.progresspal.app.ui.components.Sounds.done(); confirmComplete = null }) { Text("Complete") } },
            dismissButton = { TextButton(onClick = { confirmComplete = null }) { Text("Cancel") } },
        )
    }
    confirmDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null }, containerColor = Pal.c.card,
            title = { Text("Delete this video?", color = Pal.c.foreground) },
            text = { Text(v.title, color = Pal.c.foreground) },
            confirmButton = { TextButton(onClick = { WatchTracker.delete(v.id); confirmDelete = null }) { Text("Delete", color = Pal.c.destructive) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    if (editTarget) NameDialog("Daily target (minutes)", (d.dailyTargetS / 60).toString()) { s ->
        s?.trim()?.toIntOrNull()?.let { WatchTracker.setTarget(it * 60) }
        editTarget = false
    }
}

@Composable
private fun MiniStat(icon: String, value: String, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Primary.copy(alpha = .16f), Pal.c.card)))
            .border(1.dp, Primary.copy(alpha = .3f), RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 20.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Pal.c.foreground, textAlign = TextAlign.Center, maxLines = 2)
        Text(label, fontSize = 10.sp, color = Pal.c.mutedForeground, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VideoRow(n: Int, v: WVideo, last: Boolean, onPlay: () -> Unit, onComplete: () -> Unit, onRepeat: () -> Unit, onDelete: () -> Unit) {
    val pct = if (v.durationS > 0) (v.watchedS * 100 / v.durationS).coerceAtMost(100) else 0
    val complete = pct >= 100
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (last) Primary.copy(alpha = .10f) else Pal.c.card)
            .border(1.dp, if (last) Primary.copy(alpha = .5f) else Pal.c.border, RoundedCornerShape(12.dp)).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (v.ytId != null) Primary else Pal.c.muted).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("$n. ${v.title}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pal.c.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                    if (last) Badge("Last watched", Primary)
                    if (complete) Badge("✓ Complete", Done)
                    if (v.repeat > 0) Badge("🔁 ${v.repeat}${ordinal(v.repeat)} time", Accent)
                }
                Row(Modifier.padding(top = 2.dp)) {
                    Text("⏱ ${fmtWatch(v.watchedS)} / ${fmtWatch(v.durationS)}", Modifier.weight(1f), fontSize = 11.sp, color = Pal.c.mutedForeground)
                    Text("$pct%", fontSize = 11.sp, color = Pal.c.mutedForeground, fontWeight = FontWeight.Bold)
                }
            }
            if (!complete) TextButton(onClick = onComplete) { Text("✓", fontWeight = FontWeight.Black, color = Done) }
            else IconButton(onClick = onRepeat) { Icon(Icons.Filled.Replay, "Rewatch", tint = Pal.c.mutedForeground) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = Pal.c.mutedForeground) }
        }
        Spacer(Modifier.height(6.dp))
        BrightBar(pct / 100f, if (complete) listOf(Done, Color(0xFF00BFA5)) else listOf(Primary, Accent), 6)
    }
}

private fun ordinal(n: Int) = when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

@Composable
private fun Badge(t: String, c: Color) {
    Text(t, Modifier.clip(RoundedCornerShape(4.dp)).background(c.copy(alpha = .18f)).padding(horizontal = 5.dp, vertical = 1.dp), color = c, fontSize = 9.sp, fontWeight = FontWeight.Bold)
}

/** Bridge the embedded YouTube player calls with its position (every 5 s), end and errors. */
private class PlayerBridge(val onProgress: (Int) -> Unit, val onEnded: () -> Unit, val onError: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun progress(t: Int) { main.post { onProgress(t) } }
    @JavascriptInterface fun ended() { main.post { onEnded() } }
    @JavascriptInterface fun error(@Suppress("UNUSED_PARAMETER") code: Int) { main.post { onError() } }
}

/**
 * Plays a video inside the app and saves the watch position as it goes. If the video cannot be
 * embedded (e.g. age-restricted), it opens in YouTube with a running timer to log the time.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PlayerDialog(v: WVideo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var fallback by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var minText by remember { mutableStateOf("") }
    var secText by remember { mutableStateOf("") }

    // Record the watching as the "Dr. Vinay Sing Video" activity in Simple Time Tracker:
    // started when the player opens, stopped when it closes (or the video ends).
    val act = remember {
        val acts = com.progresspal.app.data.SimpleTimeTracker.cache.value.activities
        acts.firstOrNull { it.name.contains("vinay", ignoreCase = true) }
            ?: acts.firstOrNull { it.name.contains("video", ignoreCase = true) }
    }
    var startedByUs by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (act == null) toast(ctx, "Tip: add a \"Dr. Vinay Sing Video\" activity in Simple Time Tracker to record watching on the timeline")
        else if (!com.progresspal.app.data.SimpleTimeTracker.isRunning(act.id)) {
            com.progresspal.app.data.SimpleTimeTracker.start(act, v.title)
            startedByUs = true
        }
    }
    val stopActivity: () -> Unit = {
        if (startedByUs && act != null && com.progresspal.app.data.SimpleTimeTracker.isRunning(act.id)) com.progresspal.app.data.SimpleTimeTracker.toggle(act)
        startedByUs = false
    }
    DisposableEffect(Unit) { onDispose { stopActivity() } }
    LaunchedEffect(startedAt) {
        while (startedAt > 0) {
            now = System.currentTimeMillis()
            val total = v.watchedS + ((now - startedAt) / 1000).toInt()
            minText = (total / 60).toString(); secText = (total % 60).toString()
            delay(1000)
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(16.dp)).background(Pal.c.card).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Pal.c.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onClose) { Icon(Icons.Filled.Stop, "Stop", tint = Pal.c.destructive) }
            }
            if (!fallback) {
                val html = """
                    <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                    <body style="margin:0;background:#000"><div id="p"></div>
                    <script src="https://www.youtube.com/iframe_api"></script>
                    <script>
                    var player;
                    function onYouTubeIframeAPIReady(){
                      player=new YT.Player('p',{width:'100%',height:'100%',videoId:'${v.ytId}',
                        playerVars:{start:${v.watchedS.coerceAtMost((v.durationS - 3).coerceAtLeast(0))},autoplay:1,playsinline:1,rel:0,modestbranding:1},
                        events:{onStateChange:function(e){
                            if(e.data==0){Android.ended();}
                            if(e.data==2&&player.getCurrentTime){Android.progress(Math.floor(player.getCurrentTime()));}
                          },
                          onError:function(e){Android.error(e.data);}}});
                    }
                    setInterval(function(){ if(player&&player.getPlayerState&&player.getPlayerState()==1){Android.progress(Math.floor(player.getCurrentTime()));} },5000);
                    </script></body></html>
                """.trimIndent()
                var web by remember { mutableStateOf<WebView?>(null) }
                DisposableEffect(Unit) { onDispose { web?.evaluateJavascript("if(player&&player.getCurrentTime){Android.progress(Math.floor(player.getCurrentTime()));}", null); web?.destroy() } }
                AndroidView(
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webChromeClient = WebChromeClient()
                            addJavascriptInterface(PlayerBridge(
                                onProgress = { t -> WatchTracker.progress(v.id, t) },
                                onEnded = { WatchTracker.complete(v.id); stopActivity(); com.progresspal.app.ui.components.Sounds.done(c); toast(c, "Video completed! 🎉") },
                                onError = { fallback = true },
                            ), "Android")
                            loadDataWithBaseURL("https://progresspal.app/", html, "text/html", "utf-8", null)
                            web = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
                )
                TextButton(onClick = { fallback = true }) { Text("Video won't play here? Watch on YouTube with a timer", fontSize = 12.sp) }
            } else {
                // Age-restricted or blocked embeds: open YouTube, time it, log it.
                Column(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.Black).padding(12.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (startedAt > 0) "Timer running — come back here when done watching:" else "Watch on YouTube — the timer starts automatically:",
                        color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                    if (startedAt > 0) {
                        val t = v.watchedS + ((now - startedAt) / 1000).toInt()
                        Text("%02d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60), color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(minText, { minText = it.filter(Char::isDigit) }, Modifier.width(80.dp), label = { Text("min") }, singleLine = true)
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(secText, { secText = it.filter(Char::isDigit) }, Modifier.width(80.dp), label = { Text("sec") }, singleLine = true)
                        TextButton(onClick = {
                            val tot = (minText.toIntOrNull() ?: 0) * 60 + (secText.toIntOrNull() ?: 0)
                            if (tot > v.watchedS) { WatchTracker.setWatched(v.id, tot); toast(ctx, "Logged ${tot / 60}m ${tot % 60}s total watch time") }
                            else toast(ctx, "Entered time is less than already watched — no update.")
                            startedAt = 0; onClose()
                        }) { Text("Log") }
                    }
                    TextButton(onClick = {
                        startedAt = System.currentTimeMillis()
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${v.ytId}" + if (v.watchedS > 0) "&t=${v.watchedS}" else "")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text(if (startedAt > 0) "Reopen on YouTube" else "Watch on YouTube (starts timer)", color = Accent) }
                }
            }
            Text(
                "⏱ ${fmtWatch(v.watchedS)} / ${fmtWatch(v.durationS)} watched" + (act?.let { "  ·  ▶ recording as ${it.name}" } ?: ""),
                Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Pal.c.mutedForeground,
            )
        }
    }
}

@Composable
private fun AddVideoDialog(onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var mins by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone, containerColor = Pal.c.card,
        title = { Text("Add Video", color = Pal.c.foreground) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Video title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(mins, { mins = it }, label = { Text("Duration (minutes, or mm:ss)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("YouTube URL (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dur = if (':' in mins) WatchTracker.parseClock(mins) else ((mins.toDoubleOrNull() ?: 0.0) * 60).toInt()
                if (title.isNotBlank() && dur > 0) WatchTracker.add(title.trim(), dur, url.ifBlank { null })
                onDone()
            }) { Text("Add Video") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** Rank hero, stats, 14-day chart and career milestones. */
@Composable
private fun WatchAnalytics() {
    val d by WatchTracker.data.collectAsStateWithLifecycle()
    val total = d.sessions.sumOf { it.seconds }
    val hours = total / 3600.0
    val rank = currentMilestone(hours)
    val next = nextMilestone(hours)
    val today = WatchTracker.todayS(d)
    val (cur, best) = WatchTracker.streaks(d)
    val last30 = d.sessions.filter { it.date >= LocalDate.now().minusDays(30).toString() }
    val days30 = last30.map { it.date }.toSet().size
    val avg = if (days30 == 0) 0 else last30.sumOf { it.seconds } / days30

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Primary.copy(alpha = .25f), Accent.copy(alpha = .15f)))).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("CURRENT RANK", fontSize = 11.sp, color = Pal.c.mutedForeground, fontWeight = FontWeight.Bold)
            Text(rank.icon, fontSize = 52.sp)
            Text(rank.title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Accent, textAlign = TextAlign.Center)
            Text("${"%.1f".format(hours)} hours watched", fontSize = 13.sp, color = Pal.c.mutedForeground)
            if (next != null) {
                val frac = ((hours - rank.hours) / (next.hours - rank.hours)).toFloat().coerceIn(0f, 1f)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(rank.title, Modifier.weight(1f), fontSize = 10.sp, color = Pal.c.mutedForeground)
                    Text(next.title, fontSize = 10.sp, color = Pal.c.mutedForeground)
                }
                BrightBar(frac, listOf(Primary, Accent), 14)
                Text("${(frac * 100).toInt()}% · ${fmtWatch(((next.hours - hours) * 3600).toInt())} to ${next.icon} ${next.title}", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Pal.c.foreground)
            } else Text("🏆 Maximum rank achieved!", Modifier.padding(top = 8.dp), color = Accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("🎯", "${fmtWatch(today)} / ${fmtWatch(d.dailyTargetS)}", "Today", Modifier.weight(1f))
            MiniStat("⏱", fmtWatch(total), "Total", Modifier.weight(1f))
            MiniStat("📊", fmtWatch(avg), "Avg / day (30d)", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("🔥", "$cur days", "Current streak", Modifier.weight(1f))
            MiniStat("🏅", "$best days", "Highest streak", Modifier.weight(1f))
        }

        // Daily watch time, last 14 days
        Spacer(Modifier.height(14.dp))
        Text("Daily watch time · last 14 days", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
        val days = (13 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
        val perDay = days.map { day -> d.sessions.filter { it.date == day.toString() }.sumOf { it.seconds } }
        val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(d.dailyTargetS)
        Row(Modifier.fillMaxWidth().height(170.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, day ->
                val s = perDay[i]
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (s > 0) Text("${s / 60}", fontSize = 9.sp, color = Pal.c.foreground, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier.fillMaxWidth().height((120f * s / max).coerceAtLeast(2f).dp)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(Brush.verticalGradient(if (s >= d.dailyTargetS) listOf(Done, Color(0xFF00BFA5)) else listOf(Accent, Primary))),
                    )
                    Text(day.format(DateTimeFormatter.ofPattern("EEE")).take(2), fontSize = 9.sp, color = if (day == LocalDate.now()) Accent else Pal.c.mutedForeground)
                }
            }
        }
        Text("minutes per day · green = target reached", fontSize = 10.sp, color = Pal.c.mutedForeground)

        // Milestones
        Spacer(Modifier.height(14.dp))
        Text("🏆 Career Milestones", fontWeight = FontWeight.Bold, color = Pal.c.foreground)
        Spacer(Modifier.height(6.dp))
        MILESTONES.forEach { m ->
            val achieved = hours >= m.hours
            val at = d.achievedAt[m.hours.toString()]
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (achieved) Primary.copy(alpha = .12f) else Pal.c.muted.copy(alpha = .5f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(m.icon, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, fontWeight = FontWeight.SemiBold, color = if (achieved) Pal.c.foreground else Pal.c.mutedForeground, fontSize = 14.sp)
                    Text("${if (m.hours % 1.0 == 0.0) m.hours.toInt() else m.hours}h required", fontSize = 11.sp, color = Pal.c.mutedForeground)
                    if (achieved && at != null) Text(
                        "📅 " + DateTimeFormatter.ofPattern("d MMM yyyy 'at' h:mm a").format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())),
                        fontSize = 11.sp, color = Primary,
                    )
                }
                Text(if (achieved) "✓" else "🔒", color = if (achieved) Done else Pal.c.mutedForeground, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
