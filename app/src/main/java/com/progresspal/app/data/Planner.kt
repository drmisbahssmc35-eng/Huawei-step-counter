package com.progresspal.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** A planned block of time on the timeline. */
@Serializable
data class PlanItem(
    val id: String = UUID.randomUUID().toString(),
    val start: Long,
    val end: Long,
    val title: String,
    /** "task", "project", "activity" or "custom" */
    val kind: String = "custom",
    val refId: String? = null,
    val subtitle: String? = null,
    /** The Simple Time Tracker activity whose tracked time fulfils this plan. */
    val activityId: Long? = null,
    val activityName: String? = null,
    val color: Long? = null,
)

@Serializable
data class PlannerData(
    val enabled: Boolean = true,
    val plans: List<PlanItem> = emptyList(),
    /** Remembers which activity was picked for a task/project, to pre-select it next time. */
    val lastActivityFor: Map<String, Long> = emptyMap(),
)

enum class PlanState { UPCOMING, ACTIVE, DONE, MISSED }

/** A plan counts as done when at least this share of its time was tracked on its activity. */
const val PLAN_SUCCESS_SHARE = 0.8f

object Planner {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: File
    private val _data = MutableStateFlow(PlannerData())
    val data: StateFlow<PlannerData> = _data

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, "planner.json")
        if (file.exists()) runCatching { _data.value = json.decodeFromString<PlannerData>(file.readText()) }
    }

    private fun update(f: (PlannerData) -> PlannerData) {
        val next = f(_data.value)
        _data.value = next
        if (::file.isInitialized) runCatching { file.writeText(json.encodeToString(next)) }
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }

    fun save(p: PlanItem) = update { d ->
        val plans = if (d.plans.any { it.id == p.id }) d.plans.map { if (it.id == p.id) p else it } else d.plans + p
        val memo = if (p.refId != null && p.activityId != null) d.lastActivityFor + ("${p.kind}:${p.refId}" to p.activityId) else d.lastActivityFor
        d.copy(plans = plans.sortedBy { it.start }, lastActivityFor = memo)
    }

    fun delete(id: String) = update { d -> d.copy(plans = d.plans.filterNot { it.id == id }) }

    fun plansBetween(from: Long, to: Long): List<PlanItem> = _data.value.plans.filter { it.end > from && it.start < to }
}

/** Milliseconds of the plan's window covered by tracked time on its activity. */
fun planCovered(p: PlanItem, stt: SttCache, now: Long): Long {
    val matches: (Long?, String?) -> Boolean = { id, name ->
        when {
            p.activityId != null -> id == p.activityId
            else -> name != null && name.equals(p.title, ignoreCase = true)
        }
    }
    val zone = ZoneId.systemDefault()
    var d = Instant.ofEpochMilli(p.start).atZone(zone).toLocalDate()
    val last = Instant.ofEpochMilli(p.end).atZone(zone).toLocalDate()
    val spans = mutableListOf<Pair<Long, Long>>()
    while (!d.isAfter(last)) {
        stt.days[d.toString()].orEmpty().forEach { r ->
            if (matches(r.activityId, r.activityName)) spans += r.startedAt to r.endedAt
        }
        d = d.plusDays(1)
    }
    val names = stt.activities.associate { it.id to it.name }
    stt.running.forEach { r -> if (matches(r.id, names[r.id])) spans += r.startedAt to now }
    // Merge overlaps so the same minute is not counted twice.
    var covered = 0L
    var curS = Long.MIN_VALUE; var curE = Long.MIN_VALUE
    spans.map { maxOf(it.first, p.start) to minOf(it.second, p.end) }
        .filter { it.second > it.first }
        .sortedBy { it.first }
        .forEach { (s, e) ->
            if (s > curE) { if (curE > curS) covered += curE - curS; curS = s; curE = e } else curE = maxOf(curE, e)
        }
    if (curE > curS) covered += curE - curS
    return covered
}

fun planState(p: PlanItem, stt: SttCache, now: Long): Pair<PlanState, Float> {
    val len = (p.end - p.start).coerceAtLeast(1)
    val share = planCovered(p, stt, now).toFloat() / len
    val state = when {
        share >= PLAN_SUCCESS_SHARE -> PlanState.DONE
        now >= p.end -> PlanState.MISSED
        now >= p.start -> PlanState.ACTIVE
        else -> PlanState.UPCOMING
    }
    return state to share
}
