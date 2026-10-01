package ir.pocora.transport

import ir.pocora.protocol.Protocol

object FrameCodec {
    fun encode(payload: ByteArray): ByteArray {
        require(payload.size <= Protocol.MAXIMUM_PAYLOAD_SIZE_BYTES) { "Payload too large: ${payload.size}" }
        val frame = ByteArray(Protocol.FRAME_HEADER_SIZE_BYTES + payload.size)
        frame[0] = (payload.size ushr 24).toByte()
        frame[1] = (payload.size ushr 16).toByte()
        frame[2] = (payload.size ushr 8).toByte()
        frame[3] = payload.size.toByte()
        System.arraycopy(payload, 0, frame, Protocol.FRAME_HEADER_SIZE_BYTES, payload.size)
        return frame
    }

    // Returns null when the header announces a size no frame may have.
    fun decodeLength(header: ByteArray): Int? {
        val length =
            (header[0].toInt() and 0xFF shl 24) or
                (header[1].toInt() and 0xFF shl 16) or
                (header[2].toInt() and 0xFF shl 8) or
                (header[3].toInt() and 0xFF)
        return if (length in 0..Protocol.MAXIMUM_PAYLOAD_SIZE_BYTES) length else null
    }
}
