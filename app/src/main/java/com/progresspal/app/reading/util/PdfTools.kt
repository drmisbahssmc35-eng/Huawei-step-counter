package com.progresspal.app.reading.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI

object PdfTools {

    fun pageCount(file: File): Int = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }
    } catch (e: SecurityException) {
        error("This PDF is password-protected and can't be opened")
    } catch (e: Exception) {
        error("This file is not a valid PDF")
    }

    fun renderCover(pdf: File, out: File, width: Int = 360): Boolean = runCatching {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                r.openPage(0).use { page ->
                    val h = (page.height * (width.toFloat() / page.width)).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    bmp.recycle()
                }
            }
        }
        true
    }.getOrDefault(false)

    fun stampText(now: LocalDateTime = LocalDateTime.now()): String =
        "READ  " + now.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH))

    /**
     * Writes a date/time stamp into [file] on page [pageIndex].
     * [fx]/[fy] are the tap position as a fraction of the displayed page (0..1, top-left origin);
     * when null the stamp goes in the top-right corner.
     * The previous version is kept as `<file>.bak` so the stamp can be undone.
     */
    suspend fun stamp(file: File, pageIndex: Int, fx: Float?, fy: Float?, text: String = stampText()) =
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                val box = page.cropBox
                val rot = ((page.rotation % 360) + 360) % 360
                val w = box.width
                val h = box.height
                val dw = if (rot == 90 || rot == 270) h else w   // displayed width
                val dh = if (rot == 90 || rot == 270) w else h   // displayed height

                val font = PDType1Font.HELVETICA_BOLD
                val size = (dw / 40f).coerceIn(7f, 18f)
                val pad = size * 0.55f
                val textW = font.getStringWidth(text) / 1000f * size
                val boxW = textW + pad * 2
                val boxH = size + pad * 2
                val margin = size

                // top-left corner of the stamp, in displayed coordinates (origin top-left)
                val ax: Float
                val ay: Float
                if (fx == null || fy == null) {
                    ax = (dw - margin - boxW).coerceAtLeast(0f)
                    ay = margin
                } else {
                    ax = (fx * dw - boxW / 2).coerceIn(0f, (dw - boxW).coerceAtLeast(0f))
                    ay = (fy * dh - boxH / 2).coerceIn(0f, (dh - boxH).coerceAtLeast(0f))
                }
                val ux = ax / dw
                val uy = ay / dh
                val (px, py, angle) = when (rot) {
                    90 -> Triple(box.lowerLeftX + uy * w, box.lowerLeftY + ux * h, PI / 2)
                    180 -> Triple(box.lowerLeftX + (1 - ux) * w, box.lowerLeftY + uy * h, PI)
                    270 -> Triple(box.lowerLeftX + (1 - uy) * w, box.lowerLeftY + (1 - ux) * h, -PI / 2)
                    else -> Triple(box.lowerLeftX + ux * w, box.lowerLeftY + (1 - uy) * h, 0.0)
                }

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.saveGraphicsState()
                    // local frame: origin at stamp's top-left, +x = right, +y = up (as displayed)
                    cs.transform(Matrix.getRotateInstance(angle, px, py))
                    val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.92f }
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(1f, 0.97f, 0.80f)
                    cs.setStrokingColor(0.78f, 0.16f, 0.16f)
                    cs.setLineWidth((size / 10f).coerceAtLeast(0.8f))
                    cs.addRect(0f, -boxH, boxW, boxH)
                    cs.fillAndStroke()
                    cs.beginText()
                    cs.setNonStrokingColor(0.72f, 0.10f, 0.10f)
                    cs.setFont(font, size)
                    cs.newLineAtOffset(pad, -boxH + pad + size * 0.18f)
                    cs.showText(text)
                    cs.endText()
                    cs.restoreGraphicsState()
                }
                doc.save(tmp)
            }
            file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }

    /** Restores the copy saved before the last stamp. */
    suspend fun undoStamp(file: File): Boolean = withContext(Dispatchers.IO) {
        val bak = File(file.parentFile, file.name + ".bak")
        if (!bak.exists()) return@withContext false
        bak.copyTo(file, overwrite = true)
        bak.delete()
        true
    }
}

/** Thread-safe wrapper around [PdfRenderer] (which can only render one page at a time). */
class PdfDocument(file: File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val mutex = Mutex()
    private var closed = false
    val pageCount: Int = renderer.pageCount

    suspend fun aspectRatio(index: Int): Float = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (closed) return@withContext 0.707f
            renderer.openPage(index).use { it.width.toFloat() / it.height }
        }
    }

    suspend fun render(index: Int, targetWidth: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (closed || index !in 0 until pageCount) return@withContext null
            runCatching {
                renderer.openPage(index).use { page ->
                    val w = targetWidth.coerceIn(200, 2000)
                    val h = (page.height * (w.toFloat() / page.width)).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }.getOrNull()
        }
    }

    override fun close() {
        // Wait for any in-flight render before closing.
        kotlinx.coroutines.runBlocking {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    renderer.close()
                    pfd.close()
                }
            }
        }
    }
}
