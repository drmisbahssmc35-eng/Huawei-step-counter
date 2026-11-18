package com.progresspal.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Productivity Level: every prayer (salat) done = 10 points, every other completed task
 * (routine / daily / project task) = 1 point, every plan done on the Planner = 1 point.
 * Example: 3 prayers + 8 tasks = 30 + 8 = 38.
 */
const val SALAT_POINTS = 10
const val TASK_POINTS = 1
const val PLAN_POINTS = 1

enum class PointKind { SALAT, TASK, PLAN }

data class PointEvent(val date: LocalDate, val hour: Int, val kind: PointKind) {
    val points: Int get() = when (kind) { PointKind.SALAT -> SALAT_POINTS; PointKind.TASK -> TASK_POINTS; PointKind.PLAN -> PLAN_POINTS }
}

data class DayLevel(val salat: Int = 0, val tasks: Int = 0, val plans: Int = 0) {
    val total: Int get() = salat * SALAT_POINTS + tasks * TASK_POINTS + plans * PLAN_POINTS
}

/** Every point-earning event in the user's history. */
fun productivityEvents(app: AppData, planner: PlannerData, stt: SttCache, now: Long = System.currentTimeMillis()): List<PointEvent> {
    val zone = ZoneId.systemDefault()
    val tasks = app.projects.flatMap { it.tasks }.associateBy { it.id }
    val out = mutableListOf<PointEvent>()

    // Completed tasks (one record per task per day).
    app.completions.filter { it.completed }
        .distinctBy { it.taskId to it.date }
        .forEach { r ->
            val date = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: return@forEach
            val task = tasks[r.taskId]
            val ticked = Instant.ofEpochMilli(r.createdAt).atZone(zone)
            // Hour of the day it was done; if ticked on another day, use the task's time (or noon).
            val hour = if (ticked.toLocalDate() == date) ticked.hour
            else task?.dueTime?.substringBefore(':')?.toIntOrNull()?.coerceIn(0, 23) ?: 12
            val salat = task != null && salatSlot(task.title) != null
            out += PointEvent(date, hour, if (salat) PointKind.SALAT else PointKind.TASK)
        }

    // Plans done on the Planner (checked against tracked time).
    if (planner.plans.isNotEmpty()) {
        val first = planner.plans.minOf { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }
        val today = LocalDate.now()
        var d = first
        while (!d.isAfter(today)) {
            occurrencesOn(planner, d).forEach { p ->
                if (planState(p, stt, now).first == PlanState.DONE) {
                    out += PointEvent(d, Instant.ofEpochMilli(minOf(p.end, now)).atZone(zone).hour, PointKind.PLAN)
                }
            }
            d = d.plusDays(1)
        }
    }
    return out
}

fun List<PointEvent>.byDay(): Map<LocalDate, DayLevel> =
    groupBy { it.date }.mapValues { (_, l) ->
        DayLevel(
            salat = l.count { it.kind == PointKind.SALAT },
            tasks = l.count { it.kind == PointKind.TASK },
            plans = l.count { it.kind == PointKind.PLAN },
        )
    }
