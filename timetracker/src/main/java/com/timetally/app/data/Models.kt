package com.timetally.app.data

import kotlinx.serialization.Serializable

/** Something you spend time on: Work, Sleep, Reading... */
@Serializable
data class ActivityType(
    val id: Long,
    val name: String,
    val emoji: String,
    /** ARGB colour, e.g. 0xFF3D5AFE */
    val color: Long,
)

/** A timer that is running right now. */
@Serializable
data class Running(
    val typeId: Long,
    val start: Long,
    val comment: String = "",
)

/** A finished block of time. */
@Serializable
data class TimeRecord(
    val id: Long,
    val typeId: Long,
    val start: Long,
    val end: Long,
    val comment: String = "",
)

@Serializable
data class Settings(
    /** Allow several timers at once. Off = starting one stops the others. */
    val multitask: Boolean = true,
    val showUntracked: Boolean = true,
    val showNotification: Boolean = true,
    val weekStartsMonday: Boolean = true,
)

@Serializable
data class AppData(
    val types: List<ActivityType> = DefaultTypes,
    val running: List<Running> = emptyList(),
    val records: List<TimeRecord> = emptyList(),
    val settings: Settings = Settings(),
    val nextId: Long = 1000,
)

val Palette: List<Long> = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5,
    0xFF039BE5, 0xFF00ACC1, 0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33,
    0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E, 0xFF6D4C41, 0xFF757575,
    0xFF546E7A, 0xFF263238,
)

val EmojiChoices: List<String> = listOf(
    "💼", "💻", "📚", "✍️", "🧠", "📞", "📧", "🗂️",
    "😴", "🍽️", "☕", "🍳", "🛒", "🧹", "🧺", "🚿",
    "🏃", "🏋️", "🧘", "🚴", "⚽", "🚶", "🏊", "🎮",
    "📺", "🎵", "🎨", "📖", "🙏", "🕌", "👨‍👩‍👧", "💬",
    "🚗", "🚌", "✈️", "🏥", "💊", "🩺", "🐶", "🌱",
    "🛠️", "💰", "📱", "🎓", "🧪", "🎬", "🎸", "⭐",
)

val DefaultTypes: List<ActivityType> = listOf(
    ActivityType(1, "Work", "💼", 0xFF1E88E5),
    ActivityType(2, "Sleep", "😴", 0xFF5E35B1),
    ActivityType(3, "Eating", "🍽️", 0xFFFB8C00),
    ActivityType(4, "Exercise", "🏃", 0xFF43A047),
    ActivityType(5, "Reading", "📖", 0xFF00897B),
    ActivityType(6, "Commute", "🚗", 0xFF757575),
    ActivityType(7, "Chores", "🧹", 0xFF6D4C41),
    ActivityType(8, "Leisure", "🎮", 0xFFD81B60),
)
