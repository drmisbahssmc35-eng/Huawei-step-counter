package com.progresspal.app.reading.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.progresspal.app.reading.ReadingGraph
import com.progresspal.app.reading.data.Book
import com.progresspal.app.reading.data.BookStatus
import com.progresspal.app.reading.data.DayCount
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayStat(val date: LocalDate, val pages: Int)

/** One row of the date-wise log: total pages that date plus which books they came from. */
data class DayLog(val date: LocalDate, val pages: Int, val books: List<Pair<String, Int>>)

data class ReadingStats(
    val loaded: Boolean = false,
    val books: List<Book> = emptyList(),
    val dailyGoal: Int = 20,
    val today: Int = 0,
    val week: List<DayStat> = emptyList(),
    val month: List<DayStat> = emptyList(),
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val totalPagesLogged: Int = 0,
    val activeDays: Int = 0,
    val bestDay: DayStat? = null,
    val avgLast30: Float = 0f,
    val history: List<DayLog> = emptyList(),
) {
    val reading get() = books.filter { it.status == BookStatus.READING }
    val completed get() = books.filter { it.status == BookStatus.COMPLETED }
    val wishlist get() = books.filter { it.status == BookStatus.WISHLIST }
    private val tracked get() = books.filter { it.status != BookStatus.WISHLIST }
    val pagesRead get() = tracked.sumOf { it.pagesRead }
    val pagesTotal get() = tracked.sumOf { it.totalPages }
    val overallProgress get() = if (pagesTotal > 0) pagesRead.toFloat() / pagesTotal else 0f
    val weekTotal get() = week.sumOf { it.pages }
    val currentBook get() = reading.firstOrNull()
}

data class PendingImport(val uri: Uri, val suggestedTitle: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ReadingGraph.repository
    private val settings = ReadingGraph.settings

    val stats: StateFlow<ReadingStats> =
        combine(repo.books, repo.dayBookCounts, settings.dailyGoal) { books, dayBook, goal ->
            val counts = dayBook.groupBy { it.day }.map { (day, rows) -> DayCount(day, rows.sumOf { it.pages }) }
            val titles = books.associate { it.id to it.title }
            val history = dayBook.groupBy { it.day }.map { (day, rows) ->
                DayLog(
                    LocalDate.ofEpochDay(day),
                    rows.sumOf { it.pages },
                    rows.sortedByDescending { it.pages }.map { (titles[it.bookId] ?: "Deleted book") to it.pages },
                )
            }.sortedByDescending { it.date }
            buildStats(books, counts, goal).copy(history = history)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingStats())

    val pendingImport = MutableStateFlow<PendingImport?>(null)
    val importing = MutableStateFlow(false)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun onPdfPicked(uri: Uri) {
        pendingImport.value = PendingImport(uri, repo.displayName(uri))
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    fun confirmImport(title: String, author: String, status: BookStatus, onDone: (Long) -> Unit = {}) {
        val pending = pendingImport.value ?: return
        importing.value = true
        viewModelScope.launch {
            runCatching { repo.importBook(pending.uri, title.trim(), author.trim(), status) }
                .onSuccess {
                    _messages.send("\"${title.trim().ifBlank { "Book" }}\" added to ${status.label}")
                    pendingImport.value = null
                    onDone(it)
                }
                .onFailure { _messages.send(it.message ?: "Could not import this PDF") }
            importing.value = false
        }
    }

    fun setStatus(book: Book, status: BookStatus) = viewModelScope.launch {
        repo.setStatus(book, status)
        _messages.send("Moved to ${status.label}")
    }

    fun rename(book: Book, title: String, author: String) = viewModelScope.launch {
        repo.update(book.copy(title = title.trim().ifBlank { book.title }, author = author.trim()))
    }

    fun delete(book: Book) = viewModelScope.launch {
        repo.delete(book)
        _messages.send("Deleted \"${book.title}\"")
    }

    fun setDailyGoal(goal: Int) = settings.setDailyGoal(goal)

    private fun buildStats(books: List<Book>, counts: List<DayCount>, goal: Int): ReadingStats {
        val today = LocalDate.now()
        val byDay = counts.associate { it.day to it.pages }
        fun range(days: Int) = (days - 1 downTo 0).map {
            val d = today.minusDays(it.toLong())
            DayStat(d, byDay[d.toEpochDay()] ?: 0)
        }
        // current streak: consecutive days with reading, ending today (or yesterday if not read yet today)
        var streak = 0
        var cursor = if ((byDay[today.toEpochDay()] ?: 0) > 0) today else today.minusDays(1)
        while ((byDay[cursor.toEpochDay()] ?: 0) > 0) {
            streak++
            cursor = cursor.minusDays(1)
        }
        var best = 0
        var run = 0
        var prev: Long? = null
        counts.filter { it.pages > 0 }.sortedBy { it.day }.forEach {
            run = if (prev != null && it.day == prev!! + 1) run + 1 else 1
            best = maxOf(best, run)
            prev = it.day
        }
        val month = range(30)
        return ReadingStats(
            loaded = true,
            books = books,
            dailyGoal = goal,
            today = byDay[today.toEpochDay()] ?: 0,
            week = range(7),
            month = month,
            streak = streak,
            bestStreak = maxOf(best, streak),
            totalPagesLogged = counts.sumOf { it.pages },
            activeDays = counts.count { it.pages > 0 },
            bestDay = counts.maxByOrNull { it.pages }?.let { DayStat(LocalDate.ofEpochDay(it.day), it.pages) },
            avgLast30 = month.sumOf { it.pages } / 30f,
        )
    }
}
