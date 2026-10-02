package ir.pocora.parent

import ir.pocora.PocoraApp
import ir.pocora.debug.FileLogger
import ir.pocora.model.Event
import ir.pocora.model.Peer
import ir.pocora.model.Rules
import ir.pocora.model.Seasons
import ir.pocora.model.Snapshot
import ir.pocora.model.alerts
import ir.pocora.preset.PresetStore
import ir.pocora.protocol.Applied
import ir.pocora.protocol.Message
import ir.pocora.protocol.Protocol
import ir.pocora.protocol.Read
import ir.pocora.protocol.SetRules
import ir.pocora.protocol.Sync
import ir.pocora.protocol.SyncAnswer
import ir.pocora.protocol.Unpair
import ir.pocora.service.NetworkWatch
import ir.pocora.service.ParentNotifications
import ir.pocora.transport.Connection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

// The parent's phone at work: takes in the children's syncs, keeps their snapshots and contact, raises alerts,
// and carries the parent's changes to a child's phone. Changing anything needs the child's phone to answer.
class Parent(
    private val app: PocoraApp,
) {
    companion object {
        private const val TAG = "Parent"
        private const val MISSING_DAYS = 7L
        private const val DAY_MILLISECONDS = 86_400_000L
    }

    val presets = PresetStore.get(app)
    val snapshots = SnapshotStore(app)
    val contacts = ContactLog(app)
    val alertMarks = AlertMarks(app)
    val notifications = ParentNotifications(app)

    // Goes up whenever anything a screen shows has changed.
    private val changeCount = MutableStateFlow(0L)
    val changes: StateFlow<Long> = changeCount

    @Volatile
    private var started = false

    // One thread, so a stop never overtakes the start before it.
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "pocora-parent") }
    private val network by lazy { NetworkWatch(app) { app.endpoint.reannounce() } }

    // The parent's phone takes the children's syncs only while the app is open, and checks the calendar on each open.
    fun start() {
        if (started) return
        started = true
        app.endpoint.onPaired = ::onChildConnection
        network.start()
        worker.execute {
            app.endpoint.start()
            try {
                checkCalendar()
            } catch (error: RuntimeException) {
                FileLogger.e(TAG, "Calendar check failed", error)
            }
        }
    }

    // The app was closed or left. The children's phones keep trying, and sync the next time it is open.
    fun stop() {
        if (!started) return
        started = false
        network.stop()
        worker.execute { app.endpoint.stop() }
    }

    fun changed() {
        changeCount.value++
    }

    // --- The child calling ---

    // Runs on the connection's thread. The answer carries this phone's time, which the child's clock follows.
    private fun onChildConnection(
        connection: Connection,
        child: Peer,
    ) {
        val message = connection.receive(Protocol.REQUEST_TIMEOUT_MILLISECONDS)
        // The child disconnected from its own phone, which is then ready to be paired again.
        if (message is Unpair) {
            connection.send(Applied())
            FileLogger.i(TAG, "${child.name} disconnected")
            drop(child)
            notifications.disconnected(child)
            return
        }
        val sync = message as? Sync
        if (sync == null || sync.snapshot.childId != child.id) {
            FileLogger.w(TAG, "Unexpected message from ${child.name}")
            return
        }
        connection.send(SyncAnswer(System.currentTimeMillis(), child.name))
        app.peerLink.remember(child.id, InetSocketAddress(connection.peerAddress, sync.port))
        if (child.port != sync.port) app.peerStore.save(child.copy(port = sync.port))
        received(child, sync.snapshot, notify = true)
    }

    // A snapshot, from a sync or an answer. The first sync after a gap is one "child is home" summary,
    // instead of every alert the gap held. Later alerts arrive one by one.
    @Synchronized
    private fun received(
        child: Peer,
        snapshot: Snapshot,
        notify: Boolean,
    ) {
        snapshots.write(snapshot)
        val now = System.currentTimeMillis()
        val home = contacts.ping(child.id, now)
        val alerts = alertsOf(snapshot)
        val marks = alertMarks.read(child.id)
        val fresh = alerts.filter { it.id !in marks.notified }
        if (notify) {
            if (home) {
                if (app.configStore.notifyHome) {
                    notifications.childHome(child, screenToday(snapshot), alerts.count { it.id !in marks.seen })
                }
            } else if (app.configStore.notifyAlerts) {
                fresh.forEach { notifications.alert(child, it) }
            }
        }
        alertMarks.notified(child.id, fresh.map { it.id }, alerts.map { it.id }.toSet())
        changed()
    }

    // --- What the screens read ---

    fun children(): List<Peer> = app.peerStore.all()

    fun child(id: String): Peer? = children().firstOrNull { it.id == id }

    fun isOnline(childId: String): Boolean = contacts.isOnline(childId, System.currentTimeMillis())

    fun alertsOf(snapshot: Snapshot): List<Event> = snapshot.events.alerts()

    fun unseenAlerts(childId: String): Int {
        val snapshot = snapshots.read(childId) ?: return 0
        val seen = alertMarks.read(childId).seen
        return alertsOf(snapshot).count { it.id !in seen }
    }

    fun markAlertsSeen(childId: String) {
        val snapshot = snapshots.read(childId) ?: return
        alertMarks.seen(childId, alertsOf(snapshot).map { it.id })
        changed()
    }

    fun screenToday(snapshot: Snapshot): Long {
        val today = LocalDate.now().toEpochDay()
        return snapshot.childUsage().filter { it.date == today }.sumOf { it.screenMilliseconds }
    }

    // --- The parent's actions. Blocking: call them off the main thread. ---

    // A fresh snapshot, or null when the child's phone cannot be reached.
    fun read(child: Peer): Snapshot? = answer(child, Read(System.currentTimeMillis()))

    // True once the child's phone has applied the rules.
    fun setRules(
        child: Peer,
        rules: Rules,
    ): Boolean {
        val now = System.currentTimeMillis()
        return answer(child, SetRules(rules.copy(changedAt = now), now)) != null
    }

    private fun answer(
        child: Peer,
        message: Message,
    ): Snapshot? {
        val applied = app.peerLink.call(child, message, Protocol.CHILD_PORT) as? Applied ?: return null
        val snapshot = applied.snapshot ?: return null
        received(child, snapshot, notify = false)
        return snapshot
    }

    // Forgets a child: its fingerprint and every file about it. The child's phone is told, if it can be reached.
    fun forget(child: Peer) {
        app.peerLink.call(child, Unpair(app.identity.id), Protocol.CHILD_PORT)
        drop(child)
    }

    private fun drop(child: Peer) {
        app.peerStore.remove(child.id)
        clearData(child)
        changed()
    }

    // Everything kept about a child but its pairing. Also cleared when a phone paired before pairs again, as after
    // it disconnected while this app was closed, so it starts fresh.
    fun clearData(child: Peer) {
        snapshots.delete(child.id)
        contacts.delete(child.id)
        alertMarks.delete(child.id)
        notifications.cancelChild(child)
    }

    // --- Each time the app opens: suggestions, holidays, and children gone missing ---

    fun checkCalendar() {
        val today = LocalDate.now()
        val seasons = Seasons(presets)
        val config = app.configStore
        for (child in children()) {
            val rules = snapshots.read(child.id)?.rules
            if (config.notifySuggestions && rules != null) {
                seasons.upcomingFor(rules, today)?.let { (ids, start) ->
                    if (config.once("season-${child.id}-$start")) {
                        notifications.season(
                            child,
                            presets.schedule(ids.first()),
                            ChronoUnit.DAYS.between(today, start),
                        )
                    }
                }
                seasons.holidayTomorrow(rules, today)?.let { (date, holiday) ->
                    if (config.once("holiday-${child.id}-$date")) notifications.holiday(child, holiday)
                }
            }
            val lastSeen = contacts.lastSeen(child.id)
            if (lastSeen != null && System.currentTimeMillis() - lastSeen > MISSING_DAYS * DAY_MILLISECONDS) {
                if (config.notifyAlerts && config.once("missing-${child.id}-$lastSeen")) notifications.missing(child)
            }
        }
    }
}
