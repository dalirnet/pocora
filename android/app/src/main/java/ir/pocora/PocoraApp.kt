package ir.pocora

import android.app.Application
import android.content.Context
import ir.pocora.agent.Agent
import ir.pocora.config.ConfigStore
import ir.pocora.config.Identity
import ir.pocora.config.Language
import ir.pocora.config.PeerStore
import ir.pocora.debug.FileLogger
import ir.pocora.parent.Parent
import ir.pocora.service.AgentService
import ir.pocora.transport.Discovery
import ir.pocora.transport.Endpoint
import ir.pocora.transport.PeerLink

class PocoraApp : Application() {
    companion object {
        private const val TAG = "PocoraApp"
    }

    lateinit var configStore: ConfigStore
        private set

    lateinit var peerStore: PeerStore
        private set

    lateinit var discovery: Discovery
        private set

    lateinit var endpoint: Endpoint
        private set

    lateinit var peerLink: PeerLink
        private set

    // The child's agent. Made on first use, so the parent app never builds one.
    val agent: Agent by lazy { Agent(this) }

    // The parent's side. Made on first use, so the child app never builds one.
    val parent: Parent by lazy { Parent(this) }

    // Creating the certificate is slow the first time, so this is first read on a background thread.
    val identity: Identity by lazy { Identity(configStore) }

    // The app sets its own language, whatever the phone's is.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Language.wrap(base, ConfigStore(base).language))
    }

    // A context in the app's language now. Notifications and the tile use it, since the language may have changed
    // since the process started.
    val localized: Context
        get() = Language.wrap(this, configStore.language)

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        configStore = ConfigStore(this)
        peerStore = PeerStore(this)
        discovery = Discovery(this)
        endpoint = Endpoint({ identity }, peerStore, discovery)
        peerLink = PeerLink({ identity }, discovery)
        startServiceIfPaired()
        FileLogger.i(TAG, "Started as ${Role.current}, language ${configStore.language}")
    }

    // A paired child's phone runs its agent from the first moment, opened from the launcher or not,
    // and again after a restart or an update. The parent app runs nothing in the background.
    fun startServiceIfPaired() {
        if (Role.current != Role.CHILD || peerStore.all().isEmpty()) return
        AgentService.start(this)
    }
}
