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
    /** "NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY" or "CUSTOM" (on [days]). */
    val repeat: String = "NONE",
    /** For CUSTOM: day labels "Sat".."Fri". */
    val days: List<String> = emptyList(),
    /** Dates ("yyyy-MM-dd") where a repeating plan was removed for that day only. */
    val skip: List<String> = emptyList(),
    /** Optional note written when planning. */
    val note: String = "",
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

    private var appContext: Context? = null

    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = File(context.applicationContext.filesDir, "planner.json")
        if (file.exists()) runCatching { _data.value = json.decodeFromString<PlannerData>(file.readText()) }
    }

    private fun update(f: (PlannerData) -> PlannerData) {
        val next = f(_data.value)
        _data.value = next
        if (::file.isInitialized) runCatching { file.writeText(json.encodeToString(next)) }
        appContext?.let { com.progresspal.app.DayWidget.refresh(it) }
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }

    fun save(p: PlanItem) = update { d ->
        val plans = if (d.plans.any { it.id == p.id }) d.plans.map { if (it.id == p.id) p else it } else d.plans + p
        val memo = if (p.refId != null && p.activityId != null) d.lastActivityFor + ("${p.kind}:${p.refId}" to p.activityId) else d.lastActivityFor
        d.copy(plans = plans.sortedBy { it.start }, lastActivityFor = memo)
    }

    fun delete(id: String) = update { d -> d.copy(plans = d.plans.filterNot { it.id == id }) }

    /** Removes one day of a repeating plan. */
    fun skipDay(id: String, date: java.time.LocalDate) = update { d ->
        d.copy(plans = d.plans.map { if (it.id == id) it.copy(skip = it.skip + date.toString()) else it })
    }

    fun series(id: String): PlanItem? = _data.value.plans.firstOrNull { it.id == id }

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

/**
 * The plans that happen on [date]: one-off plans touching that day, plus each repeating plan
 * moved to that day (same time of day and length) when its rule matches.
 */
fun occurrencesOn(data: PlannerData, date: java.time.LocalDate): List<PlanItem> {
    val zone = ZoneId.systemDefault()
    val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return data.plans.mapNotNull { p ->
        if (p.repeat == "NONE") return@mapNotNull p.takeIf { it.end > from && it.start < to }
        val first = Instant.ofEpochMilli(p.start).atZone(zone)
        val d0 = first.toLocalDate()
        if (date.isBefore(d0) || date.toString() in p.skip) return@mapNotNull null
        val match = when (p.repeat) {
            "DAILY" -> true
            "WEEKLY" -> date.dayOfWeek == d0.dayOfWeek
            "MONTHLY" -> date.dayOfMonth == d0.dayOfMonth
            "YEARLY" -> date.dayOfMonth == d0.dayOfMonth && date.month == d0.month
            "CUSTOM" -> date.dayLabel() in p.days
            else -> false
        }
        if (!match) return@mapNotNull null
        val start = date.atTime(first.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        p.copy(start = start, end = start + (p.end - p.start))
    }.sortedBy { it.start }
}

/** How well a day went to plan: planned time vs the part of it spent on the planned activity. */
data class PlanScore(val plannedMs: Long = 0, val matchedMs: Long = 0) {
    /** 0–100, or null when nothing was planned (yet). */
    val percent: Int? get() = if (plannedMs <= 0) null else ((matchedMs * 100) / plannedMs).toInt().coerceIn(0, 100)
    operator fun plus(o: PlanScore) = PlanScore(plannedMs + o.plannedMs, matchedMs + o.matchedMs)
}

/**
 * Planning score for one day: for every plan that has started, the planned time so far, and how
 * much of exactly that time was tracked on the plan's activity (same task, same time).
 */
fun planScoreOn(planner: PlannerData, stt: SttCache, date: java.time.LocalDate, now: Long = System.currentTimeMillis()): PlanScore =
    occurrencesOn(planner, date).fold(PlanScore()) { acc, p ->
        val end = minOf(p.end, now)
        if (end <= p.start) acc
        else acc + PlanScore(end - p.start, planCovered(p.copy(end = end), stt, now))
    }

fun planScoreBetween(planner: PlannerData, stt: SttCache, from: java.time.LocalDate, to: java.time.LocalDate, now: Long = System.currentTimeMillis()): PlanScore {
    var d = from; var s = PlanScore()
    while (!d.isAfter(to)) { s += planScoreOn(planner, stt, d, now); d = d.plusDays(1) }
    return s
}

/** First day anything was planned (for lifetime totals). */
fun firstPlanDay(planner: PlannerData): java.time.LocalDate? =
    planner.plans.minOfOrNull { Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).toLocalDate() }
