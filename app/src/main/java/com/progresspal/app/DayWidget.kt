package com.progresspal.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.Planner
import com.progresspal.app.data.SimpleTimeTracker
import com.progresspal.app.data.byDay
import com.progresspal.app.data.productivityEvents
import com.progresspal.app.data.salatBeads
import com.progresspal.app.data.salatStats
import com.progresspal.app.ui.BeadRenderer
import com.progresspal.app.ui.TimelineRenderer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Big home-screen widget (resize it up to the whole screen): today's timeline with the Actual and
 * Planner columns, today's productivity level and the salat streak beads. Tap opens Time Tracker.
 */
class DayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        initData(context)
        render(context, manager, ids)
        scheduleTicks(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        initData(context)
        render(context, manager, intArrayOf(id)) // resized → redraw at the new size
    }

    override fun onEnabled(context: Context) = scheduleTicks(context)

    override fun onDisabled(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tickIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CTRL) {
            initData(context)
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (id >= 0) {
                control(context, id, intent.getStringExtra(EXTRA_CMD) ?: "")
                render(context, AppWidgetManager.getInstance(context), intArrayOf(id))
            }
            return
        }
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_UPDATE &&
            intent.action != AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED
        ) {
            initData(context)
            refresh(context)
            if (intent.action == ACTION_TICK) SimpleTimeTracker.syncSoon(1) // pick up new records; redraws when done
        }
    }

    companion object {
        private const val ACTION_TICK = "com.progresspal.app.DAY_WIDGET_TICK"
        private const val ACTION_CTRL = "com.progresspal.app.DAY_WIDGET_CTRL"
        private const val EXTRA_CMD = "cmd"
        private const val DAY_MIN = 24 * 60
        private const val MIN_SPAN = 1 // minute — zoom in as far as one minute

        private fun prefs(context: Context) = context.getSharedPreferences("day_widget", Context.MODE_PRIVATE)

        /** Visible window for a widget: (start ms, span minutes). Default = the whole of today. */
        private fun window(context: Context, id: Int, now: Long): Pair<Long, Int> {
            val p = prefs(context)
            val span = p.getInt("span_$id", DAY_MIN).coerceIn(MIN_SPAN, DAY_MIN)
            val start = p.getLong("start_$id", -1L)
            val dayStart = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            return when {
                start >= 0 -> start to span
                span >= DAY_MIN -> dayStart to DAY_MIN
                else -> (now - span * 60_000L / 3) to span // follow "now", a third from the top
            }
        }

        /** Zoom (+ / −), move (▲ / ▼) or back to now. */
        private fun control(context: Context, id: Int, cmd: String) {
            val now = System.currentTimeMillis()
            val (start, span) = window(context, id, now)
            val center = start + span * 30_000L
            val e = prefs(context).edit()
            when (cmd) {
                "in" -> {
                    val ns = (span / 2).coerceAtLeast(MIN_SPAN)
                    e.putInt("span_$id", ns)
                    if (prefs(context).getLong("start_$id", -1L) >= 0) e.putLong("start_$id", center - ns * 30_000L)
                }
                "out" -> {
                    val ns = (span * 2).coerceAtMost(DAY_MIN)
                    e.putInt("span_$id", ns)
                    if (ns >= DAY_MIN) e.putLong("start_$id", -1L)
                    else if (prefs(context).getLong("start_$id", -1L) >= 0) e.putLong("start_$id", center - ns * 30_000L)
                }
                "up" -> e.putLong("start_$id", start - span * 30_000L)   // half a window earlier
                "down" -> e.putLong("start_$id", start + span * 30_000L) // half a window later
                "now" -> { e.putLong("start_$id", -1L) }
            }
            e.apply()
        }

        private fun ctrlIntent(context: Context, id: Int, cmd: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
            context, id * 10 + code,
            Intent(context, DayWidget::class.java).setAction(ACTION_CTRL)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_CMD, cmd),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun spanLabel(m: Int): String = when {
            m >= 60 && m % 60 == 0 -> "${m / 60}h"
            m >= 60 -> "${m / 60}h ${m % 60}m"
            else -> "${m}m"
        }

        private fun initData(context: Context) {
            val app = context.applicationContext
            AppStore.init(app)
            SimpleTimeTracker.init(app)
            Planner.init(app)
        }

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, DayWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Redraws about every 15 minutes so the now-line and running blocks move. */
        private fun scheduleTicks(context: Context) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                tickIntent(context),
            )
        }

        /** Re-draws every placed Day widget with the latest data. Safe to call often. */
        fun refresh(context: Context) {
            FocusWidget.refresh(context)
            ScoreWidget.refresh(context)
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, DayWidget::class.java))
                if (ids.isNotEmpty()) render(context, manager, ids)
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = AppStore.data.value
            val stt = SimpleTimeTracker.cache.value
            val planner = Planner.data.value
            val today = LocalDate.now()
            val level = productivityEvents(app, planner, stt).byDay()[today]
            val planScore = com.progresspal.app.data.planScoreOn(planner, stt, today)
            val plannedToday = com.progresspal.app.data.plannedMsOn(planner, today)
            val salat = app.salatStats()
            val beads = app.salatBeads()
            val dm = context.resources.displayMetrics
            val density = dm.density

            val open = PendingIntent.getActivity(
                context, 3,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN, "timetracker")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            ids.forEach { id ->
                val opts = manager.getAppWidgetOptions(id)
                val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 320
                val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 480
                val innerW = ((wDp - 20) * density).toInt().coerceIn((150 * density).toInt(), dm.widthPixels)

                val beadBmp = BeadRenderer.render(BeadRenderer.Spec(beads.done, beads.pending, beads.labels), innerW, density, maxRows = 2)
                // Whatever height is left under the header and beads goes to the timeline.
                val usedPx = ((20 + 50 + 20 + 6) * density).toInt() + beadBmp.height
                val tlH = ((hDp * density).toInt() - usedPx).coerceIn((120 * density).toInt(), dm.heightPixels)
                val now = System.currentTimeMillis()
                val (wStart, span) = window(context, id, now)
                val wEnd = wStart + span * 60_000L
                val tlH2 = (tlH - (34 * density).toInt()).coerceAtLeast((100 * density).toInt()) // room for the zoom buttons
                val tl = TimelineRenderer.render(innerW, tlH2, density, stt, planner, planner.enabled, now, wStart, wEnd)
                val z = java.time.ZoneId.systemDefault()
                val hm = DateTimeFormatter.ofPattern("h:mm a")
                val windowText = if (span >= DAY_MIN && prefs(context).getLong("start_$id", -1L) < 0) "Whole day · tap + to zoom"
                else "${spanLabel(span)} · " + hm.format(java.time.Instant.ofEpochMilli(wStart).atZone(z)).lowercase() +
                    " – " + hm.format(java.time.Instant.ofEpochMilli(wEnd).atZone(z)).lowercase() +
                    java.time.Instant.ofEpochMilli(wStart).atZone(z).toLocalDate().let { d -> if (d == today) "" else " · " + d.format(DateTimeFormatter.ofPattern("EEE d MMM")) }

                val streak = salat.current
                val views = RemoteViews(context.packageName, R.layout.widget_day).apply {
                    setTextViewText(R.id.day_date, today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")))
                    setTextViewText(R.id.day_level, "⚡ ${level?.total ?: 0}")
                    setTextViewText(
                        R.id.day_plan,
                        "📋 Plan ${com.progresspal.app.data.hoursText(plannedToday)} · ${(plannedToday * 100 / (24 * 3_600_000L))}%\n" +
                            "🎯 Implement " + (planScore.percent?.let { "$it%" } ?: "–"),
                    )
                    setTextViewText(
                        R.id.day_breakdown,
                        "🕌 ${level?.salat ?: 0} salat · ✓ ${level?.tasks ?: 0} tasks" + if ((level?.plans ?: 0) > 0) " · ◆ ${level!!.plans} plans" else "",
                    )
                    setTextViewText(
                        R.id.day_streak,
                        if (!salat.enabled) "Salat streak: start it in the app"
                        else "📿 Salat streak $streak bead${if (streak == 1) "" else "s"} · best ${salat.longest?.length ?: 0}",
                    )
                    setImageViewBitmap(R.id.day_beads, beadBmp)
                    setImageViewBitmap(R.id.day_timeline, tl)
                    setOnClickPendingIntent(R.id.day_root, open)
                    setTextViewText(R.id.day_window, windowText)
                    setOnClickPendingIntent(R.id.day_zoom_in, ctrlIntent(context, id, "in", 1))
                    setOnClickPendingIntent(R.id.day_zoom_out, ctrlIntent(context, id, "out", 2))
                    setOnClickPendingIntent(R.id.day_up, ctrlIntent(context, id, "up", 3))
                    setOnClickPendingIntent(R.id.day_down, ctrlIntent(context, id, "down", 4))
                    setOnClickPendingIntent(R.id.day_now, ctrlIntent(context, id, "now", 5))
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
