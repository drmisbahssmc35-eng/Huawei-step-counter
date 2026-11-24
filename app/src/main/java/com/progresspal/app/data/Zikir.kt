package com.progresspal.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
data class Zikr(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val arabic: String = "",
    /** Count for one round of the tasbih (e.g. 33 or 100). */
    val target: Int = 33,
    /** Daily goal for this zikir; 0 = none. */
    val dailyGoal: Int = 0,
    val order: Int = 0,
)

/** A sequence of zikir to recite one after another (e.g. after salat: 33 / 33 / 34). */
@Serializable
data class ZGroupItem(val zikrId: String, val count: Int)

@Serializable
data class ZGroup(val id: String = UUID.randomUUID().toString(), val name: String, val items: List<ZGroupItem> = emptyList())

/** One sitting of tasbih: what was recited, how many, and for how long. */
@Serializable
data class ZSession(
    val zikrId: String,
    val groupId: String? = null,
    val count: Int,
    val startedAt: Long,
    val endedAt: Long,
    val activeMs: Long,
)

@Serializable
data class ZSettings(
    val step: Int = 1,
    /** Autoplay interval between beads. */
    val autoMs: Long = 1000,
    val sound: Boolean = false,
    val beads: Boolean = true,
    val fontScale: Float = 1f,
    val theme: Int = 0,
    val tools: Boolean = true,
    /** Average time between beads measured with "Record speed". */
    val avgMs: Long = 0,
)

@Serializable
data class ZikirData(
    val zikrs: List<Zikr> = emptyList(),
    /** "zikrId|yyyy-MM-ddTHH" → count tapped in that hour. */
    val hourly: Map<String, Int> = emptyMap(),
    /** Count of the current round per zikir (resets after each round / manually). */
    val round: Map<String, Int> = emptyMap(),
    /** Completed rounds per zikir. */
    val rounds: Map<String, Int> = emptyMap(),
    val selectedId: String? = null,
    val vibrate: Boolean = true,
    val seeded: Boolean = false,
    val groups: List<ZGroup> = emptyList(),
    val sessions: List<ZSession> = emptyList(),
    val settings: ZSettings = ZSettings(),
    /** Group being recited, its current item and the count within that item. */
    val activeGroupId: String? = null,
    val groupPos: Int = 0,
    val groupCount: Int = 0,
    val groupsSeeded: Boolean = false,
)

private val DEFAULT_ZIKRS = listOf(
    Zikr(name = "SubhanAllah", arabic = "سُبْحَانَ ٱللَّٰهِ", target = 33, dailyGoal = 100),
    Zikr(name = "Alhamdulillah", arabic = "ٱلْحَمْدُ لِلَّٰهِ", target = 33, dailyGoal = 100),
    Zikr(name = "Allahu Akbar", arabic = "ٱللَّٰهُ أَكْبَرُ", target = 34, dailyGoal = 100),
    Zikr(name = "La ilaha illallah", arabic = "لَا إِلَٰهَ إِلَّا ٱللَّٰهُ", target = 100, dailyGoal = 100),
    Zikr(name = "Astaghfirullah", arabic = "أَسْتَغْفِرُ ٱللَّٰهَ", target = 100, dailyGoal = 100),
    Zikr(name = "Durood (Salawat)", arabic = "ٱللَّٰهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ", target = 100, dailyGoal = 100),
    Zikr(name = "SubhanAllahi wa bihamdihi", arabic = "سُبْحَانَ ٱللَّٰهِ وَبِحَمْدِهِ", target = 100, dailyGoal = 100),
    Zikr(name = "La hawla wa la quwwata illa billah", arabic = "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِٱللَّٰهِ", target = 100),
).mapIndexed { i, z -> z.copy(order = i) }

