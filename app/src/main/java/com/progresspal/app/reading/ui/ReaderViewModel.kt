package com.progresspal.app.reading.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.progresspal.app.reading.ReadingGraph
import com.progresspal.app.reading.data.Book
import com.progresspal.app.reading.util.PdfDocument
import com.progresspal.app.reading.util.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ReaderEvent {
    data class Message(val text: String) : ReaderEvent
    data class Stamped(val text: String) : ReaderEvent
}

class ReaderViewModel(app: Application, val bookId: Long) : AndroidViewModel(app) {
    private val repo = ReadingGraph.repository

    val book: StateFlow<Book?> = repo.book(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Re-created after each stamp so pages re-render with the new ink. */
    val document = MutableStateFlow<PdfDocument?>(null)
    val version = MutableStateFlow(0)
    val initialPage = MutableStateFlow<Int?>(null)
    val error = MutableStateFlow<String?>(null)
    val stamping = MutableStateFlow(false)

    private val _events = Channel<ReaderEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val b = book.filterNotNull().first()
            initialPage.value = b.lastPage.coerceIn(0, (b.totalPages - 1).coerceAtLeast(0))
            repo.startReading(bookId)
            openDocument(b)
        }
    }

    private suspend fun openDocument(b: Book) {
        val old = document.value
        val doc = withContext(Dispatchers.IO) {
            runCatching { PdfDocument(File(b.filePath)) }
                .onFailure { error.value = "Could not open this PDF (${it.message})" }
                .getOrNull()
        }
        document.value = doc
        version.value++
        old?.let { withContext(Dispatchers.IO) { it.close() } }
    }

    fun onPageShown(page: Int) = viewModelScope.launch { repo.saveLastPage(bookId, page) }

    fun onPageRead(page: Int) = viewModelScope.launch { repo.markPageRead(bookId, page) }

    /** One-tap stamp: [fx]/[fy] null = top-right corner of the page. */
    fun stamp(page: Int, fx: Float? = null, fy: Float? = null) {
        val b = book.value ?: return
        if (stamping.value) return
        stamping.value = true
        viewModelScope.launch {
            val text = PdfTools.stampText()
            runCatching { PdfTools.stamp(File(b.filePath), page, fx, fy, text) }
                .onSuccess {
                    repo.incrementStamps(bookId, 1)
                    openDocument(b)
                    _events.send(ReaderEvent.Stamped(text.removePrefix("READ").trim()))
                }
                .onFailure { _events.send(ReaderEvent.Message("Could not stamp this page: ${it.message}")) }
            stamping.value = false
        }
    }

    fun undoStamp() {
        val b = book.value ?: return
        viewModelScope.launch {
            if (PdfTools.undoStamp(File(b.filePath))) {
                repo.incrementStamps(bookId, -1)
                openDocument(b)
                _events.send(ReaderEvent.Message("Stamp removed"))
            }
        }
    }

    override fun onCleared() {
        document.value?.close()
    }
}
