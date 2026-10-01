package ir.pocora.transport

import android.os.Handler
import android.os.Looper
import ir.pocora.Role
import ir.pocora.config.Identity
import ir.pocora.config.PeerStore
import ir.pocora.debug.FileLogger
import ir.pocora.model.Peer
import ir.pocora.protocol.PairingCode
import ir.pocora.protocol.Protocol
import kotlin.concurrent.thread

// This phone's side of the connection: the listening socket and the announcement on the network.
// Paired phones go to onPaired. Unpaired ones get in only while a pairing host is open.
class Endpoint(
    private val identity: () -> Identity,
    private val peerStore: PeerStore,
    private val discovery: Discovery,
) {
    companion object {
        private const val TAG = "Endpoint"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var server: Server? = null

    @Volatile
    private var pairingHost: PairingHost? = null

    // What to do with a connection from a paired phone. Runs on the connection's own thread.
    @Volatile
    var onPaired: ((Connection, Peer) -> Unit)? = null

    val port: Int
        get() = server?.port ?: Protocol.portOf(Role.current)

    // Starts listening if needed, lets unpaired phones in for as long as the host is open,
    // and hands the pairing code to onCode on the main thread.
    fun openPairing(
        host: PairingHost,
        onCode: (PairingCode) -> Unit,
    ) {
        pairingHost = host
        thread(name = "pocora-endpoint") {
            val port = start()
            val code = PairingCode(identity().id, identity().fingerprint, port)
            // Lets a virtual phone pair without a camera. The code is not a secret: the parent approves each phone.
            FileLogger.d(TAG, "Pairing code: ${code.encode()}")
            mainHandler.post { if (pairingHost === host) onCode(code) }
        }
    }

    fun closePairing(host: PairingHost) {
        if (pairingHost === host) pairingHost = null
        host.reject()
    }

    // Blocking: call it off the main thread. Safe to call again.
    @Synchronized
    fun start(): Int {
        server?.let { return it.port }
        val started = Server(identity(), ::isTrusted, ::onConnection)
        started.start(Protocol.portOf(Role.current))
        server = started
        discovery.announce(identity().id, started.port)
        return started.port
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        discovery.stopAnnouncing()
    }

    // Announce again, as after the Wi-Fi changed, so the new address is the one found.
    fun reannounce() {
        val running = server ?: return
        discovery.announce(identity().id, running.port)
    }

    // A phone that is not paired gets through the handshake only while the pairing code is on screen.
    private fun isTrusted(fingerprint: String): Boolean = pairingHost != null || peerStore.find(fingerprint) != null

    private fun onConnection(connection: Connection) {
        val host = pairingHost
        val peer = peerStore.find(connection.peerFingerprint)
        when {
            host != null && peer == null -> host.handle(connection)
            peer != null -> onPaired?.invoke(connection, peer)
            else -> FileLogger.w(TAG, "Unknown phone connected")
        }
    }
}
