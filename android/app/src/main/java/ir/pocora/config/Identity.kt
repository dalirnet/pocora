package ir.pocora.config

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ir.pocora.debug.FileLogger
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Calendar
import javax.security.auth.x500.X500Principal

// This phone's identity: a random id and a self-signed certificate whose key never leaves the Keystore.
// Creating the key takes a moment, so the first use must be off the main thread.
class Identity(
    configStore: ConfigStore,
) {
    companion object {
        private const val TAG = "Identity"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "pocora_identity"
        private const val CURVE = "secp256r1"
        private const val SUBJECT = "CN=Pocora"
        private const val VALIDITY_YEARS = 50
    }

    val id: String = configStore.deviceId
    val certificate: X509Certificate
    val privateKey: PrivateKey
    val fingerprint: String

    init {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(ALIAS)) generate()
        certificate = keyStore.getCertificate(ALIAS) as X509Certificate
        privateKey = keyStore.getKey(ALIAS, null) as PrivateKey
        fingerprint = Fingerprint.of(certificate)
    }

    private fun generate() {
        val notBefore = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val notAfter = Calendar.getInstance().apply { add(Calendar.YEAR, VALIDITY_YEARS) }
        val specification =
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                // TLS hands the Keystore a finished hash to sign, which needs DIGEST_NONE.
                .setDigests(
                    KeyProperties.DIGEST_NONE,
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA384,
                    KeyProperties.DIGEST_SHA512,
                ).setCertificateSubject(X500Principal(SUBJECT))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(notBefore.time)
                .setCertificateNotAfter(notAfter.time)
                .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
            initialize(specification)
            generateKeyPair()
        }
        FileLogger.i(TAG, "Created this phone's certificate")
    }
}

object Fingerprint {
    private const val ALGORITHM = "SHA-256"
    private const val HEX_DIGITS = "0123456789abcdef"

    // The SHA-256 of the whole certificate, as 64 lowercase hex characters.
    // Built by hand: String.format would write Persian digits while the app runs in Persian.
    fun of(certificate: Certificate): String {
        val digest = MessageDigest.getInstance(ALGORITHM).digest(certificate.encoded)
        val text = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            text.append(HEX_DIGITS[value ushr 4]).append(HEX_DIGITS[value and 0x0F])
        }
        return text.toString()
    }
}
