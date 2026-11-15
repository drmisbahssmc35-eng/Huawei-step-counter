package com.progresspal.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.dailyTasksFor
import com.progresspal.app.data.overallProgress
import com.progresspal.app.data.salatStats
import com.progresspal.app.data.today
import com.progresspal.app.ui.components.dayClock

/** Home-screen widget: total % of tasks done, today's tasks, and the salat streak. */
class ProgressWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        AppStore.init(context.applicationContext)
        render(context, manager, ids)
        scheduleTicks(context)
    }

    override fun onEnabled(context: Context) = scheduleTicks(context)

    override fun onDisabled(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tickIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            AppStore.init(context.applicationContext)
            refresh(context)
        }
    }

    companion object {
        private const val ACTION_TICK = "com.progresspal.app.WIDGET_TICK"

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ProgressWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Redraws about every 15 minutes so the day-progress bar keeps moving (battery-friendly, no wake-ups). */
        private fun scheduleTicks(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                tickIntent(context),
            )
        }

        /** Re-draws every placed widget with the latest data. Safe to call often. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ProgressWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val data = AppStore.data.value
            val (_, _, overall) = data.overallProgress()
            val todayEntries = dailyTasksFor(data, today())
            val done = todayEntries.count { it.completed }
            val taskPct = if (todayEntries.isEmpty()) 0 else done * 100 / todayEntries.size
            val clock = dayClock(today(), System.currentTimeMillis())
            val salat = data.salatStats()
            val best = salat.longest?.length ?: 0
            val scale = maxOf(best, salat.current, 1)

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_progress).apply {
                setTextViewText(R.id.widget_percent, "$overall% of all tasks")
                setTextViewText(R.id.widget_day_value, "${clock.percent}% · ${clock.label.substringBefore("m:") + "m"} left")
                setProgressBar(R.id.widget_day_bar, 100, clock.percent, false)
                setTextViewText(R.id.widget_task_value, "$done/${todayEntries.size} · $taskPct%")
                setProgressBar(R.id.widget_task_bar, 100, taskPct, false)
                setTextViewText(R.id.widget_current_value, if (salat.enabled) "${salat.current} prayers" else "—")
                setProgressBar(R.id.widget_current_bar, scale, salat.current, false)
                setTextViewText(R.id.widget_best_value, if (salat.enabled) "$best prayers" else "—")
                setProgressBar(R.id.widget_best_bar, scale, best, false)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
