package com.progresspal.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.UUID

/** A video in the watch library. */
@Serializable
data class WVideo(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val durationS: Int,
    val watchedS: Int = 0,
    /** YouTube video id, if any. */
    val ytId: String? = null,
    val repeat: Int = 0,
    val order: Int = 0,
)

/** Watch time logged on a day for a video. */
@Serializable
data class WSession(val date: String, val videoId: String, val seconds: Int, val at: Long = System.currentTimeMillis())

@Serializable
data class WatchData(
    val videos: List<WVideo> = emptyList(),
    val sessions: List<WSession> = emptyList(),
    val dailyTargetS: Int = 1800,
    val lastWatchedId: String? = null,
    /** Milestone hours → when it was reached (epoch ms). */
    val achievedAt: Map<String, Long> = emptyMap(),
    val sortBy: String = "default",
)

data class Milestone(val hours: Double, val title: String, val icon: String)

/** Career ranks unlocked by total watch hours. */
val MILESTONES = listOf(
    Milestone(0.5, "Beginner", "🌱"), Milestone(1.0, "Medical Admission Candidate", "📋"),
    Milestone(2.0, "1st Year Medical Student", "📚"), Milestone(5.0, "2nd Year Medical Student", "🔬"),
    Milestone(10.0, "3rd Year Medical Student", "🩺"), Milestone(15.0, "4th Year Medical Student", "💉"),
    Milestone(20.0, "5th Year Medical Student", "🏥"), Milestone(25.0, "MBBS Completed", "🎓"),
    Milestone(30.0, "Surgery Intern", "🔪"), Milestone(35.0, "MS Admission Test Candidate", "📝"),
    Milestone(40.0, "MS Phase A", "🦴"), Milestone(45.0, "MS Phase B", "🦿"),
    Milestone(50.0, "MS (Orthopaedics)", "🏆"), Milestone(55.0, "FCPS Part 2", "📖"),
    Milestone(60.0, "FCPS (Thesis)", "📄"), Milestone(65.0, "FCPS (Orthopaedics)", "🥇"),
    Milestone(70.0, "Assistant Registrar", "🏛️"), Milestone(75.0, "Registrar", "📌"),
    Milestone(80.0, "Jr Consultant", "👨‍⚕️"), Milestone(85.0, "Senior Consultant", "👩‍⚕️"),
    Milestone(90.0, "Assistant Professor", "🎤"), Milestone(95.0, "Associate Professor", "📢"),
    Milestone(100.0, "Professor", "👑"),
)

fun currentMilestone(hours: Double): Milestone = MILESTONES.lastOrNull { hours >= it.hours } ?: MILESTONES.first()
fun nextMilestone(hours: Double): Milestone? = MILESTONES.firstOrNull { hours < it.hours }

/** "1h 5m", "12m 30s", "40s". */
fun fmtWatch(seconds: Int): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return when { h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m ${s}s"; else -> "${s}s" }
}

