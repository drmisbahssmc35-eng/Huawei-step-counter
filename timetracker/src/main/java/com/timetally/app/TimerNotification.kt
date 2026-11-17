package com.timetally.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.timetally.app.data.AppData
import com.timetally.app.data.Store

/** One ongoing notification per running timer, with a live clock and a Stop button. */
object TimerNotification {
    private const val CHANNEL = "timers"
    private const val BASE_ID = 7000

    fun refresh(context: Context, data: AppData) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val wanted = if (data.settings.showNotification) data.running else emptyList()
        val wantedIds = wanted.map { idFor(it.typeId) }.toSet()

        // Remove notifications for timers that have stopped.
        runCatching {
            nm.activeNotifications.forEach { if (it.id >= BASE_ID && it.id !in wantedIds) nm.cancel(it.id) }
        }
        if (wanted.isEmpty()) return

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Running timers", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        wanted.forEach { r ->
            val type = data.types.firstOrNull { it.id == r.typeId } ?: return@forEach
            val stop = PendingIntent.getBroadcast(
                context, idFor(r.typeId),
                Intent(context, StopReceiver::class.java).putExtra(StopReceiver.EXTRA_TYPE, r.typeId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("${type.emoji} ${type.name}")
                .setContentText(r.comment.ifBlank { "Running" })
                .setColor(type.color.toInt())
                .setWhen(r.start)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(open)
                .addAction(0, "Stop", stop)
                .build()
            runCatching { nm.notify(idFor(r.typeId), n) }
        }
    }

    private fun idFor(typeId: Long) = BASE_ID + (typeId % 100_000).toInt()
}

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getLongExtra(EXTRA_TYPE, -1)
        if (id >= 0) Store.stop(id)
    }

    companion object {
        const val EXTRA_TYPE = "type"
    }
}
