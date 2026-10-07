package ir.pocora.agent

import ir.pocora.config.ConfigStore
import ir.pocora.model.EventKind
import ir.pocora.service.AgentNotifications

// The parent's phone called over Bluetooth, see Wake, and could not reach this one: the child is asked for Wi-Fi,
// with a notification that pops up and a line on Home, until the two phones are in touch again.
// Only Wi-Fi left off counts toward Limited. Another network may be a sync that failed for some other reason, which is
// no fault of the child's, so that one is only asked about.
class WifiRequest(
    private val configStore: ConfigStore,
    private val events: EventLog,
    private val notifications: AgentNotifications,
) {
    companion object {
        // Wi-Fi left off this long after the parent's phone asked turns into Limited.
        private const val LIMIT_MILLISECONDS = 5 * 60_000L
    }

    // On another network: kept only while the agent runs, as it counts toward nothing.
    @Volatile
    private var parentWifi = false

    val open: Boolean
        get() = configStore.wifiAskedAt != 0L || parentWifi

    // Each call from the parent's phone pops the notification up again, even one the child cleared.
    fun ask(
        now: Long,
        parentWifi: Boolean,
    ) {
        if (parentWifi) {
            this.parentWifi = true
        } else if (configStore.wifiAskedAt == 0L) {
            configStore.wifiAskedAt = now
        }
        notifications.askForWifi(parentWifi)
    }

    // Wi-Fi left off past the limit: the internet is as in a Limited mark until the phones are in touch again, and apps
    // set to always keep it. Kept in Events, so the parent sees it.
    fun keptOff(now: Long): Boolean {
        val asked = configStore.wifiAskedAt
        if (asked == 0L || now - asked < LIMIT_MILLISECONDS) return false
        events.open(EventKind.WIFI_KEPT_OFF, asked)
        return true
    }

    // In touch again. True when a request was open, so the agent works again without its limit.
    fun met(now: Long): Boolean {
        if (!open) return false
        forget()
        events.close(EventKind.WIFI_KEPT_OFF, now)
        return true
    }

    fun forget() {
        parentWifi = false
        configStore.wifiAskedAt = 0L
        notifications.cancelWifi()
    }
}
