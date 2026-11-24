package com.progresspal.app.reading

import android.content.Context
import android.net.Uri
import com.progresspal.app.reading.data.AppDatabase
import com.progresspal.app.reading.data.BookRepository
import com.progresspal.app.reading.data.Settings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.MutableStateFlow

/** Reading Tracker (PDF books) inside Progress Pal: its database, settings and incoming PDFs. */
object ReadingGraph {
    lateinit var repository: BookRepository
        private set
    lateinit var settings: Settings
        private set

    /** A PDF shared / "opened with" Progress Pal, waiting to be added to the library. */
    val incoming = MutableStateFlow<Uri?>(null)

    fun init(context: Context) {
        if (::repository.isInitialized) return
        val app = context.applicationContext
        PDFBoxResourceLoader.init(app)
        repository = BookRepository(app, AppDatabase.build(app).bookDao())
        settings = Settings(app)
    }
}
