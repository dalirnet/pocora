package ir.pocora.model

import kotlinx.serialization.Serializable

// A paired phone: a child on the parent's phone, a parent on the child's phone.
// The fingerprint is the whole of the trust. The rest is for showing.
@Serializable
data class Peer(
    val id: String,
    val fingerprint: String,
    val name: String,
    val deviceName: String,
    val age: Int? = null,
    // The port the other phone listens on, for when discovery finds nothing.
    val port: Int? = null,
    // The key the Bluetooth wake-up tags are made from. The child's phone makes it and sends it with each sync.
    val wakeKey: String? = null,
)
