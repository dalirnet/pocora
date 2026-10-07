package ir.pocora.service

import android.app.Notification
import android.app.NotificationManager
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.agent.AgentStatus
import ir.pocora.preset.PresetStore
import ir.pocora.transport.Radios
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
            Triple(CHANNEL_WIFI, R.string.channel_wifi, NotificationManager.IMPORTANCE_HIGH),
        ),
    ) {
    companion object {
        const val STATUS_ID = 1
        private const val ALLOWED_ID = 2
        private const val ENDING_ID = 3
        private const val QUOTA_ID = 4
        private const val WIFI_ID = 5

        private const val CHANNEL_STATUS = "status"
        private const val CHANNEL_ALLOWED = "allowed"
        private const val CHANNEL_ENDING = "ending"
        private const val CHANNEL_QUOTA = "quota"
        private const val CHANNEL_WIFI = "wifi"
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
        val data: NotificationPicture.Data? = null,
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
                    NotificationPicture.Data(text.getString(R.string.data_used_up), 1f, alert = true)
                }

                status.bytesPerMark != null -> {
                    val share = (status.markBytes.toFloat() / status.bytesPerMark).coerceIn(0f, 1f)
                    val percent = format.number((share * PERCENT).toInt())
                    NotificationPicture.Data(text.getString(R.string.data_share_used, percent), share, alert = false)
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
    fun statusNotification(status: AgentStatus): Notification {
        val look = look(status)
        return builder(STATUS_ID, CHANNEL_STATUS, look.title, look.detail, views(look, false), views(look, true))
            .setAutoCancel(false)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open())
            .build()
    }

    private fun views(
        look: StatusLook,
        expanded: Boolean,
    ): RemoteViews =
        views(
            look.title,
            if (expanded) look.subtitle else look.detail,
            if (!expanded && look.alert) NotificationPicture.Kind.ALERT else NotificationPicture.Kind.DETAIL,
            look.data?.takeIf { expanded },
        )

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

    // The parent's phone called and could not reach this one: it pops up over whatever is open, and a tap opens the
    // Wi-Fi panel there. On another network, it asks for the parent's Wi-Fi.
    fun askForWifi(parentWifi: Boolean) =
        show(
            WIFI_ID,
            CHANNEL_WIFI,
            text.getString(if (parentWifi) R.string.notify_join_parent_wifi else R.string.notify_turn_on_wifi),
            text.getString(R.string.notify_parent_waiting),
            openScreen(Radios.wifiIntent(app)),
        )

    fun cancelWifi() = manager.cancel(WIFI_ID)

    // Whether a request for Wi-Fi can be seen: Pocora's notifications on, and that channel not silenced.
    fun canAsk(): Boolean =
        NotificationManagerCompat.from(app).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_WIFI)?.importance != NotificationManager.IMPORTANCE_NONE

    // After disconnecting: nothing from the old pairing stays on screen.
    fun cancelAll() = manager.cancelAll()
}
