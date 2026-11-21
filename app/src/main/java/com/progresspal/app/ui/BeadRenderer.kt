package com.progresspal.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the salat streak as a string of glossy rainbow beads: one bead per prayer performed in a row,
 * dashed empty slots for today's prayers still to come. Long strings wrap row by row like a snake.
 */
object BeadRenderer {
    /** Rainbow order like a bead string: red → orange → yellow → green → teal → blue → indigo → violet → magenta. */
    private val HUES = floatArrayOf(0f, 22f, 50f, 95f, 140f, 170f, 200f, 225f, 250f, 275f, 300f, 325f)

    data class Spec(
        val done: Int,
        val pending: Int,
        /** Names under the last (todayDone + pending) beads, used only when the string is short. */
        val labels: List<String> = emptyList(),
    )

    /**
     * @param widthPx bitmap width
     * @param maxRows rows allowed before older beads are folded into a "+N" counter
     */
    fun render(spec: Spec, widthPx: Int, density: Float, maxRows: Int = 5): Bitmap {
        val total = max(1, spec.done + spec.pending)
        val pad = 6 * density
        val usable = widthPx - 2 * pad

        // Pick the biggest bead that fits within maxRows (big single row when short).
        var size = 44 * density
        val minSize = 12 * density
        fun perRow(s: Float) = max(1, floor((usable + s * 0.08f) / (s * 1.08f)).toInt())
        while (size > minSize && ceil(total / perRow(size).toDouble()) > maxRows) size -= density
        val cols = perRow(size)
        val capacity = cols * maxRows
        // Too many to show: keep the newest, fold the rest into "+N" (first slot).
        val hidden = if (total > capacity) total - (capacity - 1) else 0
        val shownDone = if (hidden > 0) spec.done - hidden else spec.done
        val slots = (if (hidden > 0) 1 else 0) + shownDone + spec.pending

        val showLabels = spec.labels.isNotEmpty() && total <= cols && size >= 28 * density
        val labelH = if (showLabels) 14 * density else 0f
        val step = size * 1.08f
        val rowH = size + 8 * density + labelH
        val rows = max(1, ceil(slots / cols.toDouble()).toInt())
        val heightPx = (rows * rowH + 2 * pad).toInt()
        val bmp = Bitmap.createBitmap(widthPx, max(heightPx, 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val inRow = min(cols, slots)
        val x0 = (widthPx - (inRow * step - size * 0.08f)) / 2f + size / 2f
        val pos = (0 until slots).map { i ->
            val r = i / cols
            val col = i % cols
            val cc = if (r % 2 == 1) cols - 1 - col else col
            floatArrayOf(x0 + cc * step, pad + r * rowH + size / 2f)
        }

        // Thread (snake through the rows).
        val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.6f * density; color = Color.argb(190, 217, 179, 107)
        }
        val path = Path().apply {
            moveTo(pos[0][0] - size / 2f - 4 * density, pos[0][1])
            for (i in 1 until pos.size) {
                val (px, py) = pos[i - 1][0] to pos[i - 1][1]
                val (x, y) = pos[i][0] to pos[i][1]
                if (py != y) {
                    val side = if (x > widthPx / 2f) 1 else -1
                    cubicTo(px + side * size * 1.1f, py, x + side * size * 1.1f, y, x, y)
                } else lineTo(x, y)
            }
            if (slots == 1 || pos.last()[1] == pos.first()[1]) lineTo(pos.last()[0] + size / 2f + 4 * density, pos.last()[1])
        }
        c.drawPath(path, thread)

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 255, 255, 255) }
        val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
        val bead = Paint(Paint.ANTI_ALIAS_FLAG)
        val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.6f * density; color = Color.rgb(138, 144, 166)
            pathEffect = DashPathEffect(floatArrayOf(3 * density, 3 * density), 0f)
        }
        val emptyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(12, 255, 255, 255) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 10 * density
        }

        val r = size / 2f
        val doneEnd = (if (hidden > 0) 1 else 0) + shownDone // slots before this index hold beads
        pos.forEachIndexed { i, (x, y) ->
            val beadIndex = i + if (hidden > 0) hidden - 1 else 0 // keeps colours stable as the string grows
            when {
                hidden > 0 && i == 0 -> {
                    c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 211, 110) })
                    text.color = Color.rgb(255, 211, 110); text.textSize = min(11 * density, r * 0.8f)
                    c.drawText("+$hidden", x, y + text.textSize / 3f, text)
                }
                i < doneEnd -> {
                    val h = HUES[beadIndex % HUES.size]
                    c.drawCircle(x, y + 1.5f * density, r, shadow)
                    bead.shader = RadialGradient(
                        x - r * 0.3f, y - r * 0.4f, r * 1.5f,
                        intArrayOf(hsl(h, 1f, .88f), hsl(h, .9f, .58f), hsl(h, .85f, .28f)),
                        floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(x, y, r, bead)
                    c.drawOval(RectF(x - r * .5f, y - r * .6f, x - r * .02f, y - r * .25f), shine)
                    c.drawCircle(x + r * .36f, y + r * .4f, r * .1f, glint)
                }
                else -> {
                    c.drawCircle(x, y, r - 1.5f * density, emptyFill)
                    c.drawCircle(x, y, r - 1.5f * density, empty)
                }
            }
        }

        if (showLabels) {
            val firstLabelled = slots - spec.labels.size
            text.textSize = 10 * density
            spec.labels.forEachIndexed { k, name ->
                val i = firstLabelled + k
                if (i < 0 || i >= pos.size) return@forEachIndexed
                val isDone = i < doneEnd
                text.color = if (isDone) Color.rgb(233, 236, 245) else Color.rgb(138, 144, 166)
                c.drawText(name, pos[i][0], pos[i][1] + r + 12 * density, text)
            }
        }
        return bmp
    }

    private fun hsl(h: Float, s: Float, l: Float): Int {
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color.rgb(((r + m) * 255).toInt(), ((g + m) * 255).toInt(), ((b + m) * 255).toInt())
    }
}

/** "📿 9 beads · best 25" with the count big, bold and gold (for widget text). */
fun beadStreakText(count: Int, best: Int, numberScale: Float = 2.6f): CharSequence {
    val num = count.toString()
    val sb = android.text.SpannableStringBuilder("📿 ")
    val s = sb.length
    sb.append(num)
    sb.setSpan(android.text.style.RelativeSizeSpan(numberScale), s, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.setSpan(android.text.style.ForegroundColorSpan(0xFFFFC94A.toInt()), s, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    val rest = " bead${if (count == 1) "" else "s"}  ·  best $best"
    val r = sb.length
    sb.append(rest)
    sb.setSpan(android.text.style.ForegroundColorSpan(0xFFE6D3A3.toInt()), r, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return sb
}
