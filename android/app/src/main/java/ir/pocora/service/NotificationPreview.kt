package ir.pocora.service

import ir.pocora.PocoraApp
import ir.pocora.Role
import ir.pocora.agent.AgentStatus
import ir.pocora.model.DayPlan
import ir.pocora.model.Event
import ir.pocora.model.EventKind
import ir.pocora.model.Peer
import ir.pocora.model.Rules
import ir.pocora.preset.PresetStore
import java.time.LocalDateTime

// Every notification of this app with sample words, for a look at them on a debug build, with no pairing needed:
//   adb shell am start -n ir.pocora.parent/ir.pocora.ui.MainActivity --ez preview_notifications true
object NotificationPreview {
    const val EXTRA = "preview_notifications"

    private const val SCREEN_TODAY_MILLISECONDS = 2 * 60 * 60 * 1000L + 25 * 60 * 1000L
    private const val ALERTS = 3
    private const val MARK_BYTES = 60_000_000L
    private const val BYTES_PER_MARK = 100_000_000L
    private const val ENDING_MINUTES = 10L
    private const val DAYS_TO_SEASON = 3L

    fun show(app: PocoraApp) {
        if (Role.current == Role.CHILD) child(app) else parent(app)
    }

    private fun child(app: PocoraApp) {
        val notifications = AgentNotifications(app)
        val presets = PresetStore.get(app)
        val until = LocalDateTime.now().plusHours(2)
        notifications.status(
            AgentStatus(
                allowed = true,
                until = until,
                markBytes = MARK_BYTES,
                bytesPerMark = BYTES_PER_MARK,
                quotaReached = false,
                today = DayPlan(emptyList()),
                lastParentContact = 0,
                rules = Rules(presets.schedules.first().id, appsList = "", quota = ""),
            ),
        )
        notifications.allowedNow(until)
        notifications.endingSoon(ENDING_MINUTES)
        notifications.quotaLeft(BYTES_PER_MARK - MARK_BYTES)
    }

    private fun parent(app: PocoraApp) {
        val notifications = ParentNotifications(app)
        val presets = PresetStore.get(app)
        val child = Peer(id = "preview", fingerprint = "", name = "Sara", deviceName = "Pixel")
        val now = System.currentTimeMillis()
        notifications.childHome(child, SCREEN_TODAY_MILLISECONDS, ALERTS)
        notifications.alert(child, Event("preview-vpn", EventKind.VPN_OFF, now))
        notifications.alert(
            child,
            Event("preview-app", EventKind.WATCHED_APP, now, app = "com.instagram.android", appName = "Instagram"),
        )
        notifications.missing(child)
        notifications.disconnected(child)
        notifications.season(child, presets.schedules.first(), DAYS_TO_SEASON)
        notifications.holiday(child, presets.holidays.first())
    }
}
