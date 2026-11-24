package com.progresspal.app.reading.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.progresspal.app.reading.util.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

class BookRepository(private val context: Context, private val dao: BookDao) {

    val books = dao.observeAll()
    val dailyCounts = dao.observeDailyCounts()
    val dayBookCounts = dao.observeDayBookCounts()
    fun book(id: Long) = dao.observe(id)
    fun dailyCountsForBook(id: Long) = dao.observeDailyCountsForBook(id)

    private val booksDir get() = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir get() = File(context.filesDir, "covers").apply { mkdirs() }

    fun displayName(uri: Uri): String {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
        }
        val raw = name ?: uri.lastPathSegment ?: "Untitled"
        return raw.substringAfterLast('/').removeSuffix(".pdf").removeSuffix(".PDF")
            .replace('_', ' ').trim().ifEmpty { "Untitled" }
    }

    /** Copies the PDF into private storage so it stays available and can be stamped. */
    suspend fun importBook(uri: Uri, title: String, author: String, status: BookStatus): Long =
        withContext(Dispatchers.IO) {
            val file = File(booksDir, "${UUID.randomUUID()}.pdf")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                } ?: error("Could not open the selected file")
                val pages = PdfTools.pageCount(file)
                val now = System.currentTimeMillis()
                val id = dao.insert(
                    Book(
                        title = title.ifBlank { "Untitled" },
                        author = author,
                        filePath = file.absolutePath,
                        totalPages = pages,
                        status = status,
                        pagesRead = if (status == BookStatus.COMPLETED) pages else 0,
                        completedAt = if (status == BookStatus.COMPLETED) now else null,
                    )
                )
                val cover = File(coversDir, "$id.png")
                if (PdfTools.renderCover(file, cover)) {
                    dao.get(id)?.let { dao.update(it.copy(coverPath = cover.absolutePath)) }
                }
                id
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
        }

    suspend fun update(book: Book) = dao.update(book)

    suspend fun setStatus(book: Book, status: BookStatus) {
        val now = System.currentTimeMillis()
        dao.update(
            when (status) {
                BookStatus.COMPLETED -> book.copy(status = status, pagesRead = book.totalPages, completedAt = now)
                else -> book.copy(status = status, completedAt = null)
            }
        )
    }

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        dao.delete(book)
        File(book.filePath).delete()
        File(book.filePath + ".bak").delete()
        book.coverPath?.let { File(it).delete() }
    }

    suspend fun saveLastPage(bookId: Long, page: Int) =
        dao.updateLastPage(bookId, page, System.currentTimeMillis())

    /** Called once a page has been on screen long enough to count as read. */
    suspend fun markPageRead(bookId: Long, page: Int) {
        dao.insertVisit(PageVisit(bookId, page, LocalDate.now().toEpochDay()))
        val book = dao.get(bookId) ?: return
        val reached = maxOf(book.pagesRead, page + 1).coerceAtMost(book.totalPages)
        var updated = book.copy(pagesRead = reached, lastReadAt = System.currentTimeMillis())
        if (updated.status == BookStatus.WISHLIST) updated = updated.copy(status = BookStatus.READING)
        if (reached >= book.totalPages && book.status != BookStatus.COMPLETED) {
            updated = updated.copy(status = BookStatus.COMPLETED, completedAt = System.currentTimeMillis())
        }
        if (updated != book) dao.update(updated)
    }

    suspend fun startReading(bookId: Long) {
        val book = dao.get(bookId) ?: return
        if (book.status == BookStatus.WISHLIST) dao.update(book.copy(status = BookStatus.READING))
    }

    suspend fun incrementStamps(bookId: Long, delta: Int) {
        val book = dao.get(bookId) ?: return
        dao.update(book.copy(stampCount = (book.stampCount + delta).coerceAtLeast(0)))
    }
}
