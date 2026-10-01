package ir.pocora.transport

import ir.pocora.config.Fingerprint
import ir.pocora.protocol.Message
import ir.pocora.protocol.MessageCodec
import ir.pocora.protocol.Protocol
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.net.ssl.SSLSocket

// One open TLS socket to the other phone, after the handshake. Messages travel as length-prefixed frames.
class Connection(
    private val socket: SSLSocket,
) : Closeable {
    val peerFingerprint: String = Fingerprint.of(socket.session.peerCertificates.first())

    // Where the other phone is, so it can be called back there.
    val peerAddress: InetAddress = socket.inetAddress

    private val input = DataInputStream(socket.inputStream)
    private val output = socket.outputStream

    fun send(message: Message): Boolean =
        try {
            output.write(FrameCodec.encode(MessageCodec.encode(message)))
            output.flush()
            true
        } catch (_: IOException) {
            false
        }

    // Returns null when the time is up, the socket closed, or the frame is not a known message.
    fun receive(timeoutMilliseconds: Int): Message? =
        try {
            socket.soTimeout = timeoutMilliseconds
            val header = ByteArray(Protocol.FRAME_HEADER_SIZE_BYTES)
            input.readFully(header)
            FrameCodec.decodeLength(header)?.let { length ->
                val payload = ByteArray(length)
                input.readFully(payload)
                MessageCodec.decode(payload)
            }
        } catch (_: IOException) {
            null
        }

    // Waits up to the timeout and reports whether the other phone hung up meanwhile.
    // Only for a moment when nothing is expected from it: a stray byte counts as leaving.
    fun hasPeerLeft(timeoutMilliseconds: Int): Boolean =
        try {
            socket.soTimeout = timeoutMilliseconds
            input.read()
            true
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: IOException) {
            true
        }

    override fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }
}
