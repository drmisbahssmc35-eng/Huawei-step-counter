package com.progresspal.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A subtask under a project task or under another subtask (any depth). */
@Serializable
data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    /** The task id or the parent subtask id. */
    val parentId: String,
    val title: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class SubtaskData(val items: List<SubTask> = emptyList())

/**
 * Subtask trees, kept on the phone. Ticking the last open subtask ticks its parent (and a task's
 * last subtask ticks the task); unticking one un-ticks the parents again.
 */
object Subtasks {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: File
    private val _data = MutableStateFlow(SubtaskData())
    val data: StateFlow<SubtaskData> = _data

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, "subtasks.json")
        if (file.exists()) runCatching { _data.value = json.decodeFromString<SubtaskData>(file.readText()) }
    }

    private fun update(f: (SubtaskData) -> SubtaskData) {
        val next = f(_data.value)
        _data.value = next
        if (::file.isInitialized) runCatching { file.writeText(json.encodeToString(next)) }
    }

    fun children(parentId: String, d: SubtaskData = _data.value) = d.items.filter { it.parentId == parentId }.sortedBy { it.createdAt }

    fun add(parentId: String, title: String) = update { it.copy(items = it.items + SubTask(parentId = parentId, title = title)) }

    fun rename(id: String, title: String) = update { d -> d.copy(items = d.items.map { if (it.id == id) it.copy(title = title) else it }) }

    /** Deletes a subtask and everything under it. */
    fun delete(id: String) = update { d ->
        val gone = mutableSetOf(id)
        var grew = true
        while (grew) { val more = d.items.filter { it.parentId in gone && it.id !in gone }.map { it.id }; grew = more.isNotEmpty(); gone += more }
        d.copy(items = d.items.filterNot { it.id in gone })
    }

    /** Removes the subtasks of a deleted task. */
    fun deleteUnder(parentId: String) = children(parentId).forEach { delete(it.id) }

    /** Ticks / unticks a subtask, then lets parents follow (all done → done, any open → open). */
    fun toggle(id: String, app: AppData) {
        update { d -> d.copy(items = d.items.map { if (it.id == id) it.copy(done = !it.done) else it }) }
        var parent: String? = _data.value.items.firstOrNull { it.id == id }?.parentId
        var guard = 0
        while (parent != null && guard++ < 50) {
            val pid: String = parent
            val kids = children(pid)
            val allDone = kids.isNotEmpty() && kids.all { it.done }
            val sub = _data.value.items.firstOrNull { it.id == pid }
            if (sub != null) {
                if (sub.done != allDone) update { d -> d.copy(items = d.items.map { if (it.id == sub.id) it.copy(done = allDone) else it }) }
                parent = sub.parentId
            } else {
                // Reached a project task.
                val p = app.projects.firstOrNull { pr -> pr.tasks.any { it.id == pid } }
                val t = p?.tasks?.firstOrNull { it.id == pid }
                if (p != null && t != null && t.completed != allDone) AppStore.toggleTask(p.id, t.id)
                parent = null
            }
        }
    }

    /** 0..1: done = 1; with subtasks = average of their progress; otherwise 0. */
    fun fraction(id: String, done: Boolean, d: SubtaskData = _data.value): Float {
        if (done) return 1f
        val kids = children(id, d)
        if (kids.isEmpty()) return 0f
        return kids.map { fraction(it.id, it.done, d) }.average().toFloat()
    }

    fun countBelow(id: String, d: SubtaskData = _data.value): Pair<Int, Int> {
        val kids = children(id, d)
        return kids.count { it.done } to kids.size
    }
}

/** Project progress 0..1 counting subtasks: the average of its tasks' progress. */
fun Project.deepProgress(d: SubtaskData = Subtasks.data.value): Float =
    if (tasks.isEmpty()) 0f else tasks.map { Subtasks.fraction(it.id, it.completed, d) }.average().toFloat()
