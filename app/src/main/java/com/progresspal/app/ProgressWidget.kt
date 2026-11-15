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
import com.progresspal.app.data.BeadTier
import com.progresspal.app.data.salatBeads
import com.progresspal.app.ui.BeadRenderer
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
            val todayEntries = dailyTasksFor(data, today())
            val done = todayEntries.count { it.completed }
            val taskPct = if (todayEntries.isEmpty()) 0 else done * 100 / todayEntries.size
            val clock = dayClock(today(), System.currentTimeMillis())
            val salat = data.salatStats()
            val beads = data.salatBeads()
            val best = salat.longest?.length ?: 0
            val streak = salat.current
            val tier = BeadTier.of(streak)
            val isRecord = streak > 0 && streak >= best
            val density = context.resources.displayMetrics.density

            val title = when {
                !salat.enabled -> "Start your bead string"
                streak == 0 -> "Start a new string 🌱"
                else -> "$streak bead${if (streak == 1) "" else "s"} strong 🔥"
            }
            val nextPending = beads.labels.takeLast(beads.pending).firstOrNull()
            val message = when {
                !salat.enabled -> "Open the app and tap Start on the Salat card"
                beads.pending > 0 && isRecord -> "Add the $nextPending bead → new record! 🏆"
                beads.pending > 0 && streak + beads.pending > best && best > 0 ->
                    "Pray today's ${beads.pending} to beat your record of $best 👑"
                best > streak -> "👑 ${best - streak} more bead${if (best - streak == 1) "" else "s"} to beat your record ($streak / $best)"
                tier.next != null -> "Next milestone ${tier.next!!.label.substringBefore(' ')} ${tier.next!!.min} beads · ${tier.next!!.min - streak} to go"
                else -> "Legendary chain — keep it unbroken 🌟"
            }

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ids.forEach { id ->
                // Draw the beads at the widget's real width (falls back to ~4 cells).
                val widthDp = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 320
                val widthPx = ((widthDp - 28) * density).toInt().coerceIn((160 * density).toInt(), (480 * density).toInt())
                val bmp = BeadRenderer.render(
                    BeadRenderer.Spec(beads.done, beads.pending, beads.labels), widthPx, density,
                )
                val views = RemoteViews(context.packageName, R.layout.widget_progress).apply {
                    setInt(R.id.widget_root, "setBackgroundResource", tierBackground(tier))
                    setTextViewText(R.id.widget_tier, "SALAT STREAK · ${tier.label}")
                    setTextViewText(R.id.widget_title, title)
                    setTextColor(R.id.widget_title, tierColor(tier))
                    setTextViewText(
                        R.id.widget_days,
                        if (beads.cleanDays > 0) "${beads.cleanDays} day${if (beads.cleanDays == 1) "" else "s"} without missing a salat" else "",
                    )
                    setTextViewText(R.id.widget_pill, if (isRecord) "NEW RECORD" else "BEST EVER")
                    setInt(R.id.widget_pill, "setBackgroundResource", if (isRecord) R.drawable.widget_pill_record else R.drawable.widget_pill_plain)
                    setTextColor(R.id.widget_pill, if (isRecord) 0xFF3D2200.toInt() else 0xFFD9DEE8.toInt())
                    setTextViewText(R.id.widget_best, "👑 $best")
                    setImageViewBitmap(R.id.widget_beads, bmp)
                    setTextViewText(R.id.widget_message, message)
                    setTextViewText(R.id.widget_task_value, "✅ Tasks today $done/${todayEntries.size}")
                    setProgressBar(R.id.widget_task_bar, 100, taskPct, false)
                    setTextViewText(R.id.widget_day_value, "☀️ Day ${clock.percent}% · ${clock.label.substringBefore("m:") + "m"} left")
                    setProgressBar(R.id.widget_day_bar, 100, clock.percent, false)
                    setOnClickPendingIntent(R.id.widget_root, open)
                }
                manager.updateAppWidget(id, views)
            }
        }

        private fun tierBackground(t: BeadTier) = when (t) {
            BeadTier.BRONZE -> R.drawable.widget_bg_bronze
            BeadTier.SILVER -> R.drawable.widget_bg_silver
            BeadTier.GOLD -> R.drawable.widget_bg_gold
            BeadTier.DIAMOND -> R.drawable.widget_bg_diamond
            BeadTier.LEGEND -> R.drawable.widget_bg_legend
        }

        private fun tierColor(t: BeadTier): Int = when (t) {
            BeadTier.BRONZE -> 0xFFF0B48A
            BeadTier.SILVER -> 0xFFE3E8F0
            BeadTier.GOLD -> 0xFFFFC94A
            BeadTier.DIAMOND -> 0xFF9FE3FF
            BeadTier.LEGEND -> 0xFFFFB3EC
        }.toInt()

    }
}
