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
        if (now - last <= WINDOW_MS) {
            prefs.edit().putLong("last", 0L).apply()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN, intent.getStringExtra(MainActivity.EXTRA_OPEN) ?: "timetracker")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } else {
            prefs.edit().putLong("last", now).apply()
            Toast.makeText(this, "Tap again to open", Toast.LENGTH_SHORT).show()
        }
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object { const val WINDOW_MS = 700L }
}
