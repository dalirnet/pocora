package ir.pocora.transport

import ir.pocora.config.Identity
import ir.pocora.model.Peer
import ir.pocora.protocol.Message
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

// Calls a paired phone: finds it, opens mutual TLS pinned to its fingerprint, sends one message and reads the answer.
// Blocking. Call it off the main thread.
class PeerLink(
    private val identity: () -> Identity,
    private val discovery: Discovery,
) {
    companion object {
        private const val QUICK_DISCOVERY_MILLISECONDS = 2_000L
        private const val QUICK_CONNECT_MILLISECONDS = 3_000
        private const val ANSWER_TIMEOUT_MILLISECONDS = 20_000
    }

    private val lastAddress = ConcurrentHashMap<String, InetSocketAddress>()

    // Where this peer was last reached or last called from. Tried first next time.
    fun remember(
        peerId: String,
        address: InetSocketAddress,
    ) {
        lastAddress[peerId] = address
    }

    // Returns null when the peer cannot be reached or gives no answer. Nothing is queued: the caller retries.
    fun call(
        peer: Peer,
        message: Message,
        fallbackPort: Int,
        answerTimeoutMilliseconds: Int = ANSWER_TIMEOUT_MILLISECONDS,
    ): Message? {
        val connection = connect(peer, peer.port ?: fallbackPort) ?: return null
        return connection.use {
            if (!it.send(message)) null else it.receive(answerTimeoutMilliseconds)
        }
    }

    private fun connect(
        peer: Peer,
        port: Int,
    ): Connection? {
        val tried = mutableSetOf<InetSocketAddress>()
        val candidates =
            sequence {
                lastAddress[peer.id]?.let { yield(it) }
                yieldAll(discovery.addresses(peer.id, port, QUICK_DISCOVERY_MILLISECONDS))
            }
        for (address in candidates) {
            if (!tried.add(address)) continue
            val connection = Tls.open(identity(), peer.fingerprint, address, QUICK_CONNECT_MILLISECONDS) ?: continue
            lastAddress[peer.id] = address
            return connection
        }
        return null
    }
}
