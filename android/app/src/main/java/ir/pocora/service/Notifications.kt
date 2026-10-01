package ir.pocora.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.ui.Format
import ir.pocora.ui.MainActivity

// What both apps' notifications share: the channels, the app's icon and colour, the app's own language,
// and a tap that opens the app with a few extras.
abstract class Notifications(
    protected val app: PocoraApp,
    channels: List<Triple<String, Int, Int>>,
) {
    protected val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Notifications speak the app's language, which may have changed since the process started.
    protected val text: Context
        get() = app.localized

    protected val format: Format
        get() = Format(text)

    protected val language: String
        get() = app.configStore.language

    init {
        val text = text
        manager.createNotificationChannels(
            channels.map { (id, name, importance) ->
                NotificationChannel(id, text.getString(name), importance)
            },
        )
    }

    protected fun builder(channel: String): NotificationCompat.Builder =
        NotificationCompat
            .Builder(text, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(app, R.color.icon_end))
            .setAutoCancel(true)

    // A notification that comes and goes: a title, a line, and the tap, with one action button that does the same.
    protected fun show(
        id: Int,
        channel: String,
        title: String,
        body: String? = null,
        tap: PendingIntent = open(),
        action: String? = null,
        time: Long? = null,
    ) {
        val builder = builder(channel).setContentTitle(title).setContentText(body).setContentIntent(tap)
        // An alert keeps the time it happened, not the time it arrived.
        time?.let { builder.setWhen(it).setShowWhen(true) }
        action?.let { builder.addAction(0, it, tap) }
        manager.notify(id, builder.build())
    }

    // Opens the app. Each set of extras needs its own request code, or Android would reuse the first one's.
    protected fun open(vararg extras: Pair<String, Any?>): PendingIntent {
        val intent =
            Intent(
                app,
                MainActivity::class.java,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        for ((key, value) in extras) {
            when (value) {
                is String -> intent.putExtra(key, value)
                is Boolean -> intent.putExtra(key, value)
            }
        }
        return PendingIntent.getActivity(
            app,
            extras.toList().hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
