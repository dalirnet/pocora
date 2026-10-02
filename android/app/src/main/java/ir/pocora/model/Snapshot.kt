package ir.pocora.model

import kotlinx.serialization.Serializable

// Everything the parent sees about one child, built on the child's phone at each sync and replaced whole on the parent's.
// It covers the last 7 days.
@Serializable
data class Snapshot(
    val childId: String,
    val takenAt: Long,
    val rules: Rules,
    val state: AgentState,
    val days: List<DayRecord> = emptyList(),
    val apps: List<InstalledApp> = emptyList(),
    val usage: List<AppDay> = emptyList(),
    val events: List<Event> = emptyList(),
) {
    companion object {
        const val DAYS_KEPT = 7
        private const val DAY_MILLISECONDS = 86_400_000L

        // The oldest time still kept, on either phone.
        fun keptSince(now: Long): Long = now - DAYS_KEPT * DAY_MILLISECONDS
    }

    // Use of the child's own apps. Android's services and the home screen are left out.
    fun childUsage(): List<AppDay> {
        val own = apps.filter { !it.system }.map { it.`package` }.toSet()
        return usage.filter { it.`package` in own }
    }
}

// The child's phone right now.
@Serializable
data class AgentState(
    val vpnOn: Boolean,
    val otherVpn: Boolean = false,
    val usageAccess: Boolean = false,
    val markBytes: Long = 0,
    val quotaReached: Boolean = false,
)

// One day on the child's phone as it really ran: each mark's label as applied, and the data each mark used.
// Allowed is 48 characters, '1' for Allowed and '0' for Limited.
@Serializable
data class DayRecord(
    val date: Long,
    val allowed: String,
    val bytes: List<Long>,
) {
    fun isAllowed(mark: Int): Boolean = allowed.getOrNull(mark) == '1'

    val totalBytes: Long
        get() = bytes.sum()
}

// One app on one day: time on screen and data used. The date is an epoch day.
@Serializable
data class AppDay(
    val `package`: String,
    val date: Long,
    val screenMilliseconds: Long = 0,
    val bytes: Long = 0,
)

// An app on the child's phone, with the group it was given there.
@Serializable
data class InstalledApp(
    val `package`: String,
    val name: String,
    val version: String,
    val group: String,
    val system: Boolean = false,
    val vpn: Boolean = false,
)

// Something that happened, with its real time. An event that lasts, such as Pocora being off, has an end once it is over.
// The app is a package name, for events about one app.
@Serializable
data class Event(
    val id: String,
    val kind: EventKind,
    val start: Long,
    val end: Long? = null,
    val app: String? = null,
    val appName: String? = null,
)

// What can happen on the child's phone. Alert marks the ones the parent is notified about.
// The alerts among a child's events, newest first: what both apps show.
fun List<Event>.alerts(): List<Event> = filter { it.kind.alert }.sortedByDescending { it.start }

enum class EventKind(
    val alert: Boolean,
) {
    POCORA_STOPPED(true),
    VPN_OFF(true),
    OTHER_VPN(true),
    VPN_APP_INSTALLED(true),
    DEVICE_ADMIN_OFF(true),
    WATCHED_APP(true),
    REBOOT(false),
    APP_INSTALLED(false),
    APP_REMOVED(false),

    // No longer recorded. Kept so events from an older child app can still be read.
    REQUEST_IGNORED(false),
}

// A stretch of time the child's phone kept syncing with the parent's. Between two of them is "no contact".
@Serializable
data class Contact(
    val start: Long,
    val end: Long,
)
