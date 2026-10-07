package ir.pocora.protocol

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// The short tag a Bluetooth wake-up signal carries. It is made from a key only the two paired phones hold, and it
// changes every few minutes, so no other phone can send one that counts or follow a phone by the one it sends.
// Each direction has its own tag, so a phone never wakes itself.
object WakeTag {
    const val SIZE_BYTES = 8
    private const val KEY_SIZE_BYTES = 32
    private const val SLOT_MILLISECONDS = 5 * 60_000L
    private const val ALGORITHM = "HmacSHA256"

    enum class Direction(
        val code: Byte,
    ) {
        TO_CHILD(1),
        TO_PARENT(2),
    }

    // Made on the child's phone, once per parent, and sent to that parent over the TLS link with each sync.
    fun newKey(): String {
        val bytes = ByteArray(KEY_SIZE_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun of(
        key: String,
        direction: Direction,
        time: Long,
    ): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(Base64.getDecoder().decode(key), ALGORITHM))
        val slot = ByteBuffer.allocate(1 + Long.SIZE_BYTES).put(direction.code).putLong(time / SLOT_MILLISECONDS)
        return mac.doFinal(slot.array()).copyOf(SIZE_BYTES)
    }

    // The slot before and after count too: the two clocks may differ a little, and a signal may cross a slot's end.
    fun matches(
        key: String,
        direction: Direction,
        tag: ByteArray,
        time: Long,
    ): Boolean =
        (-1..1).any { step ->
            MessageDigest.isEqual(of(key, direction, time + step * SLOT_MILLISECONDS), tag)
        }
}
