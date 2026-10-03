package ir.pocora.service

import android.app.Notification
import android.app.NotificationManager
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.agent.AgentStatus
import ir.pocora.preset.PresetStore
import java.time.LocalDateTime

// The child's notifications. Each kind is its own channel, so it can be silenced alone in the phone's settings.
class AgentNotifications(
    app: PocoraApp,
) : Notifications(
        app,
        listOf(
            Triple(CHANNEL_STATUS, R.string.channel_status, NotificationManager.IMPORTANCE_LOW),
            Triple(CHANNEL_ALLOWED, R.string.channel_allowed, NotificationManager.IMPORTANCE_LOW),
            Triple(CHANNEL_ENDING, R.string.channel_ending, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CHANNEL_QUOTA, R.string.channel_quota, NotificationManager.IMPORTANCE_DEFAULT),
        ),
    ) {
    companion object {
        const val STATUS_ID = 1
        private const val ALLOWED_ID = 2
        private const val ENDING_ID = 3
        private const val QUOTA_ID = 4

        private const val CHANNEL_STATUS = "status"
        private const val CHANNEL_ALLOWED = "allowed"
        private const val CHANNEL_ENDING = "ending"
        private const val CHANNEL_QUOTA = "quota"
        private const val PERCENT = 100

        // The channel of the parent's requests, a feature since removed.
        private const val OLD_CHANNEL_REQUEST = "request"
    }

    private val presets = PresetStore.get(app)

    init {
        manager.deleteNotificationChannel(OLD_CHANNEL_REQUEST)
    }

    // What the permanent card says and shows, for both the plain notification and the app's own layout.
    // Folded: the sentence and the line most worth seeing now. Unfolded: the schedule's name, then the data and its bar.
    private class StatusLook(
        val title: String,
        val detail: String? = null,
        val subtitle: String? = detail,
        val data: StatusPicture.Data? = null,
    ) {
        // Folded, the line is orange when it is the news that the data ran out.
        val alert: Boolean
            get() = data?.alert == true && detail == data.text
    }

    private fun look(status: AgentStatus): StatusLook {
        val text = text
        val format = format
        val rules = status.rules ?: return StatusLook(text.getString(R.string.waiting_for_rules))
        if (status.paused) {
            return StatusLook(text.getString(R.string.pocora_paused), text.getString(R.string.paused_everything_open))
        }
        val scheduleName = presets.schedule(rules.schedule).name.of(language)
        // This half hour's data as a share, quicker to read than two numbers.
        val data =
            when {
                status.quotaReached -> {
                    StatusPicture.Data(text.getString(R.string.data_used_up), 1f, alert = true)
                }

                status.bytesPerMark != null -> {
                    val share = (status.markBytes.toFloat() / status.bytesPerMark).coerceIn(0f, 1f)
                    val percent = format.number((share * PERCENT).toInt())
                    StatusPicture.Data(text.getString(R.string.data_share_used, percent), share, alert = false)
                }

                else -> {
                    null
                }
            }
        return if (status.allowed) {
            StatusLook(
                status.until?.let { text.getString(R.string.you_have_internet_until, format.time(it)) }
                    ?: text.getString(R.string.you_have_internet),
                detail = data?.text ?: scheduleName,
                subtitle = scheduleName,
                data = data,
            )
        } else {
            // No internet, no half hour's data to follow, unless running out of it is the reason.
            val outOfData = data?.takeIf { status.quotaReached }
            StatusLook(
                status.until?.let { text.getString(R.string.notify_no_internet_until, format.time(it)) }
                    ?: text.getString(R.string.no_internet_now),
                detail = outOfData?.text ?: scheduleName,
                subtitle = scheduleName,
                data = outOfData,
            )
        }
    }

    // The permanent card, the child's main view, drawn as the status card on Home under Android's own header.
    // The plain title and text stay set, for the lock screen, watches and screen readers, which show those instead.
    fun statusNotification(status: AgentStatus): Notification {
        val look = look(status)
        return builder(CHANNEL_STATUS)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open())
            .setContentTitle(look.title)
            .setContentText(look.detail)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views(look, expanded = false))
            .setCustomBigContentView(views(look, expanded = true))
            .build()
    }

    // The layout runs right to left in Persian whatever the phone's own language, as the app does.
    private fun views(
        look: StatusLook,
        expanded: Boolean,
    ): RemoteViews {
        val rtl = text.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val picture = StatusPicture(text, rtl)
        val line = if (expanded) look.subtitle else look.detail
        val data = look.data?.takeIf { expanded }
        return RemoteViews(
            app.packageName,
            if (rtl) R.layout.notification_status_rtl else R.layout.notification_status_ltr,
        ).apply {
            setImageViewBitmap(R.id.title, picture.line(look.title, StatusPicture.Kind.TITLE))
            setContentDescription(R.id.title, look.title)
            setViewVisibility(R.id.text, if (line == null) View.GONE else View.VISIBLE)
            line?.let {
                val kind = if (!expanded && look.alert) StatusPicture.Kind.ALERT else StatusPicture.Kind.DETAIL
                setImageViewBitmap(R.id.text, picture.line(it, kind))
                setContentDescription(R.id.text, it)
            }
            setViewVisibility(R.id.data, if (data == null) View.GONE else View.VISIBLE)
            data?.let { setImageViewBitmap(R.id.data, picture.data(it)) }
        }
    }

    fun status(status: AgentStatus) = manager.notify(STATUS_ID, statusNotification(status))

    fun allowedNow(until: LocalDateTime?) =
        show(
            ALLOWED_ID,
            CHANNEL_ALLOWED,
            until?.let { text.getString(R.string.notify_internet_started, format.time(it)) }
                ?: text.getString(R.string.notify_internet_started_open),
        )

    fun endingSoon(minutes: Long) =
        show(ENDING_ID, CHANNEL_ENDING, text.getString(R.string.notify_ending, format.number(minutes)))

    fun quotaLeft(bytes: Long) =
        show(QUOTA_ID, CHANNEL_QUOTA, text.getString(R.string.notify_data_left, format.size(bytes)))

    fun quotaUsed() = show(QUOTA_ID, CHANNEL_QUOTA, text.getString(R.string.notify_data_used))

    // After disconnecting: nothing from the old pairing stays on screen.
    fun cancelAll() = manager.cancelAll()
}
