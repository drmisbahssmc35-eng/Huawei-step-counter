package com.progresspal.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Single source of truth. All data lives on the device in a JSON file
 * (no account or internet needed, works on phones without Google services).
 */
object AppStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private lateinit var file: File
    private lateinit var appContext: Context
    private var initialized = false

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        file = File(context.filesDir, "progress_pal.json")
        _data.value = try {
            if (file.exists()) json.decodeFromString(AppData.serializer(), file.readText()) else AppData()
        } catch (_: Exception) {
            // Keep a copy of an unreadable file instead of silently overwriting it.
            runCatching { file.copyTo(File(context.filesDir, "progress_pal.corrupt.json"), overwrite = true) }
            AppData()
        }
        autoResetRecurring()
        Cloud.init(context)
        removeDuplicateSalatList()
        scope.launch {
            while (true) {
                delay(60_000)
                autoResetRecurring()
                Cloud.pull()
            }
        }
    }

    private fun newId() = UUID.randomUUID().toString()
    private fun now() = System.currentTimeMillis()

    private fun mutate(block: (AppData) -> AppData) {
        val old = _data.value
        _data.update(block)
        Cloud.onLocalChange(old, _data.value)
        save()
    }

    /** Replaces local data with what's in the cloud (no push back). */
    fun replaceFromCloud(fresh: AppData) {
        _data.value = fresh
        save()
        autoResetRecurring()
        removeDuplicateSalatList()
    }

    private fun save() {
        val snapshot = _data.value
        if (::appContext.isInitialized) {
            runCatching { com.progresspal.app.ProgressWidget.refresh(appContext) }
        }
        scope.launch {
            writeLock.withLock {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.encodeToString(AppData.serializer(), snapshot))
                tmp.renameTo(file)
            }
        }
    }

    // ───────────────────────── Backup ─────────────────────────

    fun exportJson(): String = json.encodeToString(AppData.serializer(), _data.value)

    fun importJson(text: String): Boolean = try {
        val parsed = json.decodeFromString(AppData.serializer(), text)
        mutate { parsed }
        true
    } catch (_: Exception) {
        false
    }

    // ───────────────────────── Recurring reset ─────────────────────────

    /** Un-checks recurring tasks whose period (day/week/month) has rolled over. */
    fun autoResetRecurring() {
        val needs = _data.value.projects.any { p ->
            p.tasks.any { t -> t.needsReset() }
        }
        if (!needs) return
        mutate { d ->
            d.copy(projects = d.projects.map { p ->
                p.copy(tasks = p.tasks.map { t ->
                    if (t.needsReset()) t.copy(completed = false, lastResetAt = now()) else t
                })
            })
        }
    }

    private fun Task.needsReset(): Boolean {
        if (!completed || !(repeat == Repeat.DAILY || repeat == Repeat.WEEKLY || repeat == Repeat.MONTHLY)) return false
        val boundary = resetBoundary(repeat)
        return lastResetAt == null || lastResetAt < boundary
    }

    // ───────────────────────── Projects ─────────────────────────

    fun project(id: String): Project? = _data.value.projects.find { it.id == id }

    fun addProject(name: String, color: ProjectColor, target: String?, deadline: Long?): String {
        val p = Project(id = newId(), name = name, color = color, target = target, deadline = deadline, createdAt = now())
        mutate { it.copy(projects = it.projects + p) }
        return p.id
    }

    fun editProject(id: String, name: String, target: String?) = updateProject(id) {
        it.copy(name = name, target = target?.ifBlank { null })
    }

    fun deleteProject(id: String) = mutate { d ->
        val removedTaskIds = d.projects.find { it.id == id }?.tasks?.map { it.id }?.toSet() ?: emptySet()
        d.copy(
            projects = d.projects.filterNot { it.id == id },
            dateCompletions = d.dateCompletions.filterKeys { it.substringBefore(':') !in removedTaskIds },
        )
    }

    fun updateCompletedAt(id: String, completedAt: Long) = updateProject(id) { it.copy(completedAt = completedAt) }

    private fun updateProject(id: String, block: (Project) -> Project) = mutate { d ->
        d.copy(projects = d.projects.map { if (it.id == id) block(it) else it })
    }

    /** Adds the five daily prayers to the "Daily Tasks" list (only used when the user has no prayer tasks yet). */
    fun enableSalat() {
        if (_data.value.salatTasks().isNotEmpty()) return
        val pid = dailyProjectId()
        SALAT_PRAYERS.forEach { (name, time) -> addTask(pid, name, repeat = Repeat.DAILY, dueTime = time) }
    }

    /**
     * An earlier version created a separate "Salat" list even when the prayers already existed in Daily Tasks.
     * Remove that duplicate list if none of its prayers were ever ticked.
     */
    fun removeDuplicateSalatList() {
        val d = _data.value
        val salat = d.salatProject() ?: return
        val ids = salat.tasks.map { it.id }.toSet()
        val elsewhere = d.projects.any { p -> p.id != salat.id && p.tasks.any { salatSlot(it.title) != null } }
        val used = d.dateCompletions.any { it.value && it.key.substringBefore(':') in ids } || salat.tasks.any { it.completed }
        if (elsewhere && !used) deleteProject(salat.id)
    }

    /** Returns the id of the "Daily Tasks" project, creating it if needed. */
    fun dailyProjectId(): String =
        _data.value.projects.find { it.name == "Daily Tasks" }?.id
            ?: addProject("Daily Tasks", ProjectColor.GREEN, null, null)

    // ───────────────────────── Tasks ─────────────────────────

    fun addTask(
        projectId: String,
        title: String,
        deadline: Long? = null,
        repeat: Repeat = Repeat.NONE,
        dueTime: String? = null,
        repeatDays: List<String>? = null,
    ) {
        val t = Task(
            id = newId(), title = title, createdAt = now(), deadline = deadline, repeat = repeat,
            dueTime = dueTime, repeatDays = repeatDays?.takeIf { it.isNotEmpty() },
        )
        updateProject(projectId) { it.copy(tasks = it.tasks + t) }
    }

    /** Adds a task that repeats every day to "Daily Tasks" and returns its id. */
    fun addDailyTask(title: String): String {
        val pid = dailyProjectId()
        val t = Task(id = newId(), title = title, createdAt = now(), repeat = Repeat.DAILY)
        updateProject(pid) { it.copy(tasks = it.tasks + t) }
        return t.id
    }

    fun editTask(projectId: String, taskId: String, block: (Task) -> Task) = updateProject(projectId) { p ->
        p.copy(tasks = p.tasks.map { if (it.id == taskId) block(it) else it }).withCompletion()
    }

    fun deleteTask(projectId: String, taskId: String) = mutate { d ->
        d.copy(
            projects = d.projects.map { p ->
                if (p.id == projectId) p.copy(tasks = p.tasks.filterNot { it.id == taskId }).withCompletion() else p
            },
            dateCompletions = d.dateCompletions.filterKeys { it.substringBefore(':') != taskId },
        )
    }

    /** Toggles a task's own completed flag (one-off tasks, and project-level view of recurring ones). */
    fun toggleTask(projectId: String, taskId: String) = mutate { d ->
        var newCompleted = false
        val projects = d.projects.map { p ->
            if (p.id != projectId) return@map p
            p.copy(tasks = p.tasks.map { t ->
                if (t.id != taskId) return@map t
                newCompleted = !t.completed
                t.copy(
                    completed = newCompleted,
                    lastResetAt = if (newCompleted && t.repeat != Repeat.NONE) now() else t.lastResetAt,
                )
            }).withCompletion()
        }
        d.copy(projects = projects, completions = d.completions.record(taskId, today(), newCompleted))
    }

    /** Toggles completion of a recurring task for a specific date. */
    fun toggleDateTask(taskId: String, date: LocalDate) = mutate { d ->
        val key = "$taskId:${date.key()}"
        val newVal = !(d.dateCompletions[key] ?: false)
        d.copy(
            dateCompletions = d.dateCompletions + (key to newVal),
            completions = d.completions.record(taskId, date, newVal),
        )
    }

    fun setDateTask(taskId: String, date: LocalDate, completed: Boolean) {
        if ((_data.value.dateCompletions["$taskId:${date.key()}"] ?: false) != completed) toggleDateTask(taskId, date)
    }

    /** Creates a one-time override of a recurring task on [date] (used for "edit / delete only for this day"). */
    fun duplicateTaskForDate(projectId: String, taskId: String, date: LocalDate, title: String?, dueTime: String?, hidden: Boolean = false) {
        val task = project(projectId)?.tasks?.find { it.id == taskId } ?: return
        val time = parseTime(dueTime ?: task.dueTime)
        val deadline = if (time != null) date.atTime(time).millis() else date.atTime(23, 59, 59).millis()
        val t = Task(
            id = newId(), title = title?.ifBlank { null } ?: task.title, createdAt = now(), deadline = deadline,
            repeat = Repeat.ONCE, dueTime = dueTime ?: task.dueTime, sourceTaskId = taskId, completed = hidden,
        )
        updateProject(projectId) { it.copy(tasks = it.tasks + t) }
    }

    private fun Project.withCompletion(): Project {
        // Lists made only of scheduled/recurring tasks (e.g. "Daily Tasks") never "finish".
        val allDone = tasks.isNotEmpty() && tasks.all { it.completed } && !isAllRecurring()
        return copy(completedAt = if (allDone) (completedAt ?: now()) else null)
    }

    private fun List<CompletionRecord>.record(taskId: String, date: LocalDate, completed: Boolean): List<CompletionRecord> {
        val others = filterNot { it.taskId == taskId && it.date == date.key() }
        return if (completed) others + CompletionRecord(taskId, date.key(), true, now()) else others
    }

    // ───────────────────────── Challenges ─────────────────────────

    fun createChallenge(name: String, durationMinutes: Int, startAt: Long, scheduled: Boolean, tasks: List<ChallengeTask>) {
        val c = Challenge(
            id = newId(), name = name, durationMinutes = durationMinutes, startedAt = startAt,
            status = if (scheduled) ChallengeStatus.SCHEDULED else ChallengeStatus.ACTIVE,
            tasks = tasks.map { it.copy(id = newId()) }, createdAt = now(),
        )
        mutate { it.copy(challenges = listOf(c) + it.challenges) }
    }

    fun updateChallenge(id: String, block: (Challenge) -> Challenge) = mutate { d ->
        d.copy(challenges = d.challenges.map { if (it.id == id) block(it) else it })
    }

    fun deleteChallenge(id: String) = mutate { d -> d.copy(challenges = d.challenges.filterNot { it.id == id }) }
}
