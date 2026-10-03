package ir.pocora.protocol

import ir.pocora.model.Rules
import ir.pocora.model.Snapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The messages the two phones send each other.

// Everything the two apps say to each other. Each message travels as one JSON frame.
@Serializable
sealed interface Message

// Child to parent, after scanning the code. The child's certificate arrives with the TLS handshake.
// With both apps on one phone, the token the parent app handed over with its code: the parent asked for this
// pairing on this phone, so it is accepted without asking again.
// A phone already paired with another parent sends the name it goes by: the new parent takes it, and the rules stay.
@Serializable
@SerialName("pair_request")
data class PairRequest(
    val id: String,
    val deviceName: String,
    val androidVersion: String,
    val token: String? = null,
    val childName: String? = null,
) : Message

// Parent to child, once the parent has accepted or rejected. An accepted child gets its first rules with it,
// so its phone never starts with every mark Limited, and the name the parent gave it, for its header.
@Serializable
@SerialName("pair_answer")
data class PairAnswer(
    val accepted: Boolean,
    val id: String,
    val deviceName: String,
    val rules: Rules? = null,
    val childName: String? = null,
) : Message

// Child to parent, every 60 seconds and when something happens. The port is where the child listens.
@Serializable
@SerialName("sync")
data class Sync(
    val snapshot: Snapshot,
    val port: Int,
) : Message

// Parent to child: the parent's clock, which the child counts forward from, and the child's name as the parent
// has it, so phones paired before the name was sent learn it too.
@Serializable
@SerialName("sync_answer")
data class SyncAnswer(
    val time: Long,
    val childName: String? = null,
) : Message

// Parent to child: a fresh snapshot, now.
@Serializable
@SerialName("read")
data class Read(
    val time: Long,
) : Message

// Parent to child: the whole of the child's rules, as edited on the parent's phone.
@Serializable
@SerialName("set_rules")
data class SetRules(
    val rules: Rules,
    val time: Long,
) : Message

// Either side: forget me. Sent when a parent forgets a child, if the child's phone can be reached.
@Serializable
@SerialName("unpair")
data class Unpair(
    val id: String,
) : Message

// Child to parent: done, with the snapshot as it is now. Also the answer to Read.
@Serializable
@SerialName("applied")
data class Applied(
    val snapshot: Snapshot? = null,
) : Message

object MessageCodec {
    private val json =
        Json {
            classDiscriminator = "type"
            ignoreUnknownKeys = true
        }

    fun encode(message: Message): ByteArray =
        json.encodeToString(Message.serializer(), message).toByteArray(Charsets.UTF_8)

    // Returns null for anything that is not a known message.
    fun decode(payload: ByteArray): Message? =
        try {
            json.decodeFromString(Message.serializer(), String(payload, Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            null
        }
}
