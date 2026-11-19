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
            Toast.makeText(this, "Tap again to open the timeline", Toast.LENGTH_SHORT).show()
        }
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object { const val WINDOW_MS = 700L }
}
