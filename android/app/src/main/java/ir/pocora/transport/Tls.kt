package ir.pocora.transport

import android.annotation.SuppressLint
import ir.pocora.config.Fingerprint
import ir.pocora.config.Identity
import ir.pocora.debug.FileLogger
import ir.pocora.protocol.Protocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

object Tls {
    private const val TAG = "Tls"
    private const val PROTOCOL = "TLS"

    // Mutual TLS: this phone shows its own certificate and accepts only fingerprints that isTrusted allows.
    fun context(
        identity: Identity,
        isTrusted: (String) -> Boolean,
    ): SSLContext =
        SSLContext.getInstance(PROTOCOL).apply {
            init(arrayOf(IdentityKeyManager(identity)), arrayOf(PinnedTrustManager(isTrusted)), null)
        }

    // Connects to one address and finishes the handshake, or gives up quietly so the caller can try the next.
    fun open(
        identity: Identity,
        fingerprint: String,
        address: InetSocketAddress,
        connectTimeoutMilliseconds: Int,
    ): Connection? {
        val socket = context(identity) { it == fingerprint }.socketFactory.createSocket() as SSLSocket
        return try {
            socket.connect(address, connectTimeoutMilliseconds)
            socket.soTimeout = Protocol.HANDSHAKE_TIMEOUT_MILLISECONDS
            socket.startHandshake()
            Connection(socket)
        } catch (error: IOException) {
            FileLogger.d(TAG, "Not at $address: ${error.message}")
            try {
                socket.close()
            } catch (_: IOException) {
            }
            null
        }
    }
}

// Offers this phone's one certificate, as server and as client.
private class IdentityKeyManager(
    private val identity: Identity,
) : X509KeyManager {
    companion object {
        private const val ALIAS = "pocora"
        private const val KEY_TYPE = "EC"
    }

    override fun chooseClientAlias(
        keyType: Array<String>?,
        issuers: Array<Principal>?,
        socket: Socket?,
    ): String = ALIAS

    // The server is asked once per key type. Answering for another type would offer a key it does not have.
    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<Principal>?,
        socket: Socket?,
    ): String? = if (keyType == KEY_TYPE) ALIAS else null

    override fun getClientAliases(
        keyType: String?,
        issuers: Array<Principal>?,
    ): Array<String> = arrayOf(ALIAS)

    override fun getServerAliases(
        keyType: String?,
        issuers: Array<Principal>?,
    ): Array<String> = arrayOf(ALIAS)

    override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(identity.certificate)

    override fun getPrivateKey(alias: String?): PrivateKey = identity.privateKey
}

// Trust is a saved fingerprint, nothing else: no certificate authority, no hostname, no dates.
// The handshake fails unless the other phone's certificate is one this phone expects.
@SuppressLint("CustomX509TrustManager")
private class PinnedTrustManager(
    private val isTrusted: (String) -> Boolean,
) : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<X509Certificate>?,
        authType: String?,
    ) = check(chain)

    override fun checkServerTrusted(
        chain: Array<X509Certificate>?,
        authType: String?,
    ) = check(chain)

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun check(chain: Array<X509Certificate>?) {
        val certificate = chain?.firstOrNull() ?: throw CertificateException("No certificate")
        if (!isTrusted(Fingerprint.of(certificate))) throw CertificateException("Unknown device")
    }
}