/** Zikir (tasbih) counter: counts per zikir, kept per hour on the phone. */
object Zikir {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: File
    private val _data = MutableStateFlow(ZikirData())
    val data: StateFlow<ZikirData> = _data
    val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, "zikir.json")
        if (file.exists()) runCatching { _data.value = json.decodeFromString<ZikirData>(file.readText()) }
        if (!_data.value.seeded) update { it.copy(zikrs = it.zikrs + DEFAULT_ZIKRS, seeded = true, selectedId = it.selectedId ?: DEFAULT_ZIKRS.first().id) }
        if (!_data.value.groupsSeeded) update { d ->
            fun id(n: String) = d.zikrs.firstOrNull { it.name.equals(n, true) }?.id
            val after = listOfNotNull(id("SubhanAllah")?.let { ZGroupItem(it, 33) }, id("Alhamdulillah")?.let { ZGroupItem(it, 33) }, id("Allahu Akbar")?.let { ZGroupItem(it, 34) })
            val morning = listOfNotNull(id("Astaghfirullah")?.let { ZGroupItem(it, 100) }, id("SubhanAllahi wa bihamdihi")?.let { ZGroupItem(it, 100) }, id("Durood (Salawat)")?.let { ZGroupItem(it, 100) })
            d.copy(groupsSeeded = true, groups = d.groups + listOfNotNull(
                ZGroup(name = "After Salat (33·33·34)", items = after).takeIf { after.size == 3 },
                ZGroup(name = "Daily 100s", items = morning).takeIf { morning.isNotEmpty() },
            ))
        }
    }

    @Synchronized
    private fun update(f: (ZikirData) -> ZikirData) {
        val next = f(_data.value)
        _data.value = next
        if (::file.isInitialized) runCatching { file.writeText(json.encodeToString(next)) }
    }

    fun select(id: String) { if (id != sZikr) endSession(); update { it.copy(selectedId = id) } }
    fun setVibrate(on: Boolean) = update { it.copy(vibrate = on) }

    // ───── Settings, groups, sessions, backup ─────

    fun settings(f: (ZSettings) -> ZSettings) = update { it.copy(settings = f(it.settings)) }

    fun upsertGroup(g: ZGroup) = update { d ->
        if (d.groups.any { it.id == g.id }) d.copy(groups = d.groups.map { if (it.id == g.id) g else it }) else d.copy(groups = d.groups + g)
    }
    fun deleteGroup(id: String) = update { d -> d.copy(groups = d.groups.filterNot { it.id == id }, activeGroupId = d.activeGroupId.takeIf { it != id }) }

    /** Starts reciting a group from its first item. */
    fun startGroup(id: String) {
        endSession()
        update { d ->
            val g = d.groups.firstOrNull { it.id == id } ?: return@update d
            d.copy(activeGroupId = id, groupPos = 0, groupCount = 0, selectedId = g.items.firstOrNull()?.zikrId ?: d.selectedId)
        }
    }
    fun stopGroup() { endSession(); update { it.copy(activeGroupId = null, groupPos = 0, groupCount = 0) } }

    // The sitting in progress (kept in memory, saved to history when it ends).
    private var sZikr: String? = null
    private var sCount = 0
    private var sStart = 0L
    private var sActiveFrom = 0L
    private var sActiveMs = 0L
    var paused = false
        private set

    /** Active time of the current sitting (pauses excluded). */
    fun sessionMs(): Long = sActiveMs + if (sZikr != null && !paused && sActiveFrom > 0) System.currentTimeMillis() - sActiveFrom else 0
    fun sessionCount() = sCount

    fun setPaused(p: Boolean) {
        if (p == paused) return
        val now = System.currentTimeMillis()
        if (p) { if (sActiveFrom > 0) sActiveMs += now - sActiveFrom; sActiveFrom = 0 } else if (sZikr != null) sActiveFrom = now
        paused = p
    }

    private fun noteSession(id: String, n: Int) {
        val now = System.currentTimeMillis()
        if (sZikr != id) { endSession(); sZikr = id; sCount = 0; sStart = now; sActiveMs = 0; sActiveFrom = if (paused) 0 else now }
        sCount = (sCount + n).coerceAtLeast(0)
    }

    /** Saves the sitting in progress to history. */
    fun endSession() {
        val id = sZikr ?: return
        val now = System.currentTimeMillis()
        val active = sessionMs()
        val n = sCount
        sZikr = null; sCount = 0; sActiveMs = 0; sActiveFrom = 0
        if (n > 0) update { d -> d.copy(sessions = (d.sessions + ZSession(id, d.activeGroupId, n, sStart, now, active)).takeLast(5000)) }
    }

    fun deleteSession(s: ZSession) = update { d -> d.copy(sessions = d.sessions - s) }

    fun exportJson(): String = json.encodeToString(_data.value)
    fun importJson(text: String): Boolean = runCatching {
        val d = json.decodeFromString<ZikirData>(text)
        endSession(); update { d }
    }.isSuccess

    /** Adds [n] beads (negative takes beads off) with the counter's step. Returns true when a round or group item completed. */
    fun add(id: String, n: Int): Boolean {
        if (paused || n == 0) return false
        if (n < 0) { repeat(-n) { undo(id) }; noteSession(id, n); update { d -> if (d.activeGroupId != null) d.copy(groupCount = (d.groupCount + n).coerceAtLeast(0)) else d }; return false }
        noteSession(id, n)
        var done = tap(id, n)
        // Group: move on to the next zikir when this one's count is reached.
        val d = _data.value
        val g = d.groups.firstOrNull { it.id == d.activeGroupId }
        if (g != null) {
            val item = g.items.getOrNull(d.groupPos)
            val c = d.groupCount + n
            if (item != null && c >= item.count) {
                done = true
                endSession()
                val next = d.groupPos + 1
                update { x -> if (next >= g.items.size) x.copy(activeGroupId = null, groupPos = 0, groupCount = 0, round = x.round + (id to 0))
                    else x.copy(groupPos = next, groupCount = 0, selectedId = g.items[next].zikrId, round = x.round + (id to 0)) }
                groupJustFinished = next >= g.items.size
            } else update { x -> x.copy(groupCount = c) }
        }
        return done
    }

    /** True right after the last item of a group was completed (read once). */
    var groupJustFinished = false

    /** One tasbih bead. Returns true when this tap completed a round. */
    fun tap(id: String, n: Int = 1): Boolean {
        var done = false
        update { d ->
            val z = d.zikrs.firstOrNull { it.id == id } ?: return@update d
            val key = "$id|${LocalDateTime.now().format(HOUR)}"
            var r = (d.round[id] ?: 0) + n
            var rounds = d.rounds[id] ?: 0
            if (z.target > 0 && r >= z.target) { done = true; rounds += r / z.target; r %= z.target }
            d.copy(
                hourly = d.hourly + (key to (d.hourly[key] ?: 0) + n),
                round = d.round + (id to r),
                rounds = d.rounds + (id to rounds),
            )
        }
        return done
    }

    /** Undo the last bead (takes it off this hour's count and the round). */
    fun undo(id: String) = update { d ->
        val key = "$id|${LocalDateTime.now().format(HOUR)}"
        val h = d.hourly[key] ?: 0
        if (h <= 0) return@update d
        val r = d.round[id] ?: 0
        val z = d.zikrs.firstOrNull { it.id == id }
        val (nr, nrounds) = if (r > 0) (r - 1) to (d.rounds[id] ?: 0)
        else if ((d.rounds[id] ?: 0) > 0 && z != null) (z.target - 1) to ((d.rounds[id] ?: 0) - 1) else 0 to (d.rounds[id] ?: 0)
        d.copy(hourly = d.hourly + (key to h - 1), round = d.round + (id to nr), rounds = d.rounds + (id to nrounds))
    }

    fun resetRound(id: String) { endSession(); update { it.copy(round = it.round + (id to 0), groupCount = 0) } }

    fun upsert(z: Zikr) = update { d ->
        if (d.zikrs.any { it.id == z.id }) d.copy(zikrs = d.zikrs.map { if (it.id == z.id) z else it })
        else d.copy(zikrs = d.zikrs + z.copy(order = (d.zikrs.maxOfOrNull { it.order } ?: -1) + 1), selectedId = z.id)
    }

    /** Removes a zikir from the list; its past counts stay in the totals. */
    fun delete(id: String) = update { d ->
        val rest = d.zikrs.filterNot { it.id == id }
        d.copy(zikrs = rest, selectedId = if (d.selectedId == id) rest.firstOrNull()?.id else d.selectedId)
    }

    /** Counts per hour start for [zikrId] (null = all zikir). */
    fun perHour(d: ZikirData, zikrId: String?): Map<LocalDateTime, Int> {
        val out = HashMap<LocalDateTime, Int>()
        d.hourly.forEach { (k, v) ->
            val i = k.lastIndexOf('|'); if (i < 0) return@forEach
            if (zikrId != null && k.substring(0, i) != zikrId) return@forEach
            val t = runCatching { LocalDateTime.parse(k.substring(i + 1) + ":00") }.getOrNull() ?: return@forEach
            out[t] = (out[t] ?: 0) + v
        }
        return out
    }

    fun todayFor(d: ZikirData, zikrId: String?): Int {
        val today = java.time.LocalDate.now()
        return perHour(d, zikrId).filterKeys { it.toLocalDate() == today }.values.sum()
    }
}
