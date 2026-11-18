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
                val tl = TimelineRenderer.render(innerW, tlH, density, stt, planner, planner.enabled)

                val streak = salat.current
                val views = RemoteViews(context.packageName, R.layout.widget_day).apply {
                    setTextViewText(R.id.day_date, today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")))
                    setTextViewText(R.id.day_level, "⚡ ${level?.total ?: 0}")
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
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
