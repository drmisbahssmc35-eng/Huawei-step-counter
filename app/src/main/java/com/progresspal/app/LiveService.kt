package com.progresspal.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.Planner
import com.progresspal.app.data.SimpleTimeTracker

/**
 * Keeps the home-screen widgets live: listens for Simple Time Tracker's "activity started /
 * stopped" events (sent when its "Send events" option is on) and re-syncs at once, and while the
 * screen is on redraws the widgets every minute (now-line, running timer, bars). Runs as a quiet
 * foreground service so Android / Huawei don't stop it; it pauses while the screen is off.
 */
class LiveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0
    private val tick = object : Runnable {
        override fun run() {
            // Every minute: redraw; every 5 minutes: also re-sync with Simple Time Tracker.
            if (ticks++ % 5 == 0) SimpleTimeTracker.syncSoon(1)
            refreshWidgets()
            handler.postDelayed(this, 60_000)
        }
    }

    private val events = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                EVENT_STARTED, EVENT_STOPPED -> {
                    SimpleTimeTracker.syncSoon(1) // its result redraws every widget
                    handler.postDelayed({ refreshWidgets() }, 2_500)
                }
                Intent.ACTION_SCREEN_ON -> { handler.removeCallbacks(tick); handler.post(tick) }
                Intent.ACTION_SCREEN_OFF -> handler.removeCallbacks(tick)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext
        AppStore.init(app); Planner.init(app); SimpleTimeTracker.init(app)
        startInForeground()
        ContextCompat.registerReceiver(
            this, events,
            IntentFilter().apply {
                addAction(EVENT_STARTED); addAction(EVENT_STOPPED)
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(events) }
        super.onDestroy()
    }

    private fun refreshWidgets() = runCatching { ProgressWidget.refresh(this) }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Live widgets", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false); setSound(null, null)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 80, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "timetracker")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = b.setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Progress Pal widgets are live")
            .setContentText("Updating with Simple Time Tracker")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }

    companion object {
        private const val CHANNEL = "live"
        private const val NOTIF_ID = 7800
        private const val EVENT_STARTED = "com.razeeman.util.simpletimetracker.EVENT_STARTED_ACTIVITY"
        private const val EVENT_STOPPED = "com.razeeman.util.simpletimetracker.EVENT_STOPPED_ACTIVITY"

        fun enabled(context: Context) = context.getSharedPreferences("live", Context.MODE_PRIVATE).getBoolean("on", true)

        fun setEnabled(context: Context, on: Boolean) {
            context.getSharedPreferences("live", Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
            if (on) start(context) else context.stopService(Intent(context, LiveService::class.java))
        }

        /** Starts the service if it is switched on (call from the app or after boot). */
        fun start(context: Context) {
            if (!enabled(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, LiveService::class.java)) }
        }
    }
}
