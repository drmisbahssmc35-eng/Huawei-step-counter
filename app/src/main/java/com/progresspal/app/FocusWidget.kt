package com.progresspal.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.Planner
import com.progresspal.app.data.SimpleTimeTracker
import com.progresspal.app.data.activeProjects
import com.progresspal.app.data.dailyTasksFor
import com.progresspal.app.data.progress
import com.progresspal.app.data.today
import com.progresspal.app.ui.TimelineRenderer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * "Progress Pal · Focus" widget with three pages chosen by tabs (home-screen widgets can't be
 * swiped sideways): the timeline in eight 3-hour sections you scroll up/down (it opens on the
 * current one), today's Daily Tasks (tap to tick), and ongoing projects with progress bars.
 */
class FocusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        initData(context)
        ids.forEach { render(context, manager, it, scrollToNow = true) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        initData(context)
        render(context, manager, id, scrollToNow = true)
        manager.notifyAppWidgetViewDataChanged(id, R.id.focus_list)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        when (intent.action) {
            ACTION_TAB -> {
                initData(context)
                prefs(context).edit().putString("mode_$id", intent.getStringExtra(EXTRA_MODE)).apply()
                val m = AppWidgetManager.getInstance(context)
                render(context, m, id, scrollToNow = true)
                m.notifyAppWidgetViewDataChanged(id, R.id.focus_list)
            }
            ACTION_ITEM -> {
                initData(context)
                // Tap on a Daily Task: tick / untick it for today.
                val taskId = intent.getStringExtra(EXTRA_TASK) ?: return
                val entry = dailyTasksFor(AppStore.data.value, today()).firstOrNull { it.task.id == taskId } ?: return
                if (entry.isRecurring) AppStore.toggleDateTask(taskId, today()) else AppStore.toggleTask(entry.project.id, taskId)
            }
        }
    }

    companion object {
        private const val ACTION_TAB = "com.progresspal.app.FOCUS_TAB"
        private const val ACTION_ITEM = "com.progresspal.app.FOCUS_ITEM"
        private const val EXTRA_MODE = "mode"
        const val EXTRA_TASK = "task"

        fun prefs(context: Context) = context.getSharedPreferences("focus_widget", Context.MODE_PRIVATE)
        fun mode(context: Context, id: Int) = prefs(context).getString("mode_$id", "timeline") ?: "timeline"

        fun initData(context: Context) {
            val app = context.applicationContext
            AppStore.init(app); SimpleTimeTracker.init(app); Planner.init(app)
        }

        /** Redraws all Focus widgets (headers and lists) with the latest data. */
        fun refresh(context: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(context)
                val ids = m.getAppWidgetIds(ComponentName(context, FocusWidget::class.java))
                ids.forEach { render(context, m, it, scrollToNow = false) }
                if (ids.isNotEmpty()) m.notifyAppWidgetViewDataChanged(ids, R.id.focus_list)
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int, scrollToNow: Boolean) {
            val mode = mode(context, id)
            val density = context.resources.displayMetrics.density
            val opts = manager.getAppWidgetOptions(id)
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 300
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 300
            // One 3-hour section fills the list area (widget minus tabs and subtitle).
            prefs(context).edit()
                .putInt("w_$id", ((wDp - 16) * density).toInt())
                .putInt("h_$id", ((hDp - 72) * density).toInt().coerceAtLeast((120 * density).toInt()))
                .apply()

            val app = AppStore.data.value
            val subtitle = when (mode) {
                "tasks" -> {
                    val e = dailyTasksFor(app, today())
                    "Today · ${e.count { it.completed }}/${e.size} done · tap a task to tick it"
                }
                "projects" -> "${activeProjects(app.projects).size} ongoing projects"
                else -> "Scroll up/down for other 3-hour parts of today · 8 parts"
            }

            val svc = Intent(context, FocusWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .apply { data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME)) } // unique per widget
            val views = RemoteViews(context.packageName, R.layout.widget_focus).apply {
                setRemoteAdapter(R.id.focus_list, svc)
                setEmptyView(R.id.focus_list, R.id.focus_empty)
                setTextViewText(R.id.focus_subtitle, subtitle)
                listOf("timeline" to R.id.focus_tab_timeline, "tasks" to R.id.focus_tab_tasks, "projects" to R.id.focus_tab_projects)
                    .forEachIndexed { i, (m, v) ->
                        setInt(v, "setBackgroundResource", if (m == mode) R.drawable.widget_pill_record else R.drawable.widget_pill_plain)
                        setTextColor(v, if (m == mode) 0xFF3D2200.toInt() else 0xFFD9DEE8.toInt())
                        setOnClickPendingIntent(v, PendingIntent.getBroadcast(
                            context, id * 10 + i,
                            Intent(context, FocusWidget::class.java).setAction(ACTION_TAB)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_MODE, m),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ))
                    }
                setOnClickPendingIntent(R.id.focus_open, PendingIntent.getActivity(
                    context, id * 10 + 5,
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN, if (mode == "timeline") "timetracker" else if (mode == "tasks") "daily/${today()}" else "home")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ))
                // Taps on list items (task rows) come back here.
                setPendingIntentTemplate(R.id.focus_list, PendingIntent.getBroadcast(
                    context, id * 10 + 6,
                    Intent(context, FocusWidget::class.java).setAction(ACTION_ITEM).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ))
                if (scrollToNow && mode == "timeline") setScrollPosition(R.id.focus_list, LocalTime.now().hour / 3)
            }
            manager.updateAppWidget(id, views)
        }
    }
}

