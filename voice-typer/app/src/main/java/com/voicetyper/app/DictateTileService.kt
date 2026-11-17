package com.voicetyper.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: pull down the notification shade, tap it, and dictation starts at once. */
class DictateTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun { launch() } else launch()
    }

    private fun launch() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_DICTATE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            launchLegacy(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun launchLegacy(intent: Intent) = startActivityAndCollapse(intent)
}
