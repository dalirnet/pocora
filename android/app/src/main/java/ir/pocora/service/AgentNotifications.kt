package ir.pocora.service

import android.app.Notification
import android.app.NotificationManager
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.agent.AgentStatus
import ir.pocora.model.Request
import ir.pocora.model.RequestKind
import ir.pocora.ui.MainActivity
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
            Triple(CHANNEL_REQUEST, R.string.channel_request, NotificationManager.IMPORTANCE_HIGH),
        ),
    ) {
    companion object {
        const val STATUS_ID = 1
        private const val ALLOWED_ID = 2
        private const val ENDING_ID = 3
        private const val QUOTA_ID = 4
        private const val REQUEST_ID_BASE = 100

        private const val CHANNEL_STATUS = "status"
        private const val CHANNEL_ALLOWED = "allowed"
        private const val CHANNEL_ENDING = "ending"
        private const val CHANNEL_QUOTA = "quota"
        private const val CHANNEL_REQUEST = "request"
        private const val PERCENT = 100
    }

    // The permanent card, the child's main view: internet or not, until when, and this half hour's data as a bar.
    fun statusNotification(status: AgentStatus): Notification {
        val text = text
        val format = format
        val builder =
            builder(
                CHANNEL_STATUS,
            ).setAutoCancel(false).setOngoing(true).setSilent(true).setShowWhen(false).setContentIntent(open())
        when {
            !status.hasRules -> {
                builder.setContentTitle(text.getString(R.string.waiting_for_rules))
            }

            status.allowed -> {
                builder.setContentTitle(
                    status.until?.let { text.getString(R.string.notify_internet_until, format.time(it)) }
                        ?: text.getString(R.string.you_have_internet),
                )
                status.bytesPerMark?.let { quota ->
                    builder
                        .setContentText(
                            text.getString(
                                R.string.data_this_half_hour,
                                format.megabytes(status.markBytes),
                                format.megabytes(quota),
                            ),
                        ).setProgress(PERCENT, (status.markBytes * PERCENT / quota).toInt().coerceIn(0, PERCENT), false)
                }
            }

            else -> {
                builder
                    .setContentTitle(
                        status.until?.let { text.getString(R.string.notify_no_internet_until, format.time(it)) }
                            ?: text.getString(R.string.no_internet_now),
                    ).setContentText(if (status.quotaReached) text.getString(R.string.data_used_up) else null)
            }
        }
        return builder.build()
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

    // A parent's request, with its two answers. Approving opens the app, which opens the store or the uninstall prompt.
    fun request(request: Request) {
        val text = text
        val remove = request.kind == RequestKind.REMOVE
        val notification =
            builder(CHANNEL_REQUEST)
                .setContentTitle(
                    text.getString(
                        if (remove) R.string.notify_parent_asks_remove else R.string.notify_parent_suggests,
                        request.appName,
                    ),
                ).setContentIntent(open(MainActivity.EXTRA_REQUEST to request.id))
                .addAction(
                    0,
                    text.getString(R.string.not_now),
                    open(
                        MainActivity.EXTRA_REQUEST to request.id,
                        MainActivity.EXTRA_APPROVE to false,
                    ),
                ).addAction(
                    0,
                    text.getString(if (remove) R.string.remove else R.string.install),
                    open(MainActivity.EXTRA_REQUEST to request.id, MainActivity.EXTRA_APPROVE to true),
                ).build()
        manager.notify(idOf(request), notification)
    }

    fun cancelRequest(request: Request) = manager.cancel(idOf(request))

    private fun idOf(request: Request) = REQUEST_ID_BASE + request.id.hashCode().mod(REQUEST_ID_BASE)
}
