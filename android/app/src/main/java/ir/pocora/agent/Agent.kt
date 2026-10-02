package ir.pocora.agent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.Handler
import android.os.HandlerThread
import ir.pocora.PocoraApp
import ir.pocora.debug.FileLogger
import ir.pocora.model.AgentState
import ir.pocora.model.AppAccess
import ir.pocora.model.EventKind
import ir.pocora.model.Mark
import ir.pocora.model.Peer
import ir.pocora.model.Request
import ir.pocora.model.RequestKind
import ir.pocora.model.Rules
import ir.pocora.model.Schedule
import ir.pocora.model.Snapshot
import ir.pocora.model.Suggested
import ir.pocora.model.Week
import ir.pocora.preset.PresetStore
import ir.pocora.protocol.Applied
import ir.pocora.protocol.PairAnswer
import ir.pocora.protocol.Protocol
import ir.pocora.protocol.Read
import ir.pocora.protocol.SendRequest
import ir.pocora.protocol.SetRules
import ir.pocora.protocol.Sync
import ir.pocora.protocol.SyncAnswer
import ir.pocora.protocol.Unpair
import ir.pocora.service.AgentNotifications
import ir.pocora.service.AgentService
import ir.pocora.service.TunnelService
import ir.pocora.transport.Connection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// The child's phone at work. Once a minute, and at every mark boundary, it reads the clock and the rules,
// counts the data, decides which apps have internet, watches for events, and syncs to the parents it can reach.
// Everything runs on one thread, so nothing here needs a lock.
class Agent(
    private val app: PocoraApp,
) {
    companion object {
        private const val TAG = "Agent"

        // How long an answer to the parent waits for a change to be applied on the agent's thread.
        private const val APPLY_TIMEOUT_MILLISECONDS = 10_000L
        private const val TICK_MILLISECONDS = 60_000L
        private const val STOP_GAP_MILLISECONDS = 3 * 60_000L
        private const val WATCH_SESSION_MILLISECONDS = 30 * 60_000L
        private const val ENDING_NOTICE_MINUTES = 10L
        private const val QUOTA_WARNING_SHARE = 0.9
        private const val SNAPSHOT_CACHE_MILLISECONDS = 60_000L
        private const val VPN_DOWN_MILLISECONDS = 2 * 60_000L
    }

    private val presets = PresetStore.get(app)
    private val schedule = Schedule(presets)
    val clock = AgentClock(app, app.configStore)
    private val rulesStore = RulesStore(app)
    val events = EventLog(app)
    private val days = DaysLog(app)
    val requests = RequestStore(app)
    private val goodbyes = GoodbyeStore(app)
    val catalog = AppCatalog(app, presets)
    val usage = UsageReader(app)
    private val meter = TrafficMeter()
    val notifications = AgentNotifications(app)

    private val thread = HandlerThread("pocora-agent").apply { start() }
    private val handler = Handler(thread.looper)
    private val tickRunnable = Runnable { tick() }

    @Volatile
    var status: AgentStatus = AgentStatus.EMPTY
        private set

    // Goes up after every tick, so the screens redraw with the new status.
    private val updateCount = MutableStateFlow(0L)
    val updates: StateFlow<Long> = updateCount

    private var started = false
    private var lastSync = 0L
    private var lastWatchCheck = 0L
    private var lastPrune = 0L
    private var currentMark: Pair<LocalDate, Int>? = null
    private var quotaReached = false
    private var quotaWarned = false
    private var endingNotified: Long = 0
    private var wasAllowed: Boolean? = null
    private var vpnDownSince: Long? = null
    private var cachedSnapshot: Snapshot? = null

    // --- Life ---

    fun start() =
        handler.post {
            if (started) return@post
            started = true
            app.endpoint.onPaired = ::onParentConnection
            thread(name = "pocora-endpoint-start") { app.endpoint.start() }
            noteStopsAndReboots()
            tick()
        }

    // Before the first tick: was the agent stopped, or the phone restarted, since it last ran?
    private fun noteStopsAndReboots() {
        val now = clock.now()
        val boot = clock.bootCount()
        val alive = app.configStore.aliveTime
        if (app.configStore.bootTime != boot) {
            if (app.configStore.bootTime != 0L) events.add(EventKind.REBOOT, clock.bootTime())
            app.configStore.bootTime = boot
        } else if (alive != 0L && now - alive > STOP_GAP_MILLISECONDS) {
            events.add(EventKind.POCORA_STOPPED, alive, now)
        }
        app.configStore.aliveTime = now
    }

    fun refresh() = handler.post { tick() }

    // --- The tick ---

    private fun tick() {
        handler.removeCallbacks(tickRunnable)
        try {
            work()
        } catch (error: RuntimeException) {
            FileLogger.e(TAG, "Tick failed", error)
        }
        updateCount.value++
        // The next whole minute, so marks change on time.
        val now = System.currentTimeMillis()
        handler.postDelayed(tickRunnable, TICK_MILLISECONDS - now % TICK_MILLISECONDS + 50)
    }

    private fun work() {
        val nowTime = clock.now()
        val now = clock.localNow()
        val date = now.toLocalDate()
        val mark = schedule.markOf(now)
        app.configStore.aliveTime = nowTime
        val bytes = meter.sample()
        val rules = rulesStore.read()
        if (rules == null) {
            status = AgentStatus.EMPTY
            TunnelService.apply(app, null)
            return
        }

        if (currentMark != date to mark) {
            currentMark = date to mark
            quotaReached = false
            quotaWarned = false
        }
        val state = schedule.state(rules, now)
        days.record(date, mark, state.allowed && !quotaReached, bytes)
        val markBytes = days.markBytes(date, mark)
        val perMark = presets.quota(rules.quota).bytesPerMark
        if (perMark != null && state.allowed) {
            if (!quotaReached && markBytes >= perMark) {
                quotaReached = true
                notifications.quotaUsed()
            } else if (!quotaWarned && markBytes >= perMark * QUOTA_WARNING_SHARE) {
                quotaWarned = true
                notifications.quotaLeft(perMark - markBytes)
            }
        }

        val allowedNow = state.allowed && !quotaReached
        TunnelService.apply(app, blockedApps(rules, allowedNow, state.appsList))
        watchVpn(nowTime)
        watchApps(rules, nowTime)
        notifyChanges(allowedNow, state.until, now)

        status =
            AgentStatus(
                allowed = allowedNow,
                // A used-up quota holds the internet back until the next mark.
                until =
                    if (quotaReached) {
                        date.atStartOfDay().plusMinutes(
                            (mark + 1L) * Mark.DURATION_MINUTES,
                        )
                    } else {
                        state.until
                    },
                markBytes = markBytes,
                bytesPerMark = perMark,
                quotaReached = quotaReached,
                today = schedule.day(rules, date),
                lastParentContact = app.configStore.lastParentContact,
                childName = app.configStore.childName,
                requests = requests.all(),
                rules = rules,
            )
        notifications.status(status)

        if (nowTime - lastPrune > TICK_MILLISECONDS * 60) {
            lastPrune = nowTime
            events.prune(nowTime)
            rulesStore.write(rules.withoutPastChanges(Week.startOf(date).toEpochDay()))
        }
        if (nowTime - lastSync >= Protocol.SYNC_INTERVAL_MILLISECONDS - 1_000) syncNow()
    }

    // The packages that must not have internet now. In a Limited mark that is every app that could use it.
    private fun blockedApps(
        rules: Rules,
        allowed: Boolean,
        appsList: String,
    ): List<String> {
        val withInternet = catalog.withInternet().toSet()
        if (!allowed) return withInternet.sorted()
        return catalog
            .installed()
            .filter { it.`package` in withInternet && !AppAccess.hasInternet(presets, rules, it, appsList) }
            .map { it.`package` }
            .sorted()
    }

    // Pocora's VPN off while it should be on, and another VPN in its place, are episodes on the timeline.
    private fun watchVpn(now: Long) {
        val prepared = VpnService.prepare(app) == null
        val running = TunnelService.running
        // The VPN takes a moment to start or rebuild, so only a gap of two ticks or more counts as off.
        if (running || !prepared) {
            vpnDownSince = null
        } else if (vpnDownSince == null) {
            vpnDownSince = now
        }
        val down = vpnDownSince?.takeIf { now - it >= VPN_DOWN_MILLISECONDS }
        val changed =
            when {
                down != null -> events.open(EventKind.VPN_OFF, down)
                running -> events.close(EventKind.VPN_OFF, now)
                else -> false
            }
        val otherVpn = down != null && otherVpnActive()
        val otherChanged =
            if (otherVpn) {
                events.open(
                    EventKind.OTHER_VPN,
                    now,
                )
            } else {
                events.close(EventKind.OTHER_VPN, now)
            }
        if (changed || otherChanged) syncSoon()
    }

    private fun otherVpnActive(): Boolean {
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        return connectivity.allNetworks.any {
            connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ==
                true
        }
    }

    // A watched app opened is an alert, once per usage session.
    private fun watchApps(
        rules: Rules,
        now: Long,
    ) {
        // Android stamps app events with the phone's own clock, which may differ from the agent's,
        // so the window is asked for in phone time and each event moved onto the agent's clock.
        val phoneNow = System.currentTimeMillis()
        val since = if (lastWatchCheck == 0L) phoneNow - TICK_MILLISECONDS else lastWatchCheck
        lastWatchCheck = phoneNow
        if (rules.watch.isEmpty()) return
        var added = false
        for ((packageName, phoneTime) in usage.opened(since, phoneNow)) {
            if (packageName !in rules.watch) continue
            val time = phoneTime + (now - phoneNow)
            val last = events.lastOf(EventKind.WATCHED_APP, packageName)
            if (last != null && time - last.start < WATCH_SESSION_MILLISECONDS) continue
            events.add(EventKind.WATCHED_APP, time, app = packageName, appName = catalog.find(packageName)?.name)
            added = true
        }
        if (added) syncSoon()
    }

    private fun notifyChanges(
        allowed: Boolean,
        until: LocalDateTime?,
        now: LocalDateTime,
    ) {
        if (wasAllowed == false && allowed) notifications.allowedNow(until)
        wasAllowed = allowed
        if (allowed && until != null) {
            val notice = until.minusMinutes(ENDING_NOTICE_MINUTES)
            val key = until.toLocalDate().toEpochDay() * 10_000 + until.hour * 100 + until.minute
            if (!now.isBefore(notice) && endingNotified != key) {
                endingNotified = key
                notifications.endingSoon(ENDING_NOTICE_MINUTES)
            }
        }
    }

    // --- Syncing ---

    fun syncSoon() = handler.post { syncNow() }

    // To every paired parent this phone can reach. A parent that cannot be reached is simply tried again next time.
    private fun syncNow() {
        lastSync = clock.now()
        sendGoodbyes()
        val parents = app.peerStore.all()
        if (parents.isEmpty()) return
        val snapshot = snapshot(fresh = true)
        val port = app.endpoint.port
        thread(name = "pocora-sync") {
            for (parent in parents) {
                val answer =
                    app.peerLink.call(parent, Sync(snapshot, port), Protocol.PARENT_PORT) as? SyncAnswer ?: continue
                clock.setFromParent(answer.time)
                app.configStore.lastParentContact = clock.now()
                val name = answer.childName
                if (name != null && name != app.configStore.childName) {
                    app.configStore.childName = name
                    refresh()
                }
            }
        }
    }

    fun snapshot(fresh: Boolean = false): Snapshot {
        cachedSnapshot?.let { if (!fresh && clock.now() - it.takenAt < SNAPSHOT_CACHE_MILLISECONDS) return it }
        val now = clock.now()
        val today = clock.localNow().toLocalDate()
        val rules =
            rulesStore.read()
                ?: Rules(
                    Rules.FIRST_WEEK_SCHEDULE,
                    appsList = Suggested.OPEN_APPS_LIST,
                    quota = Suggested.NO_LIMIT_QUOTA,
                )
        val snapshot =
            Snapshot(
                childId = app.identity.id,
                takenAt = now,
                rules = rules,
                state =
                    AgentState(
                        vpnOn = TunnelService.running,
                        otherVpn = events.isOpen(EventKind.OTHER_VPN),
                        usageAccess = usage.hasAccess(),
                        markBytes = status.markBytes,
                        quotaReached = status.quotaReached,
                    ),
                days = days.all(),
                apps = catalog.installed(),
                usage = usage.days(today.minusDays(Snapshot.DAYS_KEPT - 1L), today, now, app.configStore.pairedAt),
                events = events.all(),
                requests = requests.all(),
            )
        cachedSnapshot = snapshot
        return snapshot
    }

    // --- The parent calling ---

    // Runs on the connection's thread. One message, one answer.
    private fun onParentConnection(
        connection: Connection,
        parent: Peer,
    ) {
        app.peerLink.remember(parent.id, InetSocketAddress(connection.peerAddress, parent.port ?: Protocol.PARENT_PORT))
        app.configStore.lastParentContact = clock.now()
        when (val message = connection.receive(Protocol.REQUEST_TIMEOUT_MILLISECONDS)) {
            is Read -> {
                clock.setFromParent(message.time)
                connection.send(Applied(snapshot(fresh = true)))
            }

            is SetRules -> {
                clock.setFromParent(message.time)
                rulesStore.write(message.rules)
                FileLogger.i(TAG, "New rules from ${parent.deviceName}")
                runOnAgent { work() }
                connection.send(Applied(snapshot(fresh = true)))
            }

            is SendRequest -> {
                requests.add(message.request)
                notifications.request(message.request)
                runOnAgent { work() }
                connection.send(Applied(snapshot(fresh = true)))
            }

            is Unpair -> {
                app.peerStore.remove(parent.id)
                if (app.peerStore.all().isEmpty()) app.configStore.childName = null
                connection.send(Applied())
            }

            else -> {
                FileLogger.w(TAG, "Unexpected message from ${parent.deviceName}")
            }
        }
    }

    // Runs a step on the agent's thread and waits for it, so an answer reflects the change.
    private fun runOnAgent(step: () -> Unit) {
        val done = CountDownLatch(1)
        handler.post {
            try {
                step()
            } finally {
                done.countDown()
            }
        }
        done.await(APPLY_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }

    // --- The child's own actions ---

    // The parent's yes: the agent starts with the rules that came with it, and the header shows the child's name.
    fun paired(answer: PairAnswer) {
        goodbyes.remove(answer.id)
        app.configStore.pairedAt = System.currentTimeMillis()
        answer.childName?.let { app.configStore.childName = it }
        answer.rules?.let {
            rulesStore.write(it)
            AgentService.start(app)
        }
    }

    // The child disconnected, from its own Settings. Each parent is told, now or the next time it can be reached,
    // and everything kept while paired goes, so this phone starts fresh with the next pairing.
    fun disconnect() {
        val parents = app.peerStore.all()
        goodbyes.add(parents, System.currentTimeMillis())
        parents.forEach { app.peerStore.remove(it.id) }
        app.stopService(Intent(app, AgentService::class.java))
        // On the agent's thread, so no tick writes a file back meanwhile.
        handler.post { forgetPairing() }
        sendGoodbyes()
    }

    // Every file, setting and notification from the pairing. The tick after finds no rules and turns the VPN off.
    private fun forgetPairing() {
        rulesStore.delete()
        requests.delete()
        events.delete()
        days.delete()
        app.configStore.forgetPairing()
        notifications.cancelAll()
        status = AgentStatus.EMPTY
        cachedSnapshot = null
        currentMark = null
        quotaReached = false
        quotaWarned = false
        endingNotified = 0
        wasAllowed = null
        vpnDownSince = null
        tick()
    }

    // Tells each parent left behind. Tried on disconnecting, on every sync, and when the app opens, until it answers.
    fun sendGoodbyes() {
        val parents = goodbyes.pending(System.currentTimeMillis())
        if (parents.isEmpty()) return
        thread(name = "pocora-goodbye") {
            for (parent in parents) {
                app.peerLink.call(parent, Unpair(app.identity.id), Protocol.PARENT_PORT) ?: continue
                goodbyes.remove(parent.id)
                FileLogger.i(TAG, "Told ${parent.deviceName} about the disconnect")
            }
        }
    }

    // The child's answer to a parent's request. An approved one opens the store, or Android's own uninstall prompt.
    fun answer(
        context: Context,
        request: Request,
        approved: Boolean,
    ) {
        requests.remove(request.id)
        if (!approved) {
            events.add(EventKind.REQUEST_IGNORED, clock.now(), app = request.`package`, appName = request.appName)
        } else {
            val intent =
                when (request.kind) {
                    RequestKind.INSTALL -> AppStores.detailsIntent(context, request.`package`)
                    RequestKind.REMOVE -> Intent(Intent.ACTION_DELETE, Uri.parse("package:${request.`package`}"))
                }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (error: ActivityNotFoundException) {
                FileLogger.w(TAG, "Nothing to open ${request.kind} with", error)
            }
        }
        notifications.cancelRequest(request)
        refresh()
        syncSoon()
    }

    // A package arrived or left. Installs and removals go on the timeline, and a new VPN app is an alert.
    fun onPackageChanged(
        packageName: String,
        added: Boolean,
    ) = handler.post {
        catalog.invalidate()
        val now = clock.now()
        if (added) {
            val installed = catalog.find(packageName)
            val name = installed?.name ?: packageName
            events.add(EventKind.APP_INSTALLED, now, app = packageName, appName = name)
            if (installed?.vpn == true) events.add(EventKind.VPN_APP_INSTALLED, now, app = packageName, appName = name)
        } else {
            events.add(EventKind.APP_REMOVED, now, app = packageName)
        }
        work()
        syncNow()
    }

    fun onDeviceAdminOff() =
        handler.post {
            events.add(EventKind.DEVICE_ADMIN_OFF, clock.now())
            syncNow()
        }
}
