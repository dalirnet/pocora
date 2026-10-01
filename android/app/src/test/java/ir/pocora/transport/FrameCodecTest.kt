package ir.pocora.transport

import ir.pocora.protocol.Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameCodecTest {
    // --- Encode ---

    @Test
    fun encode_prefixesLength() {
        val frame = FrameCodec.encode(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(0, 0, 0, 3, 1, 2, 3), frame)
    }

    @Test
    fun encode_emptyPayload() {
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), FrameCodec.encode(ByteArray(0)))
    }

    @Test
    fun encode_tooLarge() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameCodec.encode(ByteArray(Protocol.MAXIMUM_PAYLOAD_SIZE_BYTES + 1))
        }
    }

    // --- Decode ---

    @Test
    fun decodeLength_roundTrip() {
        val payload = ByteArray(70_000) { it.toByte() }
        val frame = FrameCodec.encode(payload)
        assertEquals(payload.size, FrameCodec.decodeLength(frame.copyOfRange(0, Protocol.FRAME_HEADER_SIZE_BYTES)))
    }

    @Test
    fun decodeLength_tooLarge() {
        assertNull(FrameCodec.decodeLength(byteArrayOf(0x7F, 0, 0, 0)))
    }

    @Test
    fun decodeLength_negative() {
        assertNull(FrameCodec.decodeLength(byteArrayOf(0xFF.toByte(), 0, 0, 0)))
    }
}
