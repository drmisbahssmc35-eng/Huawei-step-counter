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
import com.progresspal.app.data.dailyTasksFor
import com.progresspal.app.data.hoursText
import com.progresspal.app.data.missedSinceLastSalat
import com.progresspal.app.data.planScoreOn
import com.progresspal.app.data.plannedMsOn
import com.progresspal.app.data.productivityEvents
import com.progresspal.app.data.salatBeads
import com.progresspal.app.data.salatStats
import com.progresspal.app.data.today
import com.progresspal.app.ui.BeadRenderer
import com.progresspal.app.ui.components.dayClock

/**
 * "Progress Pal · Score": productivity in big bright numbers (vs yesterday and the last 7 days),
 * Plan and Implement score bars, the salat bead streak with missed prayers since the last one,
 * day and task progress, and a live timer of the running activity.
 * Timer → Simple Time Tracker; anywhere else → Progress Pal's timeline.
 */
class ScoreWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        initData(context)
        render(context, manager, ids)
        scheduleTicks(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        initData(context)
        render(context, manager, intArrayOf(id))
    }

    override fun onEnabled(context: Context) = scheduleTicks(context)

    override fun onDisabled(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tickIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            initData(context)
            refresh(context)
            SimpleTimeTracker.syncSoon(1)
        }
    }

    companion object {
        private const val ACTION_TICK = "com.progresspal.app.SCORE_WIDGET_TICK"

        private fun initData(context: Context) {
            val app = context.applicationContext
            AppStore.init(app); SimpleTimeTracker.init(app); Planner.init(app)
        }

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 4, Intent(context, ScoreWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** About every 15 minutes: day bar, missed-prayer count and scores move on. */
        private fun scheduleTicks(context: Context) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                tickIntent(context),
            )
        }

        fun refresh(context: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(context)
                val ids = m.getAppWidgetIds(ComponentName(context, ScoreWidget::class.java))
                if (ids.isNotEmpty()) render(context, m, ids)
            }
        }

        private fun signed(n: Int) = if (n > 0) "▲ +$n" else if (n < 0) "▼ $n" else "= 0"
        private fun signColor(n: Int) = if (n > 0) 0xFF00E676.toInt() else if (n < 0) 0xFFFF5A7A.toInt() else 0xFFB8C0CF.toInt()

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = AppStore.data.value
            val stt = SimpleTimeTracker.cache.value
            val planner = Planner.data.value
            val today = today()
            val now = System.currentTimeMillis()
            val density = context.resources.displayMetrics.density

            // Productivity: today, vs yesterday, vs the average of the 7 days before today.
            val daily = productivityEvents(app, planner, stt, now).byDay()
            fun lv(d: java.time.LocalDate) = daily[d]?.total ?: 0
            val todayL = lv(today)
            val vsDay = todayL - lv(today.minusDays(1))
            val weekAvg = Math.round((1..7).sumOf { lv(today.minusDays(it.toLong())) } / 7.0).toInt()
            val vsWeek = todayL - weekAvg

            val plannedMs = plannedMsOn(planner, today)
            val planPct = ((plannedMs * 100) / (24 * 3_600_000L)).toInt()
            val impl = planScoreOn(planner, stt, today, now).percent

            val salat = app.salatStats()
            val beads = app.salatBeads()
            val missed = app.missedSinceLastSalat()

            val clock = dayClock(today, now)
            val tasks = dailyTasksFor(app, today)
            val done = tasks.count { it.completed }
            val taskPct = if (tasks.isEmpty()) 0 else done * 100 / tasks.size

            val running = stt.running.firstOrNull()
            val runAct = running?.let { r -> stt.activities.firstOrNull { it.id == r.id } }

            val openTimeline = PendingIntent.getActivity(
                context, 40,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "timetracker")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val sttLaunch = context.packageManager.getLaunchIntentForPackage(SimpleTimeTracker.PKG)
            val openStt = sttLaunch?.let {
                PendingIntent.getActivity(context, 41, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            } ?: openTimeline

            ids.forEach { id ->
                val wDp = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 300
                val beadW = ((wDp - 24) * density).toInt().coerceAtLeast((160 * density).toInt())
                // Big beads: at most 2 rows.
                val beadBmp = BeadRenderer.render(BeadRenderer.Spec(beads.done, beads.pending, beads.labels), beadW, density, maxRows = 2)

                val views = RemoteViews(context.packageName, R.layout.widget_score).apply {
                    setTextViewText(R.id.score_level, "$todayL")
                    setTextViewText(R.id.score_vs_day, signed(vsDay)); setTextColor(R.id.score_vs_day, signColor(vsDay))
                    setTextViewText(R.id.score_vs_week, signed(vsWeek)); setTextColor(R.id.score_vs_week, signColor(vsWeek))

                    setTextViewText(R.id.score_plan_label, "📋 Plan score · ${hoursText(plannedMs)} of 24h")
                    setTextViewText(R.id.score_plan_value, "$planPct%")
                    setProgressBar(R.id.score_plan_bar, 100, planPct.coerceIn(0, 100), false)
                    setTextViewText(R.id.score_impl_label, "🎯 Implement score")
                    setTextViewText(R.id.score_impl_value, impl?.let { "$it%" } ?: "–")
                    setProgressBar(R.id.score_impl_bar, 100, impl ?: 0, false)

                    setTextViewText(
                        R.id.score_streak,
                        if (!salat.enabled) "📿 Salat streak — start it in the app"
                        else "📿 ${salat.current} bead${if (salat.current == 1) "" else "s"} · best ${salat.longest?.length ?: 0}",
                    )
                    setTextViewText(R.id.score_missed, if (missed == 0) "✓ none missed" else "⚠ $missed missed")
                    setInt(R.id.score_missed, "setBackgroundResource", if (missed == 0) R.drawable.widget_pill_plain else R.drawable.widget_pill_record)
                    setTextColor(R.id.score_missed, if (missed == 0) 0xFFD9DEE8.toInt() else 0xFF3D2200.toInt())
                    setImageViewBitmap(R.id.score_beads, beadBmp)

                    setTextViewText(R.id.score_day_label, "☀️ Day · ${clock.label}")
                    setTextViewText(R.id.score_day_value, "${clock.percent}%")
                    setProgressBar(R.id.score_day_bar, 100, clock.percent, false)
                    setTextViewText(R.id.score_task_label, "✅ Tasks today $done/${tasks.size}")
                    setTextViewText(R.id.score_task_value, "$taskPct%")
                    setProgressBar(R.id.score_task_bar, 100, taskPct, false)

                    if (running != null && runAct != null) {
                        setTextViewText(R.id.score_timer_label, "NOW TRACKING")
                        setTextViewText(R.id.score_timer_name, runAct.name)
                        // Ticks by itself every second, no widget updates needed.
                        setChronometer(R.id.score_chrono, SystemClock.elapsedRealtime() - (now - running.startedAt), null, true)
                        setViewVisibility(R.id.score_chrono, android.view.View.VISIBLE)
                    } else {
                        setTextViewText(R.id.score_timer_label, "NO TIMER RUNNING")
                        setTextViewText(R.id.score_timer_name, "Tap to open Simple Time Tracker")
                        setChronometer(R.id.score_chrono, SystemClock.elapsedRealtime(), null, false)
                        setViewVisibility(R.id.score_chrono, android.view.View.GONE)
                    }
                    setOnClickPendingIntent(R.id.score_timer, openStt)
                    setOnClickPendingIntent(R.id.score_root, openTimeline)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
