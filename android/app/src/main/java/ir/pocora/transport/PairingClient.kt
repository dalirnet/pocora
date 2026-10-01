package ir.pocora.transport

import android.os.Handler
import android.os.Looper
import ir.pocora.config.Identity
import ir.pocora.config.PeerStore
import ir.pocora.debug.FileLogger
import ir.pocora.model.Peer
import ir.pocora.protocol.PairAnswer
import ir.pocora.protocol.PairRequest
import ir.pocora.protocol.PairingCode
import ir.pocora.protocol.Protocol
import kotlin.concurrent.thread

// The child's side of pairing: finds the parent named in the code, asks to pair, and waits for the answer.
// onResult runs on the main thread, and never after cancel.
class PairingClient(
    private val identity: () -> Identity,
    private val peerStore: PeerStore,
    private val discovery: Discovery,
    private val code: PairingCode,
    // Takes the parent's yes, with the first rules and the child's name, on the pairing thread, before the parent is saved.
    private val onAccepted: (PairAnswer) -> Unit,
    private val onResult: (PairingResult) -> Unit,
) {
    companion object {
        private const val TAG = "PairingClient"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cancelled = false

    @Volatile
    private var connection: Connection? = null

    fun start() {
        thread(name = "pocora-pairing") {
            val result = pair()
            mainHandler.post { if (!cancelled) onResult(result) }
        }
    }

    fun cancel() {
        cancelled = true
        connection?.close()
    }

    private fun pair(): PairingResult {
        val opened = connect() ?: return PairingResult.NOT_REACHABLE
        connection = opened
        return opened.use {
            if (cancelled || !it.send(PairRequest(identity().id, Device.name, Device.androidVersion))) {
                return@use PairingResult.NOT_REACHABLE
            }
            val answer = it.receive(Protocol.PAIR_ANSWER_TIMEOUT_MILLISECONDS) as? PairAnswer
            when {
                answer == null -> {
                    PairingResult.NOT_REACHABLE
                }

                !answer.accepted -> {
                    PairingResult.REJECTED
                }

                else -> {
                    onAccepted(answer)
                    peerStore.save(
                        Peer(answer.id, code.fingerprint, answer.deviceName, answer.deviceName, port = code.port),
                    )
                    FileLogger.i(TAG, "Paired with ${answer.deviceName}")
                    PairingResult.ACCEPTED
                }
            }
        }
    }

    private fun connect(): Connection? {
        for (address in discovery.addresses(code.id, code.port, Protocol.DISCOVERY_TIMEOUT_MILLISECONDS)) {
            if (cancelled) return null
            Tls.open(identity(), code.fingerprint, address, Protocol.CONNECT_TIMEOUT_MILLISECONDS)?.let { return it }
        }
        return null
    }
}

enum class PairingResult {
    ACCEPTED,
    REJECTED,
    NOT_REACHABLE,
}
