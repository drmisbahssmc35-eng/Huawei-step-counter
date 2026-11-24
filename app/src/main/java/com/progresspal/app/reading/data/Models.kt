package com.progresspal.app.reading.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class BookStatus(val label: String) {
    READING("Ongoing"),
    COMPLETED("Completed"),
    WISHLIST("Wishlist"),
}

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val filePath: String,
    val coverPath: String? = null,
    val totalPages: Int,
    /** 0-based page the reader was last on (used to resume). */
    val lastPage: Int = 0,
    /** Highest page reached (1-based count) – drives the progress percentage. */
    val pagesRead: Int = 0,
    val status: BookStatus = BookStatus.READING,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val completedAt: Long? = null,
    val stampCount: Int = 0,
) {
    val progress: Float
        get() = if (totalPages > 0) (pagesRead.toFloat() / totalPages).coerceIn(0f, 1f) else 0f
    val percent: Int get() = (progress * 100).toInt()
}

/** One row per (book, page, day): a page that was actually read on that day. */
@Entity(
    tableName = "page_visits",
    primaryKeys = ["bookId", "page", "day"],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class PageVisit(val bookId: Long, val page: Int, val day: Long)

data class DayCount(val day: Long, val pages: Int)

data class DayBookCount(val day: Long, val bookId: Long, val pages: Int)

class Converters {
    @TypeConverter
    fun fromStatus(s: BookStatus): String = s.name

    @TypeConverter
    fun toStatus(s: String): BookStatus =
        runCatching { BookStatus.valueOf(s) }.getOrDefault(BookStatus.READING)
}
