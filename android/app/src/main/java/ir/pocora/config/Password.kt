package ir.pocora.config

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// The parent app's password. Only a salted hash is kept, never the digits.
object Password {
    const val LENGTH = 4
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    // A new salt and the hash of the password with it, both as Base64.
    fun create(password: String): Pair<String, String> {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return encode(salt) to encode(hash(password, salt))
    }

    fun matches(
        password: String,
        salt: String,
        hash: String,
    ): Boolean = MessageDigest.isEqual(hash(password, decode(salt)), decode(hash))

    private fun hash(
        password: String,
        salt: ByteArray,
    ): ByteArray {
        val specification = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(specification).encoded
        } finally {
            specification.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}
