package com.progresspal.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/* ------------------------------------------------------------------------------------------
 * Link to the "Simple Time Tracker" app (Play Store, 1.61+), through its automation broadcasts.
 *  - Reading: ACTION_QUERY_* → it answers with ACTION_RESPONSE_* broadcasts carrying JSON in
 *    the "data" extra. Needs "Answer queries" switched on in its Settings → Automated tracking.
 *  - Writing: ACTION_START_ACTIVITY / ACTION_STOP_ACTIVITY with the activity name.
 * Everything read is cached on the phone, so days already synced stay available.
 * ------------------------------------------------------------------------------------------ */

@Serializable
data class SttActivity(val id: Long, val name: String, val icon: String = "", val color: Long = 0xFF888888)

@Serializable
data class SttRunning(val id: Long, val startedAt: Long)

@Serializable
data class SttRecord(
    val id: Long = 0,
    val type: String = "TRACKED",
    val activityId: Long? = null,
    val activityName: String? = null,
    val activityIcon: String? = null,
    val activityColor: Long? = null,
    val startedAt: Long,
    val endedAt: Long,
)

@Serializable
data class SttCache(
    val activities: List<SttActivity> = emptyList(),
    val running: List<SttRunning> = emptyList(),
    /** "yyyy-MM-dd" → finished records of that day. */
    val days: Map<String, List<SttRecord>> = emptyMap(),
    val lastSync: Long = 0,
    /** Starting an activity from Progress Pal stops the one that is running ("switch"). */
    val switchMode: Boolean = true,
    /** Records hidden in Progress Pal (Simple Time Tracker does not let other apps delete them). */
    val hidden: List<Long> = emptyList(),
    /** Keep a Daily Task for every Simple Time Tracker activity. */
    val syncDailyTasks: Boolean = true,
    /** Simple Time Tracker activity id → Progress Pal task id it is paired with. */
    val linkedTasks: Map<Long, String> = emptyMap(),
    /** Tick the paired Daily Task once its activity was tracked this many minutes that day (0 = off). */
    val autoCompleteMinutes: Int = 1,
    /** "taskId:yyyy-MM-dd" already ticked automatically, so a manual un-tick is respected. */
    val autoDone: List<String> = emptyList(),
)

enum class SttStatus { UNKNOWN, SYNCING, OK, NOT_INSTALLED, NO_ANSWER }

object SimpleTimeTracker {
    const val PKG = "com.razeeman.util.simpletimetracker"
    private const val A = "com.razeeman.util.simpletimetracker."
    private const val QUERY_ACTIVITIES = A + "ACTION_QUERY_ACTIVITIES"
    private const val QUERY_RUNNING = A + "ACTION_QUERY_RUNNING"
    private const val QUERY_RECORDS = A + "ACTION_QUERY_RECORDS"
    private const val RESP_ACTIVITIES = A + "ACTION_RESPONSE_ACTIVITIES"
    private const val RESP_RUNNING = A + "ACTION_RESPONSE_RUNNING"
    private const val RESP_RECORDS = A + "ACTION_RESPONSE_RECORDS"
    private const val START = A + "ACTION_START_ACTIVITY"
    private const val STOP = A + "ACTION_STOP_ACTIVITY"
    private const val STOP_ALL = A + "ACTION_STOP_ALL_ACTIVITIES"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private lateinit var appContext: Context
    private lateinit var file: File

