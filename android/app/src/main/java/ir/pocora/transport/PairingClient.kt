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
import java.io.Serializable
import kotlin.concurrent.thread

// The child's side of pairing: finds the parent named in the code, asks to pair, and waits for the answer.
// onResult runs on the main thread, and never after cancel.
class PairingClient(
    private val identity: () -> Identity,
    private val peerStore: PeerStore,
    private val discovery: Discovery,
    private val code: PairingCode,
    private val version: String,
    // Takes the parent's yes, with the first rules and the child's name, on the pairing thread, before the parent is saved.
    private val onAccepted: (PairAnswer) -> Unit,
    // Handed over by the parent app on this same phone, which then accepts without asking. See SamePhone.
    private val token: String? = null,
    // The name this phone goes by with the parents it already has, when it has any.
    private val childName: String? = null,
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

    // Called from the main thread, where closing a TLS socket is not allowed: it may still write.
    fun cancel() {
        cancelled = true
        connection?.let { open -> thread(name = "pocora-pairing-cancel") { open.close() } }
    }

    private fun pair(): PairingResult {
        val opened = connect() ?: return PairingResult.NotReachable
        connection = opened
        return opened.use {
            if (cancelled ||
                !it.send(PairRequest(identity().id, Device.name, Device.androidVersion, token, childName, version))
            ) {
                return@use PairingResult.NotReachable
            }
            val answer = it.receive(Protocol.PAIR_ANSWER_TIMEOUT_MILLISECONDS) as? PairAnswer
            when {
                answer == null -> {
                    PairingResult.NotReachable
                }

                !answer.accepted -> {
                    if (answer.version != null && answer.version != version) {
                        PairingResult.OtherVersion
                    } else {
                        PairingResult.Rejected
                    }
                }

                else -> {
                    onAccepted(answer)
                    peerStore.save(
                        Peer(answer.id, code.fingerprint, answer.deviceName, answer.deviceName, port = code.port),
                    )
                    FileLogger.i(TAG, "Paired with ${answer.deviceName}")
                    PairingResult.Accepted
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

// How pairing ended. Serializable, so a screen can keep it across a restart.
sealed interface PairingResult : Serializable {
    data object Accepted : PairingResult

    data object Rejected : PairingResult

    data object NotReachable : PairingResult

    // The parent's phone runs another version of Pocora. Both apps must be the same version.
    data object OtherVersion : PairingResult
}
