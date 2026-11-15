package com.progresspal.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.dailyTasksFor
import com.progresspal.app.data.overallProgress
import com.progresspal.app.data.salatStats
import com.progresspal.app.data.today

/** Home-screen widget: total % of tasks done, today's tasks, and the salat streak. */
class ProgressWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        AppStore.init(context.applicationContext)
        render(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            AppStore.init(context.applicationContext)
            refresh(context)
        }
    }

    companion object {
        /** Re-draws every placed widget with the latest data. Safe to call often. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ProgressWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val data = AppStore.data.value
            val (done, total, pct) = data.overallProgress()
            val today = dailyTasksFor(data, today()).filter { it.project.name != com.progresspal.app.data.SALAT_PROJECT }
            val salat = data.salatStats()

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_progress).apply {
                setTextViewText(R.id.widget_percent, "$pct%")
                setProgressBar(R.id.widget_bar, 100, pct, false)
                setTextViewText(
                    R.id.widget_detail,
                    "$done/$total tasks · today ${today.count { it.completed }}/${today.size}",
                )
                setTextViewText(R.id.widget_streak, "🔥 ${salat.current}")
                setTextViewText(
                    R.id.widget_salat,
                    if (salat.enabled) "Salat today ${salat.todayCount}/${salat.total} · best ${salat.longest?.length ?: 0} days"
                    else "Open the app and tap Start on the Salat card",
                )
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
