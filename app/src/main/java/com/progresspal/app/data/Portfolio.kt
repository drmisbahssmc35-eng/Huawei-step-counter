package com.progresspal.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** One stage of a project on the portfolio timeline (e.g. Planning, Operations, Sign off). */
@Serializable
data class PortfolioPhase(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** yyyy-MM-dd */
    val start: String,
    val end: String,
)

/** How a project is drawn on the portfolio: its dates, colour, phases, and whether it is shown. */
@Serializable
data class PortfolioEntry(
    val start: String? = null,
    val end: String? = null,
    /** ARGB; null = the project's own colour. */
    val color: Long? = null,
    val phases: List<PortfolioPhase> = emptyList(),
    val hidden: Boolean = false,
    /** Lower = higher in the list. */
    val order: Int = 0,
)

@Serializable
data class PortfolioData(
    val entries: Map<String, PortfolioEntry> = emptyMap(),
    val showCompleted: Boolean = false,
    /** Old setting, no longer used (kept so saved files still load). */
    val scale: String = "month",
    /** "hour", "day" (default), "week", "month" or "quarter" — how wide a day is drawn. */
    val zoom: String = "day",
)

/** Project portfolio settings, kept on the phone. */
object Portfolio {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: File
    private val _data = MutableStateFlow(PortfolioData())
    val data: StateFlow<PortfolioData> = _data

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, "portfolio.json")
        if (file.exists()) runCatching { _data.value = json.decodeFromString<PortfolioData>(file.readText()) }
    }

    private fun update(f: (PortfolioData) -> PortfolioData) {
        val next = f(_data.value)
        _data.value = next
        if (::file.isInitialized) runCatching { file.writeText(json.encodeToString(next)) }
    }

    fun entry(projectId: String): PortfolioEntry = _data.value.entries[projectId] ?: PortfolioEntry()

    fun save(projectId: String, e: PortfolioEntry) = update { it.copy(entries = it.entries + (projectId to e)) }

    fun setScale(s: String) = update { it.copy(zoom = s) }
    fun setShowCompleted(on: Boolean) = update { it.copy(showCompleted = on) }

    /** Moves a project up (-1) or down (+1) among [ids] (the current visible order). */
    fun move(ids: List<String>, projectId: String, by: Int) = update { d ->
        val list = ids.toMutableList()
        val i = list.indexOf(projectId); val j = i + by
        if (i < 0 || j !in list.indices) return@update d
        list.add(j, list.removeAt(i))
        val entries = d.entries.toMutableMap()
        list.forEachIndexed { idx, id -> entries[id] = (entries[id] ?: PortfolioEntry()).copy(order = idx) }
        d.copy(entries = entries)
    }
}

/** Start and end shown for a project: its custom dates, else created → deadline (or +30 days). */
fun portfolioRange(p: Project, e: PortfolioEntry): Pair<LocalDate, LocalDate> {
    val zone = ZoneId.systemDefault()
    val start = e.start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: e.phases.mapNotNull { runCatching { LocalDate.parse(it.start) }.getOrNull() }.minOrNull()
        ?: Instant.ofEpochMilli(p.createdAt).atZone(zone).toLocalDate()
    val end = e.end?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: e.phases.mapNotNull { runCatching { LocalDate.parse(it.end) }.getOrNull() }.maxOrNull()
        ?: p.deadline?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: start.plusDays(30)
    return start to (if (end.isBefore(start)) start else end)
}
