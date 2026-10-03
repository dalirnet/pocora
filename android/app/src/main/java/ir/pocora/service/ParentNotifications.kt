package ir.pocora.service

import android.app.NotificationManager
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.model.Event
import ir.pocora.model.EventKind
import ir.pocora.model.Peer
import ir.pocora.preset.Holiday
import ir.pocora.preset.SchedulePreset
import ir.pocora.ui.Labels
import ir.pocora.ui.MainActivity

// The parent's notifications, made on this phone from what the children's phones send. Each kind is its own channel.
class ParentNotifications(
    app: PocoraApp,
) : Notifications(
        app,
        listOf(
            Triple(CHANNEL_HOME, R.string.notify_when_connects, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CHANNEL_ALERTS, R.string.alerts, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CHANNEL_SUGGESTIONS, R.string.schedule_suggestions, NotificationManager.IMPORTANCE_LOW),
        ),
    ) {
    companion object {
        private const val CHANNEL_HOME = "home"
        private const val CHANNEL_ALERTS = "alerts"
        private const val CHANNEL_SUGGESTIONS = "suggestions"

        // What a notification opens on the child's page.
        const val OPEN_ALERTS = "alerts"
        const val OPEN_SCHEDULE = "schedule"
        const val OPEN_COPY_FRIDAY = "copy_friday"
        const val OPEN_USAGE = "usage"
    }

    // Earlier versions kept a listener running in the background, with a channel of its own. Updated phones drop it.
    init {
        manager.deleteNotificationChannel("listener")
    }

    fun childHome(
        child: Peer,
        screenToday: Long,
        alerts: Int,
    ) = show(
        idOf(child, CHANNEL_HOME),
        CHANNEL_HOME,
        text.getString(R.string.child_connected_title, child.name),
        text.getString(
            R.string.child_connected_text,
            format.listed(
                format.screenTime(screenToday),
                alerts.takeIf { it > 0 }?.let { text.getString(R.string.new_alerts, format.number(it)) },
            ) ?: text.getString(R.string.not_used),
        ),
        openChild(child, OPEN_USAGE),
        text.getString(R.string.see),
    )

    fun alert(
        child: Peer,
        event: Event,
    ) = show(
        idOf(child, event.id),
        CHANNEL_ALERTS,
        if (event.kind == EventKind.VPN_OFF) {
            text.getString(R.string.child_turned_off_at, child.name, format.time(event.start))
        } else {
            text.getString(R.string.child_and_event, child.name, text.getString(Labels.event(event.kind)))
        },
        event.appName ?: event.app,
        openChild(child, OPEN_ALERTS),
        time = event.start,
    )

    fun missing(child: Peer) =
        show(
            idOf(child, MISSING),
            CHANNEL_ALERTS,
            text.getString(R.string.child_and_event, child.name, text.getString(R.string.event_missing)),
            tap = openChild(child, null),
        )

    // The child is no longer on this phone, so the tap opens the app's start.
    fun disconnected(child: Peer) =
        show(
            idOf(child, DISCONNECTED),
            CHANNEL_ALERTS,
            text.getString(R.string.child_disconnected, child.name),
            text.getString(R.string.child_disconnected_text),
        )

    fun season(
        child: Peer,
        preset: SchedulePreset,
        days: Long,
    ) = show(
        idOf(child, CHANNEL_SUGGESTIONS),
        CHANNEL_SUGGESTIONS,
        text.getString(R.string.starts_in_days, preset.name.of(language), format.number(days)),
        text.getString(R.string.for_child, child.name),
        openChild(child, OPEN_SCHEDULE),
        text.getString(R.string.see_schedule),
    )

    fun holiday(
        child: Peer,
        holiday: Holiday,
    ) = show(
        idOf(child, HOLIDAY),
        CHANNEL_SUGGESTIONS,
        text.getString(R.string.tomorrow_is_holiday, holiday.name.of(language)),
        text.getString(R.string.for_child, child.name),
        openChild(child, OPEN_COPY_FRIDAY),
        text.getString(R.string.use_fridays_hours),
    )

    fun cancelChild(child: Peer) =
        listOf(CHANNEL_HOME, CHANNEL_SUGGESTIONS, HOLIDAY, MISSING).forEach {
            manager.cancel(idOf(child, it))
        }

    private fun openChild(
        child: Peer,
        target: String?,
    ) = open(MainActivity.EXTRA_CHILD to child.id, MainActivity.EXTRA_OPEN to target)

    private fun idOf(
        child: Peer,
        key: String,
    ): Int = (child.id + key).hashCode() and Int.MAX_VALUE or 0x100
}

private const val HOLIDAY = "holiday"
private const val MISSING = "missing"
private const val DISCONNECTED = "disconnected"
