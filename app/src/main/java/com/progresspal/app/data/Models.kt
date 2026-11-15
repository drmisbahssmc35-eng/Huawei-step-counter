package com.progresspal.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class ProjectColor { GREEN, AMBER, CORAL, BLUE, PURPLE }

@Serializable
enum class Repeat(val label: String) {
    NONE("No repeat"),
    ONCE("Once"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Custom");

    /** daily / weekly / monthly / custom — tasks that recur and track completion per date. */
    val isRecurring: Boolean get() = this != NONE && this != ONCE
}

@Serializable
data class Task(
    val id: String,
    val title: String,
    val completed: Boolean = false,
    val createdAt: Long,
    val deadline: Long? = null,
    val repeat: Repeat = Repeat.NONE,
    val lastResetAt: Long? = null,
    /** "HH:mm" */
    val dueTime: String? = null,
    /** e.g. ["Sat", "Mon", "Wed"] */
    val repeatDays: List<String>? = null,
    /** Set on a one-time override ("edit/delete only for this date") of a recurring task. */
    val sourceTaskId: String? = null,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val color: ProjectColor,
    val tasks: List<Task> = emptyList(),
    val createdAt: Long,
    val target: String? = null,
    val deadline: Long? = null,
    val completedAt: Long? = null,
)

@Serializable
data class CompletionRecord(
    val taskId: String,
    /** yyyy-MM-dd */
    val date: String,
    val completed: Boolean,
    val createdAt: Long,
)

@Serializable
enum class ChallengeStatus { SCHEDULED, ACTIVE, COMPLETED, FAILED }

@Serializable
data class ChallengeTask(
    val id: String,
    val title: String,
    val completed: Boolean = false,
    val timerSeconds: Int,
    val startedAt: Long? = null,
    val sourceTaskId: String? = null,
)

@Serializable
data class Challenge(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: ChallengeStatus,
    val pausedAt: Long? = null,
    val totalPausedMs: Long = 0,
    val tasks: List<ChallengeTask> = emptyList(),
    val createdAt: Long,
)

@Serializable
data class AppData(
    val projects: List<Project> = emptyList(),
    /** key = "taskId:yyyy-MM-dd" */
    val dateCompletions: Map<String, Boolean> = emptyMap(),
    val completions: List<CompletionRecord> = emptyList(),
    val challenges: List<Challenge> = emptyList(),
)
