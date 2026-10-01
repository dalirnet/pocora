package ir.pocora.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.ui.MainActivity

// A quick-settings tile with the state, Allowed or Limited, one swipe away. Tapping it opens the app.
class StatusTileService : TileService() {
    override fun onStartListening() {
        val app = application as PocoraApp
        val status = app.agent.status
        val text = app.localized
        val tile = qsTile ?: return
        tile.label =
            text.getString(
                when {
                    !status.hasRules -> R.string.app_name
                    status.allowed -> R.string.tile_internet
                    else -> R.string.tile_no_internet
                },
            )
        tile.state =
            if (status.hasRules && status.allowed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