/** Vinay Watch Tracker data (videos, watch sessions, target), kept on the phone. */
object WatchTracker {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: File
    private lateinit var appContext: Context
    private val _data = MutableStateFlow(WatchData())
    val data: StateFlow<WatchData> = _data

    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = File(appContext.filesDir, "watch_tracker.json")
        if (file.exists()) runCatching { _data.value = json.decodeFromString<WatchData>(file.readText()) }
    }

    @Synchronized
    private fun update(f: (WatchData) -> WatchData) {
        val next = f(_data.value)
        _data.value = next
        if (::file.isInitialized) runCatching { file.writeText(json.encodeToString(next)) }
    }

    val totalS: Int get() = _data.value.sessions.sumOf { it.seconds }
    fun todayS(d: WatchData = _data.value) = d.sessions.filter { it.date == LocalDate.now().toString() }.sumOf { it.seconds }

    /** Adds every video of Dr. Vinay Kumar Singh's channel that is not in the library yet. Returns how many. */
    fun loadAll(): Int {
        val lines = runCatching { appContext.assets.open("vinay_videos.txt").bufferedReader().readLines() }.getOrDefault(emptyList())
        var added = 0
        update { d ->
            val have = d.videos.mapNotNull { it.ytId }.toSet()
            var order = (d.videos.maxOfOrNull { it.order } ?: -1) + 1
            val fresh = lines.mapNotNull { line ->
                val p = line.split('|'); if (p.size < 3) return@mapNotNull null
                if (p[2] in have) return@mapNotNull null
                WVideo(title = p[0], durationS = parseClock(p[1]), ytId = p[2], order = order++)
            }
            added = fresh.size
            d.copy(videos = d.videos + fresh)
        }
        return added
    }

    /** True when this YouTube video is already in the library. */
    fun has(ytId: String) = _data.value.videos.any { it.ytId == ytId }

    // ───── YouTube look-up (no API key: oEmbed for the title, the watch / results pages for the rest) ─────

    data class YtResult(val id: String, val title: String, val durationS: Int, val channel: String)

    private fun httpGet(url: String): String {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            c.setRequestProperty("Cookie", "CONSENT=YES+1")
            if (c.responseCode !in 200..299) throw java.io.IOException("YouTube returned ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** Title, length (seconds) and channel of a YouTube video. */
    suspend fun lookup(ytId: String): YtResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val page = runCatching { httpGet("https://www.youtube.com/watch?v=$ytId&hl=en") }.getOrNull().orEmpty()
        val length = Regex("""\"lengthSeconds\":\"(\d+)\"""").find(page)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val o = runCatching {
            json.parseToJsonElement(httpGet("https://www.youtube.com/oembed?format=json&url=" + java.net.URLEncoder.encode("https://www.youtube.com/watch?v=$ytId", "UTF-8"))).jsonObject
        }.getOrNull()
        val title = o?.get("title")?.jsonPrimitive?.contentOrNull
            ?: Regex("""<meta name=\"title\" content=\"([^\"]*)\"""").find(page)?.groupValues?.get(1)?.let { htmlDecode(it) }
            ?: throw java.io.IOException("Video not found")
        YtResult(ytId, title, length, o?.get("author_name")?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    /** YouTube search by keywords (videos only). */
    suspend fun search(query: String): List<YtResult> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val page = httpGet("https://www.youtube.com/results?hl=en&search_query=" + java.net.URLEncoder.encode(query, "UTF-8"))
        val start = page.indexOf("ytInitialData = ").takeIf { it >= 0 }?.plus(16) ?: page.indexOf("ytInitialData\"] = ").takeIf { it >= 0 }?.plus(18)
            ?: throw java.io.IOException("Couldn't read YouTube's results")
        val end = page.indexOf(";</script>", start)
        val root = json.parseToJsonElement(page.substring(start, end))
        val out = mutableListOf<YtResult>()
        fun text(e: kotlinx.serialization.json.JsonElement?): String {
            val o = e as? kotlinx.serialization.json.JsonObject ?: return ""
            o["simpleText"]?.jsonPrimitive?.contentOrNull?.let { return it }
            return (o["runs"] as? kotlinx.serialization.json.JsonArray)?.joinToString("") { (it as? kotlinx.serialization.json.JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty() }.orEmpty()
        }
        fun walk(e: kotlinx.serialization.json.JsonElement) {
            when (e) {
                is kotlinx.serialization.json.JsonObject -> {
                    val v = e["videoRenderer"] as? kotlinx.serialization.json.JsonObject
                    if (v != null) {
                        val id = v["videoId"]?.jsonPrimitive?.contentOrNull
                        if (id != null && out.none { it.id == id }) out += YtResult(id, text(v["title"]), parseClock(text(v["lengthText"])), text(v["ownerText"]))
                    } else e.values.forEach { walk(it) }
                }
                is kotlinx.serialization.json.JsonArray -> e.forEach { walk(it) }
                else -> {}
            }
        }
        walk(root)
        out
    }

    private fun htmlDecode(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    fun add(title: String, durationS: Int, url: String?) = update { d ->
        d.copy(videos = d.videos + WVideo(title = title, durationS = durationS, ytId = url?.let { youTubeId(it) }, order = (d.videos.maxOfOrNull { it.order } ?: -1) + 1))
    }

    fun delete(id: String) = update { d -> d.copy(videos = d.videos.filterNot { it.id == id }) }

    /** Rewatch: progress back to 0, count one more viewing. */
    fun repeat(id: String) = update { d -> d.copy(videos = d.videos.map { if (it.id == id) it.copy(watchedS = 0, repeat = it.repeat + 1) else it }) }

    fun setTarget(seconds: Int) = update { it.copy(dailyTargetS = seconds.coerceAtLeast(60)) }
    fun setSort(s: String) = update { it.copy(sortBy = s) }

    /** Player position moved to [positionS]: log the newly watched part. */
    fun progress(id: String, positionS: Int) {
        val v = _data.value.videos.firstOrNull { it.id == id } ?: return
        val target = positionS.coerceAtMost(if (v.durationS > 0) v.durationS else positionS)
        if (target > v.watchedS) log(id, target - v.watchedS)
    }

    /** Marks a video fully watched, logging the remaining time. */
    fun complete(id: String) {
        val v = _data.value.videos.firstOrNull { it.id == id } ?: return
        if (v.durationS > v.watchedS) log(id, v.durationS - v.watchedS)
    }

    /** Sets the total watched of a video (manual entry); only moves forward. */
    fun setWatched(id: String, totalS: Int) {
        val v = _data.value.videos.firstOrNull { it.id == id } ?: return
        val capped = if (v.durationS > 0) totalS.coerceAtMost(v.durationS) else totalS
        if (capped > v.watchedS) log(id, capped - v.watchedS)
    }

    private fun log(id: String, seconds: Int) {
        if (seconds <= 0) return
        update { d ->
            val before = d.sessions.sumOf { it.seconds } / 3600.0
            val sessions = d.sessions + WSession(LocalDate.now().toString(), id, seconds)
            val after = sessions.sumOf { it.seconds } / 3600.0
            val newly = MILESTONES.filter { it.hours in before..after && it.hours > before }.associate { it.hours.toString() to System.currentTimeMillis() }
            d.copy(
                sessions = sessions,
                videos = d.videos.map { if (it.id == id) it.copy(watchedS = it.watchedS + seconds) else it },
                lastWatchedId = id,
                achievedAt = newly + d.achievedAt,
            )
        }
    }

    /** Days in a row with watching, ending today (or yesterday), and the longest such run. */
    fun streaks(d: WatchData = _data.value): Pair<Int, Int> {
        val days = d.sessions.map { it.date }.toSet().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        var best = 0; var run = 0; var prev: LocalDate? = null
        days.forEach { day -> run = if (prev != null && prev!!.plusDays(1) == day) run + 1 else 1; best = maxOf(best, run); prev = day }
        val today = LocalDate.now()
        var cur = 0
        var dd = if (today.toString() in d.sessions.map { it.date }) today else today.minusDays(1)
        val set = days.toSet()
        while (dd in set) { cur++; dd = dd.minusDays(1) }
        return cur to best
    }

    fun parseClock(t: String): Int {
        val p = t.split(':').mapNotNull { it.trim().toIntOrNull() }
        return when (p.size) { 3 -> p[0] * 3600 + p[1] * 60 + p[2]; 2 -> p[0] * 60 + p[1]; 1 -> p[0]; else -> 0 }
    }

    fun youTubeId(url: String): String? =
        Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?v=|embed/|v/|shorts/))([a-zA-Z0-9_-]{11})""").find(url)?.groupValues?.get(1)
            ?: url.trim().takeIf { Regex("^[a-zA-Z0-9_-]{11}$").matches(it) }
}