/** Supplies the list rows for one Focus widget. */
class FocusWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))

    private class Factory(val context: Context, val id: Int) : RemoteViewsService.RemoteViewsFactory {
        private var mode = "timeline"
        private var rows: List<RemoteViews> = emptyList()

        override fun onCreate() {}
        override fun onDestroy() {}
        override fun getCount() = rows.size
        override fun getViewAt(position: Int): RemoteViews = rows.getOrElse(position) { RemoteViews(context.packageName, R.layout.focus_item_segment) }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 3
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false

        override fun onDataSetChanged() {
            FocusWidget.initData(context)
            mode = FocusWidget.mode(context, id)
            rows = when (mode) {
                "tasks" -> taskRows()
                "projects" -> projectRows()
                else -> segmentRows()
            }
        }

        /** Today in eight 3-hour images (12–3am, 3–6am … 9pm–12am). */
        private fun segmentRows(): List<RemoteViews> {
            val p = FocusWidget.prefs(context)
            val density = context.resources.displayMetrics.density
            val w = p.getInt("w_$id", (300 * density).toInt())
            val h = p.getInt("h_$id", (220 * density).toInt())
            val stt = SimpleTimeTracker.cache.value
            val planner = Planner.data.value
            val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            return (0 until 8).map { i ->
                val from = dayStart + i * 3 * 3_600_000L
                val bmp = TimelineRenderer.render(w, h, density, stt, planner, planner.enabled, now, from, from + 3 * 3_600_000L)
                RemoteViews(context.packageName, R.layout.focus_item_segment).apply { setImageViewBitmap(R.id.focus_segment, bmp) }
            }
        }

        private fun taskRows(): List<RemoteViews> {
            val stt = SimpleTimeTracker.cache.value
            val now = System.currentTimeMillis()
            val entries = com.progresspal.app.data.DailyOrder.sort(dailyTasksFor(AppStore.data.value, today())) { it.task.id }
            return entries.map { e ->
                val goal = SimpleTimeTracker.goalFor(e.task.id, stt)
                val act = goal?.let { g -> stt.activities.firstOrNull { it.id == g.activityId } }
                RemoteViews(context.packageName, R.layout.focus_item_task).apply {
                    setTextViewText(R.id.task_title, e.task.title)
                    setTextViewText(R.id.task_check, if (e.completed) "✓" else "○")
                    val color = act?.color?.toInt() ?: 0xFF31C47F.toInt()
                    setInt(R.id.task_check, "setBackgroundColor", if (e.completed) color else 0x22FFFFFF)
                    if (goal != null && act != null) {
                        val ms = SimpleTimeTracker.trackedMs(goal.activityId, today(), stt, now)
                        val pct = if (e.completed) 100 else ((ms * 100) / (goal.minutes.coerceAtLeast(1) * 60_000L)).toInt().coerceIn(0, 100)
                        setTextViewText(R.id.task_sub, "${ms / 60_000}m / ${goal.minutes}m · $pct%" + if (stt.running.any { it.id == act.id }) " · ▶ running" else "")
                        setProgressBar(R.id.task_bar, 100, pct, false)
                        setViewVisibility(R.id.task_bar, android.view.View.VISIBLE)
                    } else {
                        setTextViewText(R.id.task_sub, listOfNotNull(e.task.dueTime?.let { "⏰ $it" }, e.project.name).joinToString(" · "))
                        setViewVisibility(R.id.task_bar, android.view.View.GONE)
                    }
                    setOnClickFillInIntent(R.id.task_row, Intent().putExtra(FocusWidget.EXTRA_TASK, e.task.id))
                }
            }
        }

        private fun projectRows(): List<RemoteViews> =
            activeProjects(AppStore.data.value.projects).map { p ->
                val pct = p.progress()
                val done = p.tasks.count { it.completed }
                RemoteViews(context.packageName, R.layout.focus_item_project).apply {
                    setTextViewText(R.id.proj_name, p.name)
                    setTextViewText(R.id.proj_pct, "$pct%")
                    setTextViewText(R.id.proj_sub, "$done/${p.tasks.size} tasks" + (p.target?.let { " · $it" } ?: ""))
                    setProgressBar(R.id.proj_bar, 100, pct, false)
                }
            }
    }
}
