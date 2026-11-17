package com.timetally.app.data

import android.content.Context
import com.timetally.app.TimerNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

/** All app data, kept in one JSON file on the phone. */
object Store {
    private lateinit var appContext: Context
    private lateinit var file: File
    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state

    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = File(appContext.filesDir, "timetally.json")
        if (file.exists()) {
            runCatching { _state.value = AppJson.decodeFromString<AppData>(file.readText()) }
        }
    }

    val data: AppData get() = _state.value

    @Synchronized
    private fun update(transform: (AppData) -> AppData) {
        val next = transform(_state.value)
        _state.value = next
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(next))
            tmp.renameTo(file)
        }
        TimerNotification.refresh(appContext, next)
    }

    fun refreshNotification() = TimerNotification.refresh(appContext, data)

    // ---------- timers ----------

    fun isRunning(typeId: Long) = data.running.any { it.typeId == typeId }

    /** Start the timer if stopped, stop (and save a record) if running. */
    fun toggle(typeId: Long) {
        if (isRunning(typeId)) stop(typeId) else start(typeId)
    }

    fun start(typeId: Long) = update { d ->
        val now = System.currentTimeMillis()
        var next = d
        if (!d.settings.multitask) {
            next.running.forEach { next = stopIn(next, it.typeId, now) }
        }
        if (next.running.any { it.typeId == typeId }) next
        else next.copy(running = next.running + Running(typeId, now))
    }

    fun stop(typeId: Long) = update { d -> stopIn(d, typeId, System.currentTimeMillis()) }

    fun stopAll() = update { d ->
        val now = System.currentTimeMillis()
        d.running.fold(d) { acc, r -> stopIn(acc, r.typeId, now) }
    }

    private fun stopIn(d: AppData, typeId: Long, now: Long): AppData {
        val r = d.running.firstOrNull { it.typeId == typeId } ?: return d
        val rest = d.running.filterNot { it.typeId == typeId }
        // Ignore accidental taps shorter than a second.
        if (now - r.start < 1000) return d.copy(running = rest)
        val rec = TimeRecord(d.nextId, r.typeId, r.start, now, r.comment)
        return d.copy(running = rest, records = d.records + rec, nextId = d.nextId + 1)
    }

    fun updateRunning(typeId: Long, start: Long, comment: String) = update { d ->
        d.copy(running = d.running.map {
            if (it.typeId == typeId) it.copy(start = start.coerceAtMost(System.currentTimeMillis()), comment = comment) else it
        })
    }

    fun cancelRunning(typeId: Long) = update { d ->
        d.copy(running = d.running.filterNot { it.typeId == typeId })
    }

    // ---------- activity types ----------

    fun saveType(t: ActivityType) = update { d ->
        if (d.types.any { it.id == t.id }) d.copy(types = d.types.map { if (it.id == t.id) t else it })
        else d.copy(types = d.types + t.copy(id = d.nextId), nextId = d.nextId + 1)
    }

    fun deleteType(id: Long) = update { d ->
        d.copy(
            types = d.types.filterNot { it.id == id },
            running = d.running.filterNot { it.typeId == id },
            records = d.records.filterNot { it.typeId == id },
        )
    }

    fun moveType(id: Long, by: Int) = update { d ->
        val list = d.types.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in list.indices) d
        else {
            val t = list.removeAt(i); list.add(j, t); d.copy(types = list)
        }
    }

    // ---------- records ----------

    /** id <= 0 means a new record. */
    fun saveRecord(r: TimeRecord) = update { d ->
        if (r.id > 0 && d.records.any { it.id == r.id }) d.copy(records = d.records.map { if (it.id == r.id) r else it })
        else d.copy(records = d.records + r.copy(id = d.nextId), nextId = d.nextId + 1)
    }

    fun deleteRecord(id: Long) = update { d -> d.copy(records = d.records.filterNot { it.id == id }) }

    fun clearRecords() = update { d -> d.copy(records = emptyList(), running = emptyList()) }

    // ---------- settings ----------

    fun saveSettings(s: Settings) = update { d -> d.copy(settings = s) }

    // ---------- backup ----------

    fun exportJson(): String = AppJson.encodeToString(data)

    /** Replaces all data. Returns false if the file is not a valid backup. */
    fun importJson(text: String): Boolean {
        val parsed = runCatching { AppJson.decodeFromString<AppData>(text) }.getOrNull() ?: return false
        val maxId = (parsed.records.map { it.id } + parsed.types.map { it.id }).maxOrNull() ?: 0
        update { parsed.copy(nextId = maxOf(parsed.nextId, maxId + 1)) }
        return true
    }

    fun exportCsv(): String {
        val d = data
        val names = d.types.associate { it.id to it.name }
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("activity,start,end,duration_minutes,comment\n")
        d.records.sortedBy { it.start }.forEach { r ->
            sb.append(esc(names[r.typeId] ?: "?")).append(',')
                .append(fmt.format(Instant.ofEpochMilli(r.start))).append(',')
                .append(fmt.format(Instant.ofEpochMilli(r.end))).append(',')
                .append("%.1f".format(java.util.Locale.US, (r.end - r.start) / 60000.0)).append(',')
                .append(esc(r.comment)).append('\n')
        }
        return sb.toString()
    }
}
