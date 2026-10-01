package ir.pocora.transport

import ir.pocora.config.Identity
import ir.pocora.debug.FileLogger
import ir.pocora.protocol.Protocol
import java.io.IOException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

// Listens for the other phone. Each connection gets its own thread, which onConnection may hold for as long as it needs.
class Server(
    private val identity: Identity,
    private val isTrusted: (String) -> Boolean,
    private val onConnection: (Connection) -> Unit,
) {
    companion object {
        private const val TAG = "Server"
    }

    var port = 0
        private set

    private var serverSocket: SSLServerSocket? = null

    // Takes the preferred port, or any free one when it is taken.
    fun start(preferredPort: Int) {
        val factory = Tls.context(identity, isTrusted).serverSocketFactory
        val socket =
            try {
                factory.createServerSocket(preferredPort)
            } catch (error: IOException) {
                FileLogger.w(TAG, "Port $preferredPort is taken, using another", error)
                factory.createServerSocket(0)
            } as SSLServerSocket
        socket.needClientAuth = true
        serverSocket = socket
        port = socket.localPort
        FileLogger.i(TAG, "Listening on port $port")
        thread(name = "pocora-server") { acceptLoop(socket) }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
    }

    private fun acceptLoop(socket: SSLServerSocket) {
        while (!socket.isClosed) {
            val client =
                try {
                    socket.accept() as SSLSocket
                } catch (_: IOException) {
                    break
                }
            thread(name = "pocora-connection") { serve(client) }
        }
    }

    private fun serve(client: SSLSocket) {
        try {
            client.soTimeout = Protocol.HANDSHAKE_TIMEOUT_MILLISECONDS
            client.startHandshake()
            Connection(client).use(onConnection)
        } catch (error: IOException) {
            FileLogger.w(TAG, "Connection refused", error)
            try {
                client.close()
            } catch (_: IOException) {
            }
        }
    }
}
