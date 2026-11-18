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
            }
            ask(QUERY_RUNNING, RESP_RUNNING) { putExtra("extra_answer_type", "json") }?.let { r ->
                runCatching { c = c.copy(running = json.decodeFromString<List<SttRunning>>(r)) }
            }
            val today = LocalDate.now()
            val days2 = c.days.toMutableMap()
            var answered = false
            for (k in 0 until days) {
                val shift = fromShift - k
                val data = ask(QUERY_RECORDS, RESP_RECORDS) { putExtra("extra_shift", shift) } ?: break
                answered = true
                val list = runCatching { json.decodeFromString<List<SttRecord>>(data) }.getOrNull() ?: continue
                days2[today.plusDays(shift.toLong()).toString()] =
                    list.filter { it.type == "TRACKED" }.sortedBy { it.startedAt }
            }
            c = c.copy(days = days2, lastSync = System.currentTimeMillis())
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

    fun stopAll() {
        appContext.sendBroadcast(command(STOP_ALL))
        save(_cache.value.copy(running = emptyList()))
        scope.launch { delay(900); sync(days = 1) }
    }
}
