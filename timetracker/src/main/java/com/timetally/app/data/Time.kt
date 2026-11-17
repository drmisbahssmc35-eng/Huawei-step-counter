package com.timetally.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

val zone: ZoneId get() = ZoneId.systemDefault()

fun LocalDate.startMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** Milliseconds of [start, end) that fall inside [from, to). */
fun overlap(start: Long, end: Long, from: Long, to: Long): Long =
    (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0)

/** "1:05:09" or "05:09" — for live timers. */
fun fmtTimer(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** "2h 15m", "15m", "40s" — for totals. */
fun fmtDur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60
    return when {
        h > 0 -> "${h}h %02dm".format(m)
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
fun fmtClock(ms: Long): String = hm.format(Instant.ofEpochMilli(ms).atZone(zone))
fun fmtDay(d: LocalDate): String = dayFmt.format(d)
fun fmtDateTime(ms: Long): String =
    DateTimeFormatter.ofPattern("d MMM, HH:mm").format(Instant.ofEpochMilli(ms).atZone(zone))

enum class RangeKind(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All") }

data class Range(val from: Long, val to: Long, val title: String, val firstDay: LocalDate?)

/** The day/week/month/year that is [offset] steps away from the current one. */
fun rangeFor(kind: RangeKind, offset: Int, mondayFirst: Boolean, data: AppData): Range {
    val today = LocalDate.now()
    return when (kind) {
        RangeKind.DAY -> {
            val d = today.plusDays(offset.toLong())
            val title = when (offset) { 0 -> "Today"; -1 -> "Yesterday"; else -> fmtDay(d) }
            Range(d.startMillis(), d.plusDays(1).startMillis(), title, d)
        }
        RangeKind.WEEK -> {
            val first = if (mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
            val s = today.with(TemporalAdjusters.previousOrSame(first)).plusWeeks(offset.toLong())
            val e = s.plusDays(6)
            val f = DateTimeFormatter.ofPattern("d MMM")
            Range(s.startMillis(), s.plusDays(7).startMillis(), "${f.format(s)} – ${f.format(e)}", s)
        }
        RangeKind.MONTH -> {
            val s = today.withDayOfMonth(1).plusMonths(offset.toLong())
            Range(s.startMillis(), s.plusMonths(1).startMillis(), DateTimeFormatter.ofPattern("MMMM yyyy").format(s), s)
        }
        RangeKind.YEAR -> {
            val s = today.withDayOfYear(1).plusYears(offset.toLong())
            Range(s.startMillis(), s.plusYears(1).startMillis(), s.year.toString(), s)
        }
        RangeKind.ALL -> {
            val first = (data.records.map { it.start } + data.running.map { it.start }).minOrNull()
                ?: System.currentTimeMillis()
            Range(first, Long.MAX_VALUE, "All time", null)
        }
    }
}

/** Total time per activity inside [from, to), running timers included up to [now]. */
fun totalsIn(data: AppData, from: Long, to: Long, now: Long): Map<Long, Long> {
    val out = HashMap<Long, Long>()
    data.records.forEach { r ->
        val o = overlap(r.start, r.end, from, to)
        if (o > 0) out[r.typeId] = (out[r.typeId] ?: 0) + o
    }
    data.running.forEach { r ->
        val o = overlap(r.start, now, from, to)
        if (o > 0) out[r.typeId] = (out[r.typeId] ?: 0) + o
    }
    return out
}
