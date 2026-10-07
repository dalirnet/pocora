package ir.pocora.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.ui.Format
import ir.pocora.ui.MainActivity

// What both apps' notifications share: the channels, the app's icon and colour, the app's own language,
// the app's own look under Android's header, and a tap that opens the app with a few extras.
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

    // The start of every notification: the app's icon and colour, the plain words for the lock screen, watches and
    // screen readers, and under Android's header the app's own look, folded and unfolded.
    // Each one is a group of its own: from four up, Android would fold an app's notifications into one group and draw
    // the folded rows itself from the plain words, in its own font. Alone, each keeps the app's look.
    protected fun builder(
        id: Int,
        channel: String,
        title: String,
        line: String?,
        folded: RemoteViews,
        unfolded: RemoteViews = folded,
    ): NotificationCompat.Builder =
        NotificationCompat
            .Builder(text, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(app, R.color.icon_end))
            .setAutoCancel(true)
            .setGroup(id.toString())
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(folded)
            .setCustomBigContentView(unfolded)

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
        val builder = builder(id, channel, title, body, views(title, body)).setContentIntent(tap)
        // An alert keeps the time it happened, not the time it arrived.
        time?.let { builder.setWhen(it).setShowWhen(true) }
        action?.let { builder.addAction(0, it, tap) }
        manager.notify(id, builder.build())
    }

    // A notification's content in the app's own look: the sentence, a line under it, and the status card's data bar.
    // Android lays this out without the app's fonts, so each piece arrives as a picture, with the plain words beside it
    // for screen readers. The layout runs right to left in Persian whatever the phone's own language, as the app does.
    protected fun views(
        title: String,
        line: String?,
        lineKind: NotificationPicture.Kind = NotificationPicture.Kind.DETAIL,
        data: NotificationPicture.Data? = null,
    ): RemoteViews {
        val rtl = text.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val picture = NotificationPicture(text, rtl)
        return RemoteViews(
            app.packageName,
            if (rtl) R.layout.notification_rtl else R.layout.notification_ltr,
        ).apply {
            setImageViewBitmap(R.id.title, picture.line(title, NotificationPicture.Kind.TITLE))
            setContentDescription(R.id.title, title)
            setViewVisibility(R.id.text, if (line == null) View.GONE else View.VISIBLE)
            line?.let {
                setImageViewBitmap(R.id.text, picture.line(it, lineKind))
                setContentDescription(R.id.text, it)
            }
            setViewVisibility(R.id.data, if (data == null) View.GONE else View.VISIBLE)
            data?.let { setImageViewBitmap(R.id.data, picture.data(it)) }
        }
    }

    // Opens one of Android's own screens, such as the Wi-Fi panel.
    protected fun openScreen(intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            app,
            intent.action.hashCode(),
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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
