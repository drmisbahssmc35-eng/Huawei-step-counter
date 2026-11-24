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
    }

    @Synchronized
    private fun update(f: (ZikirData) -> ZikirData) {
        val next = f(_data.value)
        _data.value = next
        if (::file.isInitialized) runCatching { file.writeText(json.encodeToString(next)) }
    }

    fun select(id: String) = update { it.copy(selectedId = id) }
    fun setVibrate(on: Boolean) = update { it.copy(vibrate = on) }

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

    fun resetRound(id: String) = update { it.copy(round = it.round + (id to 0)) }

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