    private val _cache = MutableStateFlow(SttCache())
    val cache: StateFlow<SttCache> = _cache
    private val _status = MutableStateFlow(SttStatus.UNKNOWN)
    val status: StateFlow<SttStatus> = _status

    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = File(appContext.filesDir, "simple_time_tracker.json")
        if (file.exists()) runCatching { _cache.value = json.decodeFromString<SttCache>(file.readText()) }
    }

    fun isInstalled(): Boolean =
        runCatching { appContext.packageManager.getPackageInfo(PKG, 0); true }.getOrDefault(false)

    fun openApp(context: Context) {
        context.packageManager.getLaunchIntentForPackage(PKG)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun recordsOn(date: LocalDate): List<SttRecord> = _cache.value.days[date.toString()].orEmpty()

    /** Sync in the background. More days the first time, then the last week. */
    fun syncSoon(days: Int? = null) {
        if (!::file.isInitialized) return
        scope.launch { sync(days ?: if (_cache.value.days.isEmpty()) 30 else 7) }
    }

    /** Fetch one specific day (e.g. when it is opened on the timeline). */
    fun syncDay(date: LocalDate) {
        val shift = (date.toEpochDay() - LocalDate.now().toEpochDay()).toInt()
        if (shift > 0) return
        scope.launch { sync(days = 1, fromShift = shift, withActivities = false) }
    }

    private suspend fun sync(days: Int, fromShift: Int = 0, withActivities: Boolean = true) = lock.withLock {
        if (!isInstalled()) { _status.value = SttStatus.NOT_INSTALLED; return@withLock }
        _status.value = SttStatus.SYNCING

        val inbox = Channel<Intent>(Channel.UNLIMITED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { inbox.trySend(i) }
        }
        val filter = IntentFilter().apply {
            addAction(RESP_ACTIVITIES); addAction(RESP_RUNNING); addAction(RESP_RECORDS)
        }
        withContext(Dispatchers.Main) {
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }

        suspend fun ask(action: String, answer: String, extras: Intent.() -> Unit = {}): String? {
            while (inbox.tryReceive().isSuccess) { /* drop stale answers */ }
            appContext.sendBroadcast(command(action).apply(extras))
            return withTimeoutOrNull(5000) {
                var got: String? = null
                while (got == null) {
                    val i = inbox.receive()
                    if (i.action == answer) got = i.getStringExtra("data") ?: ""
                }
                got
            }
        }

        try {
            var c = _cache.value
            if (withActivities) {
                val acts = ask(QUERY_ACTIVITIES, RESP_ACTIVITIES) { putExtra("extra_answer_type", "json") }
                if (acts == null) { _status.value = SttStatus.NO_ANSWER; return@withLock }
                runCatching { c = c.copy(activities = json.decodeFromString<List<SttActivity>>(acts)) }
                if (c.syncDailyTasks) c = withContext(Dispatchers.Main) { pairDailyTasks(c) }
            }
            ask(QUERY_RUNNING, RESP_RUNNING) { putExtra("extra_answer_type", "json") }?.let { r ->
                runCatching { c = c.copy(running = json.decodeFromString<List<SttRunning>>(r)) }
            }
            val today = LocalDate.now()
            val days2 = c.days.toMutableMap()
            val syncedDates = mutableListOf<LocalDate>()
            var answered = false
            for (k in 0 until days) {
                val shift = fromShift - k
                val data = ask(QUERY_RECORDS, RESP_RECORDS) { putExtra("extra_shift", shift) } ?: break
                answered = true
                val list = runCatching { json.decodeFromString<List<SttRecord>>(data) }.getOrNull() ?: continue
                syncedDates += today.plusDays(shift.toLong())
                days2[today.plusDays(shift.toLong()).toString()] =
                    list.filter { it.type == "TRACKED" && it.id !in c.hidden }.sortedBy { it.startedAt }
            }
            c = c.copy(days = days2, lastSync = System.currentTimeMillis())
            if (c.syncDailyTasks && c.autoCompleteMinutes > 0 && syncedDates.isNotEmpty()) {
                val snapshot = c
                c = withContext(Dispatchers.Main) { autoComplete(snapshot, syncedDates) }
            }
            save(c)
            _status.value = if (answered || !withActivities) SttStatus.OK else SttStatus.NO_ANSWER
        } finally {
            runCatching { appContext.unregisterReceiver(receiver) }
            inbox.close()
        }
    }

    private fun command(action: String) = Intent(action)
        .setPackage(PKG)
        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)

    private fun save(c: SttCache) {
        _cache.value = c
        runCatching { file.writeText(json.encodeToString(c)) }
    }

    fun isRunning(activityId: Long) = _cache.value.running.any { it.id == activityId }

    /** Start if stopped, stop if running — inside Simple Time Tracker. */
    fun toggle(a: SttActivity) {
        val running = isRunning(a.id)
        appContext.sendBroadcast(command(if (running) STOP else START).putExtra("extra_activity_name", a.name))
        // Show the change at once, then read back the real state.
        val c = _cache.value
        save(
            if (running) c.copy(running = c.running.filterNot { it.id == a.id })
            else c.copy(running = c.running + SttRunning(a.id, System.currentTimeMillis()))
        )
        scope.launch { delay(900); sync(days = 1) }
    }

    /** Hide a finished record from Progress Pal's timeline, stats and plan checks. */
    fun hideRecord(id: Long) {
        val c = _cache.value
        save(c.copy(hidden = c.hidden + id, days = c.days.mapValues { (_, l) -> l.filterNot { it.id == id } }))
    }

    /**
     * Makes sure every activity has one Daily Task, without duplicates:
     * an activity is paired with an existing task of the same name (ignoring case, spaces and a
     * leading number such as "17."), otherwise a daily task is created. Renamed activities rename
     * their task. A paired task the user deleted is not created again.
     */
    private fun pairDailyTasks(c: SttCache): SttCache {
        val tasks = AppStore.data.value.projects.flatMap { it.tasks }
        val byId = tasks.associateBy { it.id }
        val links = c.linkedTasks.toMutableMap()
        val seen = mutableSetOf<String>()
        c.activities.forEach { a ->
            val key = normName(a.name)
            if (key.isEmpty() || !seen.add(key)) return@forEach
            val linkedId = links[a.id]
            if (linkedId != null) {
                val t = byId[linkedId] ?: return@forEach // deleted by the user: leave it deleted
                if (normName(t.title) != key) {
                    AppStore.data.value.projects.firstOrNull { p -> p.tasks.any { it.id == t.id } }?.let { p ->
                        AppStore.editTask(p.id, t.id) { it.copy(title = a.name) }
                    }
                }
                return@forEach
            }
            val existing = tasks.firstOrNull { normName(it.title) == key && it.id !in links.values }
            links[a.id] = existing?.id ?: AppStore.addDailyTask(a.name)
        }
        return c.copy(linkedTasks = links)
    }

    /** Ticks the paired Daily Task on each day its activity was tracked long enough. */
    private fun autoComplete(c: SttCache, dates: List<LocalDate>): SttCache {
        val minMs = c.autoCompleteMinutes * 60_000L
        val done = c.autoDone.toMutableSet()
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        dates.forEach { date ->
            val totals = HashMap<Long, Long>()
            c.days[date.toString()].orEmpty().forEach { r ->
                val id = r.activityId ?: return@forEach
                totals[id] = (totals[id] ?: 0L) + (r.endedAt - r.startedAt)
            }
            if (date == today) {
                val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
                c.running.forEach { r -> totals[r.id] = (totals[r.id] ?: 0L) + (now - maxOf(r.startedAt, dayStart)) }
            }
            totals.forEach { (activityId, ms) ->
                if (ms < minMs) return@forEach
                val taskId = c.linkedTasks[activityId] ?: return@forEach
                val key = "$taskId:$date"
                if (key in done) return@forEach
                val project = AppStore.data.value.projects.firstOrNull { p -> p.tasks.any { it.id == taskId } } ?: return@forEach
                val task = project.tasks.first { it.id == taskId }
                if (task.repeat.isRecurring) AppStore.setDateTask(taskId, date, true)
                else if (date == today && !task.completed) AppStore.toggleTask(project.id, taskId)
                else return@forEach
                done += key
            }
        }
        // Forget entries older than 60 days.
        val cutoff = today.minusDays(60).toString()
        return c.copy(autoDone = done.filter { it.substringAfterLast(':') >= cutoff })
    }

    fun setAutoCompleteMinutes(m: Int) {
        save(_cache.value.copy(autoCompleteMinutes = m))
        if (m > 0) syncSoon(7)
    }

    /** Daily tasks that have no activity in Simple Time Tracker yet. */
    fun dailyTasksMissingInStt(): List<String> {
        val c = _cache.value
        val names = c.activities.map { normName(it.name) }.toSet()
        val daily = AppStore.data.value.projects.firstOrNull { it.name == "Daily Tasks" } ?: return emptyList()
        return daily.tasks.filter { it.repeat.isRecurring && normName(it.title) !in names }.map { it.title }.distinct()
    }

    fun setSyncDailyTasks(on: Boolean) {
        save(_cache.value.copy(syncDailyTasks = on))
        if (on) syncSoon(1)
    }

    fun setSwitchMode(on: Boolean) = save(_cache.value.copy(switchMode = on))

    /**
     * Start [a] in Simple Time Tracker. In switch mode the running activities are stopped first.
     * Does nothing if [a] is already running.
     */
    fun start(a: SttActivity) {
        if (isRunning(a.id)) return
        val c = _cache.value
        val switching = c.switchMode && c.running.isNotEmpty()
        if (switching) appContext.sendBroadcast(command(STOP_ALL))
        appContext.sendBroadcast(command(START).putExtra("extra_activity_name", a.name))
        val now = System.currentTimeMillis()
        save(c.copy(running = (if (switching) emptyList() else c.running) + SttRunning(a.id, now)))
        scope.launch { delay(900); sync(days = 1) }
    }

    /** Tile tap: stop if running, otherwise start (switching if switch mode is on). */
    fun tap(a: SttActivity) = if (isRunning(a.id)) toggle(a) else start(a)

    fun stopAll() {
        appContext.sendBroadcast(command(STOP_ALL))
        save(_cache.value.copy(running = emptyList()))
        scope.launch { delay(900); sync(days = 1) }
    }
}

/** "17. Sleep " → "sleep": compares names ignoring case, extra spaces and a leading number. */
fun normName(s: String): String =
    s.trim().lowercase().replace(Regex("^\\d+\\s*[.)\\-:]?\\s*"), "").replace(Regex("\\s+"), " ")
