package com.progresspal.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible screen that receives taps on the Day widget: two taps within [WINDOW_MS] open
 * Progress Pal's Time Tracker timeline; a single tap only shows a hint.
 */
class WidgetTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A task row on the Focus widget: tick / untick it right away, no app opened.
        intent.getStringExtra(FocusWidget.EXTRA_TASK)?.let { taskId ->
            FocusWidget.initData(this)
            val today = com.progresspal.app.data.today()
            com.progresspal.app.data.dailyTasksFor(com.progresspal.app.data.AppStore.data.value, today)
                .firstOrNull { it.task.id == taskId }?.let { e ->
                    if (!e.completed) com.progresspal.app.ui.components.Sounds.done(this)
                    if (e.isRecurring) com.progresspal.app.data.AppStore.toggleDateTask(taskId, today)
                    else com.progresspal.app.data.AppStore.toggleTask(e.project.id, taskId)
                }
            finish()
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            return
        }
        val prefs = getSharedPreferences("widget_tap", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong("last", 0L)
        // Second tap that arrived after this screen had already closed (a slower double tap).
        if (now - last <= WINDOW_MS) { openApp(); return }
        prefs.edit().putLong("last", now).apply()

        // First tap: stay on screen, invisible, for a moment. A quick second tap lands here
        // (instead of being lost while the screen opens or closes) and opens the app at once.
        val catcher = android.view.View(this)
        catcher.setOnTouchListener { _, ev ->
            if (ev.action == android.view.MotionEvent.ACTION_DOWN) openApp()
            true
        }
        setContentView(catcher)
        catcher.postDelayed({ if (!isFinishing) close() }, CATCH_MS)
    }

    private fun openApp() {
        getSharedPreferences("widget_tap", Context.MODE_PRIVATE).edit().putLong("last", 0L).apply()
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, intent.getStringExtra(MainActivity.EXTRA_OPEN) ?: "timetracker")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        close()
    }

    private fun close() {
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object {
        /** A second tap this soon after the first, landing after the catcher closed, still counts. */
        const val WINDOW_MS = 900L
        /** How long the invisible catcher waits for a quick second tap. */
        const val CATCH_MS = 450L
    }
}
