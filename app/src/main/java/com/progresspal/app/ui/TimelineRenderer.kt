package com.progresspal.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import com.progresspal.app.data.PlanState
import com.progresspal.app.data.PlannerData
import com.progresspal.app.data.SttCache
import com.progresspal.app.data.occurrencesOn
import com.progresspal.app.data.planState
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** Draws today's timeline (Actual on the left, Planner on the right) into a bitmap for the widget. */
object TimelineRenderer {
    private data class Item(val start: Long, val end: Long, val color: Int, val title: String, val right: String, val badge: String, val alpha: Int, val outlined: Boolean)

    /**
     * Draws the window [from, to) — the whole day by default, or a zoomed slice of it
     * (down to a minute) chosen with the widget's zoom buttons.
     */
    fun render(
        widthPx: Int, heightPx: Int, density: Float, stt: SttCache, planner: PlannerData, showPlanner: Boolean,
        now: Long = System.currentTimeMillis(),
        from: Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        to: Long = from + 24 * 3_600_000L,
    ): Bitmap {
        val w = max(1, widthPx); val h = max(1, heightPx)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val zone = ZoneId.systemDefault()
        // Days touched by the window (it may cross midnight).
        val days = generateSequence(java.time.Instant.ofEpochMilli(from).atZone(zone).toLocalDate()) { it.plusDays(1) }
            .takeWhile { !it.isAfter(java.time.Instant.ofEpochMilli(to - 1).atZone(zone).toLocalDate()) }.toList()

        val axis = 40 * density
        val pad = 6 * density
        val top = pad; val bottom = h - pad
        val pxPerMs = (bottom - top) / (to - from).toFloat()
        fun y(ms: Long) = top + (ms - from) * pxPerMs
        val areaL = 2 * density
        val areaR = w - axis
        val cols = if (showPlanner) 2 else 1
        val colW = (areaR - areaL) / cols

        // Grid + labels: pick a step so lines stay ~22dp apart (3h … 1min).
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF; strokeWidth = density * 0.8f }
        val strong = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF; strokeWidth = density }
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = 9.5f * density }
        val stepMin = listOf(1, 2, 5, 10, 15, 30, 60, 120, 180).firstOrNull { it * 60_000L * pxPerMs >= 22 * density } ?: 180
        val stepMs = stepMin * 60_000L
        val dayStart0 = days.first().atStartOfDay(zone).toInstant().toEpochMilli()
        var t = dayStart0 + ((from - dayStart0 + stepMs - 1) / stepMs) * stepMs
        val fmt = java.time.format.DateTimeFormatter.ofPattern(if (stepMin >= 60) "ha" else "h:mm")
        while (t <= to) {
            val yy = y(t)
            val onHour = java.time.Instant.ofEpochMilli(t).atZone(zone).minute == 0
            c.drawLine(areaL, yy, areaR, yy, if (onHour) strong else line)
            val txt = fmt.format(java.time.Instant.ofEpochMilli(t).atZone(zone)).lowercase()
            c.drawText(txt, areaR + 4 * density, min(max(yy + label.textSize / 3, top + label.textSize), bottom), label)
            t += stepMs
        }
        if (showPlanner) {
            val div = Paint().apply { color = 0x55FFFFFF; strokeWidth = density }
            c.drawLine(areaL + colW, top, areaL + colW, bottom, div)
        }

        // Actual: finished records + running timers.
        val acts = stt.activities.associateBy { it.id }
        val actual = mutableListOf<Item>()
        days.flatMap { stt.days[it.toString()].orEmpty() }.filter { it.endedAt > from && it.startedAt < to }.forEach { r ->
            val a = r.activityId?.let { acts[it] }
            actual += Item(max(r.startedAt, from), min(r.endedAt, to), (a?.color ?: r.activityColor ?: 0xFF888888).toInt(),
                a?.name ?: r.activityName ?: "?", dur(r.endedAt - r.startedAt), "", 255, false)
        }
        stt.running.forEach { r ->
            val a = acts[r.id] ?: return@forEach
            if (r.startedAt < to && now > from) actual += Item(max(r.startedAt, from), min(now, to), a.color.toInt(), a.name, "▶ " + dur(now - r.startedAt), "", 255, false)
        }
        drawColumn(c, actual, areaL, colW, ::y, density)

        // Planner.
        if (showPlanner) {
            val plans = days.flatMap { occurrencesOn(planner, it) }.filter { it.end > from && it.start < to }.map { p ->
                val (st, share) = planState(p, stt, now)
                Item(
                    max(p.start, from), min(p.end, to), (p.color ?: 0xFF5B8DEF).toInt(), p.title,
                    when (st) { PlanState.DONE -> "Done"; PlanState.UPCOMING -> dur(p.end - p.start); else -> "${(share * 100).toInt()}%" },
                    when (st) { PlanState.DONE -> "✓"; PlanState.MISSED -> "✗"; else -> "" },
                    if (st == PlanState.MISSED) 90 else 255,
                    st == PlanState.UPCOMING || st == PlanState.ACTIVE,
                )
            }
            drawColumn(c, plans, areaL + colW, colW, ::y, density)
            val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 9f * density; typeface = Typeface.DEFAULT_BOLD }
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88000000.toInt() }
            listOf("Actual", "Planner").forEachIndexed { i, t ->
                val x = areaL + colW * i + 4 * density
                c.drawRoundRect(RectF(x, top, x + head.measureText(t) + 8 * density, top + 13 * density), 4 * density, 4 * density, bg)
                c.drawText(t, x + 4 * density, top + 10 * density, head)
            }
        }

        // Now line.
        if (now in from until to) {
            val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B30.toInt(); strokeWidth = 2 * density }
            val yy = y(now)
            c.drawLine(areaL, yy, areaR, yy, red)
            c.drawCircle(areaL + 3 * density, yy, 3.5f * density, red)
        }
        return bmp
    }

    private fun drawColumn(c: Canvas, items: List<Item>, x0: Float, colW: Float, y: (Long) -> Float, density: Float) {
        // Side-by-side lanes for overlaps.
        val sorted = items.sortedBy { it.start }
        val laneEnds = mutableListOf<Long>()
        val lanes = sorted.map { b ->
            var lane = laneEnds.indexOfFirst { it <= b.start }
            if (lane < 0) { lane = laneEnds.size; laneEnds += b.end } else laneEnds[lane] = b.end
            lane
        }
        val n = max(1, laneEnds.size)
        val laneW = colW / n
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density }
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f * density; typeface = Typeface.DEFAULT_BOLD }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDDFFFFFF.toInt(); textSize = 9.5f * density }
        val r = 4 * density
        sorted.forEachIndexed { i, b ->
            val l = x0 + laneW * lanes[i] + density
            val rgt = l + laneW - 2 * density
            val t = y(b.start)
            val btm = max(y(b.end), t + 2 * density)
            val rect = RectF(l, t, rgt, btm)
            if (b.outlined) {
                fill.color = (b.color and 0x00FFFFFF) or 0x40000000
                c.drawRoundRect(rect, r, r, fill)
                stroke.color = b.color or 0xFF000000.toInt()
                stroke.pathEffect = DashPathEffect(floatArrayOf(4 * density, 3 * density), 0f)
                c.drawRoundRect(rect, r, r, stroke)
            } else {
                fill.color = (b.color and 0x00FFFFFF) or (b.alpha shl 24)
                c.drawRoundRect(rect, r, r, fill)
            }
            val hgt = btm - t
            if (hgt >= 13 * density) {
                val textW = rgt - l - 6 * density
                val right = b.right + if (b.badge.isNotEmpty()) " ${b.badge}" else ""
                val rightW = small.measureText(right)
                val name = TextUtils.ellipsize(b.title, title, max(0f, textW - rightW - 4 * density), TextUtils.TruncateAt.END).toString()
                title.alpha = if (b.alpha < 255) 140 else 255
                c.drawText(name, l + 3 * density, t + 11 * density, title)
                if (textW - title.measureText(name) > rightW) c.drawText(right, rgt - 3 * density - rightW, t + 11 * density, small)
            }
        }
    }

    private fun dur(ms: Long): String {
        val m = (ms / 60_000).coerceAtLeast(0)
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }
}
