package com.progresspal.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Week starts on Saturday, matching the web app. */
val DAY_LABELS = listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")

const val DAY_MS = 86_400_000L

fun zone(): ZoneId = ZoneId.systemDefault()

fun LocalDate.weekIndex(): Int = (dayOfWeek.value + 1) % 7
fun LocalDate.dayLabel(): String = DAY_LABELS[weekIndex()]
fun LocalDate.weekStart(): LocalDate = minusDays(weekIndex().toLong())
fun LocalDate.startMillis(): Long = atStartOfDay(zone()).toInstant().toEpochMilli()
fun LocalDate.key(): String = toString() // yyyy-MM-dd

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone()).toLocalDate()
fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone()).toLocalDateTime()
fun LocalDateTime.millis(): Long = atZone(zone()).toInstant().toEpochMilli()

fun today(): LocalDate = LocalDate.now(zone())

fun fmt(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
fun Long.format(pattern: String): String = toLocalDateTime().format(fmt(pattern))
fun LocalDate.format(pattern: String): String = format(fmt(pattern))

fun parseTime(hhmm: String?): LocalTime? = try {
    if (hhmm.isNullOrBlank()) null else LocalTime.parse(hhmm)
} catch (_: Exception) {
    null
}

fun formatTime12(hhmm: String?): String = parseTime(hhmm)?.format(fmt("h:mm a")) ?: (hhmm ?: "")

fun pad2(n: Long) = n.toString().padStart(2, '0')

/** "3d : 04h : 05m : 06s" style countdown. */
fun formatRemaining(ms: Long, sep: String = " : "): String {
    val total = max(0, ms / 1000)
    val d = total / 86400
    val h = (total % 86400) / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "${d}d$sep${pad2(h)}h$sep${pad2(m)}m$sep${pad2(s)}s"
}

/** Start of the current period for a recurring task (local time). */
fun resetBoundary(repeat: Repeat, now: LocalDate = today()): Long = when (repeat) {
    Repeat.DAILY -> now.startMillis()
    Repeat.WEEKLY -> now.weekStart().startMillis()
    Repeat.MONTHLY -> now.withDayOfMonth(1).startMillis()
    else -> 0L
}

fun Project.progress(): Int =
    if (tasks.isEmpty()) 0 else ((tasks.count { it.completed } * 100.0) / tasks.size).roundToInt()

fun Project.timeProgress(now: Long = System.currentTimeMillis()): Int? {
    val dl = deadline ?: return null
    val total = dl - createdAt
    if (total <= 0) return 100
    return min(100, max(0, (((now - createdAt).toDouble() / total) * 100).roundToInt()))
}

fun Project.isUrgent(now: Long = System.currentTimeMillis()): Boolean =
    deadline?.let { it - now in 1 until DAY_MS } ?: false

fun Project.isOverdue(now: Long = System.currentTimeMillis()): Boolean =
    deadline?.let { it - now <= 0 } ?: false

fun Project.isAllRecurring(): Boolean = tasks.isNotEmpty() && tasks.all { it.repeat != Repeat.NONE }

/** Active projects shown on home: not completed and not purely a list of recurring tasks. Urgent first, then overdue. */
fun activeProjects(projects: List<Project>): List<Project> {
    val now = System.currentTimeMillis()
    return projects
        .filter { it.completedAt == null && !it.isAllRecurring() }
        .sortedWith(compareBy({ if (it.isUrgent(now)) 0 else 1 }, { if (it.isOverdue(now)) 0 else 1 }))
}

data class DailyEntry(
    val project: Project,
    val task: Task,
    /** Completion as shown for the selected date (per-date for recurring tasks). */
    val completed: Boolean,
) {
    val isRecurring get() = task.repeat.isRecurring
}

/** Tasks scheduled on [date] — same rules as the web app's Daily Tasks page. */
fun dailyTasksFor(data: AppData, date: LocalDate): List<DailyEntry> {
    val label = date.dayLabel()
    val overridden = mutableSetOf<String>()
    data.projects.forEach { p ->
        p.tasks.forEach { t ->
            if (t.repeat == Repeat.ONCE && t.sourceTaskId != null && t.deadline != null && t.deadline.toLocalDate() == date) {
                overridden += t.sourceTaskId
            }
        }
    }
    val result = mutableListOf<DailyEntry>()
    data.projects.forEach { p ->
        if (p.completedAt != null) return@forEach
        p.tasks.forEach { t ->
            val recurring = t.repeat.isRecurring
            val onDate = t.deadline != null && t.deadline.toLocalDate() == date
            if (recurring && t.id in overridden) return@forEach
            val include = when (t.repeat) {
                Repeat.ONCE -> onDate && !(t.sourceTaskId != null && t.completed)
                Repeat.CUSTOM -> t.repeatDays?.contains(label) == true
                else -> recurring || onDate
            }
            if (include) {
                val done = if (recurring) data.dateCompletions["${t.id}:${date.key()}"] ?: false else t.completed
                result += DailyEntry(p, t, done)
            }
        }
    }
    return result.sortedBy { it.task.dueTime ?: "99:99" }
}

// ───────────────────────── Challenge difficulty ─────────────────────────

data class Difficulty(val score: Int, val level: String, val bg: Long, val fg: Long)

fun calculateDifficulty(durationMinutes: Int, timers: List<Int>): Difficulty {
    if (timers.isEmpty()) return levelFor(0)
    val count = timers.size
    val totalTaskMinutes = timers.sum() / 60.0
    val avgPerTask = totalTaskMinutes / count
    val challengeMinutes = if (durationMinutes > 0) durationMinutes.toDouble() else 60.0
    val density = min(35.0, (count / challengeMinutes) * 30 * 7)
    val pressure = max(0.0, min(35.0, (1 - (avgPerTask - 2) / 13) * 35))
    val load = min(30.0, (totalTaskMinutes / challengeMinutes) * 30)
    return levelFor(min(100.0, density + pressure + load).roundToInt())
}

private fun levelFor(score: Int): Difficulty = when {
    score >= 85 -> Difficulty(score, "Most Difficult", 0xFFDC3B3B, 0xFFFFFFFF)
    score >= 70 -> Difficulty(score, "Very Difficult", 0xFFE5571A, 0xFFFFFFFF)
    score >= 55 -> Difficulty(score, "Difficult", 0xFFF2A60D, 0xFF4A3205)
    score >= 40 -> Difficulty(score, "Medium", 0xFF1A9FE5, 0xFFFFFFFF)
    score >= 20 -> Difficulty(score, "Easy", 0xFF31C47F, 0xFF0C3D26)
    else -> Difficulty(score, "Very Easy", 0xFF7DD9AF, 0xFF133D2A)
}

fun Challenge.difficulty() = calculateDifficulty(durationMinutes, tasks.map { it.timerSeconds })

/** When a finished challenge actually ended, or null if it never really ran. */
fun Challenge.endedAt(): Long? {
    val scheduledEnd = startedAt + durationMinutes * 60_000L + totalPausedMs
    return when (status) {
        ChallengeStatus.FAILED -> when {
            completedAt == null -> scheduledEnd
            completedAt < startedAt -> null
            else -> min(completedAt, scheduledEnd)
        }
        ChallengeStatus.COMPLETED -> completedAt ?: scheduledEnd
        else -> null
    }
}

// ───────────────────────── Salat ─────────────────────────

const val SALAT_PROJECT = "Salat"

/** Prayer name → default reminder time (used only for ordering / display). */
val SALAT_PRAYERS = listOf("Fajr" to "05:00", "Dhuhr" to "13:00", "Asr" to "16:30", "Maghrib" to "18:00", "Isha" to "20:00")

fun AppData.salatProject(): Project? = projects.find { it.name == SALAT_PROJECT }

/** The daily prayer tasks, in prayer order. */
fun AppData.salatTasks(): List<Task> {
    val tasks = salatProject()?.tasks?.filter { it.repeat == Repeat.DAILY && it.sourceTaskId == null } ?: return emptyList()
    val order = SALAT_PRAYERS.map { it.first }
    return tasks.sortedBy { t -> order.indexOf(t.title).let { if (it < 0) 99 else it } }
}

fun AppData.prayed(task: Task, date: LocalDate): Boolean = dateCompletions["${task.id}:${date.key()}"] == true

fun AppData.prayedCount(date: LocalDate, tasks: List<Task> = salatTasks()): Int = tasks.count { prayed(it, date) }

data class StreakRun(val start: LocalDate, val end: LocalDate) {
    val length: Int get() = (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1).toInt()
}

data class SalatStats(
    val enabled: Boolean,
    /** Consecutive days with all prayers, ending today (or yesterday if today isn't finished yet). */
    val current: Int,
    val longest: StreakRun?,
    /** Every unbroken run of complete days, oldest first. */
    val runs: List<StreakRun>,
    val perfectDays: Int,
    val todayCount: Int,
    val total: Int,
)

fun AppData.salatStats(today: LocalDate = today()): SalatStats {
    val tasks = salatTasks()
    if (tasks.isEmpty()) return SalatStats(false, 0, null, emptyList(), 0, 0, 0)
    val ids = tasks.map { it.id }.toSet()
    val completeDays = dateCompletions.entries
        .filter { it.value && it.key.substringBefore(':') in ids }
        .groupingBy { it.key.substringAfter(':') }.eachCount()
        .filter { it.value >= tasks.size }
        .keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .sorted()

    val runs = mutableListOf<StreakRun>()
    for (d in completeDays) {
        val last = runs.lastOrNull()
        if (last != null && last.end.plusDays(1) == d) runs[runs.lastIndex] = last.copy(end = d) else runs += StreakRun(d, d)
    }
    val set = completeDays.toSet()
    var cursor = if (today in set) today else today.minusDays(1)
    var current = 0
    while (cursor in set) { current++; cursor = cursor.minusDays(1) }

    return SalatStats(
        enabled = true,
        current = current,
        longest = runs.maxWithOrNull(compareBy<StreakRun> { it.length }.thenBy { it.end }),
        runs = runs,
        perfectDays = completeDays.size,
        todayCount = prayedCount(today, tasks),
        total = tasks.size,
    )
}

/** Overall % of tasks done across all projects (same number as the Home header). */
/** Excludes the Salat list, whose prayers are ticked per day and would otherwise never count as done. */
fun AppData.overallProgress(): Triple<Int, Int, Int> {
    val counted = projects.filter { it.name != SALAT_PROJECT }
    val total = counted.sumOf { it.tasks.size }
    val done = counted.sumOf { p -> p.tasks.count { it.completed } }
    return Triple(done, total, if (total > 0) ((done * 100.0) / total).roundToInt() else 0)
}
