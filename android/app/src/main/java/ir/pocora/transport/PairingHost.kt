package ir.pocora.transport

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ir.pocora.config.Identity
import ir.pocora.config.PeerStore
import ir.pocora.debug.FileLogger
import ir.pocora.model.Peer
import ir.pocora.model.Rules
import ir.pocora.protocol.PairAnswer
import ir.pocora.protocol.PairRequest
import ir.pocora.protocol.Protocol
import java.util.concurrent.ArrayBlockingQueue

// The parent's side of pairing, alive while the pairing code is on screen.
// It holds a child's request open until the parent accepts or rejects it. The callbacks run on the main thread.
class PairingHost(
    private val identity: () -> Identity,
    private val peerStore: PeerStore,
    private val onRequest: (PairRequest) -> Unit,
    private val onPaired: (Peer) -> Unit,
    private val onRequestGone: () -> Unit,
) {
    companion object {
        private const val TAG = "PairingHost"
        private const val POLL_INTERVAL_MILLISECONDS = 250
    }

    private class Decision(
        val accepted: Boolean,
        val name: String,
        val age: Int?,
        val rules: Rules?,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var pending: ArrayBlockingQueue<Decision>? = null

    // Runs on the connection's own thread and keeps it until the parent has answered.
    fun handle(connection: Connection) {
        val request = connection.receive(Protocol.PAIR_REQUEST_TIMEOUT_MILLISECONDS) as? PairRequest ?: return
        val decisions = ArrayBlockingQueue<Decision>(1)
        synchronized(lock) {
            // One request at a time. A second phone asking meanwhile is turned away.
            if (pending != null) {
                connection.send(answer(false))
                return
            }
            pending = decisions
        }
        FileLogger.i(TAG, "Pairing request from ${request.deviceName}")
        mainHandler.post { onRequest(request) }

        val decision = awaitDecision(connection, decisions)
        synchronized(lock) { pending = null }

        if (decision == null || !decision.accepted) {
            connection.send(answer(false))
            if (decision == null) mainHandler.post(onRequestGone)
            return
        }
        // Saved only once the answer is on its way, so a child that gave up is not left half paired.
        if (!connection.send(answer(true, decision.rules, decision.name))) {
            mainHandler.post(onRequestGone)
            return
        }
        val peer =
            Peer(
                request.id,
                connection.peerFingerprint,
                decision.name,
                request.deviceName,
                decision.age,
                Protocol.CHILD_PORT,
            )
        peerStore.save(peer)
        FileLogger.i(TAG, "Paired with ${request.deviceName}")
        mainHandler.post { onPaired(peer) }
    }

    // Returns null when the time is up or the child's phone gave up waiting.
    private fun awaitDecision(
        connection: Connection,
        decisions: ArrayBlockingQueue<Decision>,
    ): Decision? {
        val deadline = SystemClock.elapsedRealtime() + Protocol.PAIR_ANSWER_TIMEOUT_MILLISECONDS
        while (SystemClock.elapsedRealtime() < deadline) {
            decisions.poll()?.let { return it }
            if (connection.hasPeerLeft(POLL_INTERVAL_MILLISECONDS)) return null
        }
        return null
    }

    // The child's phone starts on the rules chosen while adding the child, so it is never left with every mark Limited.
    fun accept(
        name: String,
        age: Int?,
        rules: Rules,
    ) = decide(Decision(true, name, age, rules))

    fun reject() = decide(Decision(false, "", null, null))

    private fun decide(decision: Decision) {
        synchronized(lock) { pending }?.offer(decision)
    }

    private fun answer(
        accepted: Boolean,
        rules: Rules? = null,
        childName: String? = null,
    ) = PairAnswer(accepted, identity().id, Device.name, rules, childName)
}
