package com.progresspal.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.Planner
import com.progresspal.app.data.SimpleTimeTracker
import com.progresspal.app.data.occurrencesOn
import com.progresspal.app.data.salatOn
import com.progresspal.app.data.salatSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Azan at Fajr: plays res/raw/azan.mp3 at the planned Fajr time (the Planner's Fajr plan, else the
 * Fajr task's time, else 5:00) and stops by itself once Fajr is started (its activity running in
 * Simple Time Tracker or the Fajr task ticked), or from the notification's Stop button.
 */
object AzanScheduler {
    private const val PREFS = "azan"

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        schedule(context)
    }

    /** Planned Fajr time on [date]. */
    fun fajrTime(date: LocalDate): LocalTime {
        val zone = ZoneId.systemDefault()
        occurrencesOn(Planner.data.value, date).filter { salatSlot(it.title) == "Fajr" }.minOfOrNull { it.start }
            ?.let { return java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
        AppStore.data.value.salatOn(date).filter { salatSlot(it.task.title) == "Fajr" }
            .mapNotNull { e -> e.task.dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } }
            .filterNot { it == LocalTime.NOON } // 12:00 is only the add form's default
            .minOrNull()?.let { return it }
        return LocalTime.of(5, 0)
    }

    fun nextFajr(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val today = now.toLocalDate().atTime(fajrTime(now.toLocalDate()))
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).let { it.atTime(fajrTime(it)) }
    }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 70, Intent(context, AzanReceiver::class.java).setAction(AzanReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** (Re)sets the alarm for the next Fajr. Safe to call often. */
    fun schedule(context: Context) {
        runCatching {
            val app = context.applicationContext
            AppStore.init(app); Planner.init(app); SimpleTimeTracker.init(app)
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!enabled(app)) { am.cancel(alarmIntent(app)); return }
            val at = nextFajr().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val show = PendingIntent.getActivity(
                app, 71, Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "timetracker"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (canExact) am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), alarmIntent(app))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(app))
        }
    }
}

/** Fires at Fajr (starts the Azan) and re-arms the alarm after reboot / update / time change. */
class AzanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                if (AzanScheduler.enabled(context)) {
                    val svc = Intent(context, AzanService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc) else context.startService(svc)
                }
                AzanScheduler.schedule(context) // tomorrow's
            }
            ACTION_STOP -> context.stopService(Intent(context, AzanService::class.java))
            else -> {
                AzanScheduler.schedule(context)
                LiveService.start(context) // after reboot / update: widgets live again
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.progresspal.app.AZAN_FIRE"
        const val ACTION_STOP = "com.progresspal.app.AZAN_STOP"
    }
}

/** Plays the Azan in the foreground; stops when Fajr is started, when it ends, or on Stop. */
class AzanService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val check = object : Runnable {
        override fun run() {
            if (fajrStarted()) { stopSelf(); return }
            SimpleTimeTracker.syncSoon(1) // pick up a Fajr timer started in Simple Time Tracker
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext
        AppStore.init(app); Planner.init(app); SimpleTimeTracker.init(app)
        startInForeground()
        if (player == null) {
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                    )
                    val afd = resources.openRawResourceFd(R.raw.azan)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    setOnCompletionListener { stopSelf() }
                    prepare()
                    start()
                }
            }.getOrNull()
            if (player == null) { stopSelf(); return START_NOT_STICKY }
            handler.postDelayed(check, 3_000)
        }
        return START_NOT_STICKY
    }

    /** Fajr counts as started when a Fajr activity runs in Simple Time Tracker or a Fajr task is ticked today. */
    private fun fajrStarted(): Boolean {
        val stt = SimpleTimeTracker.cache.value
        val running = stt.running.any { r -> stt.activities.firstOrNull { it.id == r.id }?.name?.let { salatSlot(it) } == "Fajr" }
        val ticked = AppStore.data.value.salatOn(LocalDate.now()).any { salatSlot(it.task.title) == "Fajr" && it.completed }
        return running || ticked
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Azan", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
            })
        }
        val stop = PendingIntent.getBroadcast(
            this, 72, Intent(this, AzanReceiver::class.java).setAction(AzanReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 73, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "timetracker")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🕌 Fajr — Azan")
            .setContentText("Start your Fajr task to stop the Azan")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        runCatching { player?.stop() }
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "azan"
        private const val NOTIF_ID = 7700
    }
}
