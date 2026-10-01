package ir.pocora.protocol

// What the parent's QR code carries: who to look for, which certificate to expect, and where it listens.
// None of it is secret. The parent still approves every phone that asks to pair.
data class PairingCode(
    val id: String,
    val fingerprint: String,
    val port: Int,
) {
    companion object {
        private const val PREFIX = "pocora"
        private const val VERSION = "1"
        private const val SEPARATOR = ":"
        private const val PART_COUNT = 5
        private const val MAXIMUM_ID_LENGTH = 64
        private const val MAXIMUM_PORT = 65_535
        private val FINGERPRINT_PATTERN = Regex("[0-9a-f]{64}")

        // Returns null for any text that is not a Pocora pairing code.
        fun parse(text: String): PairingCode? {
            val parts = text.trim().split(SEPARATOR)
            if (parts.size != PART_COUNT || parts[0] != PREFIX || parts[1] != VERSION) return null
            val id = parts[2]
            val fingerprint = parts[3]
            val port = parts[4].toIntOrNull() ?: return null
            if (id.isEmpty() || id.length > MAXIMUM_ID_LENGTH) return null
            if (!FINGERPRINT_PATTERN.matches(fingerprint)) return null
            if (port !in 1..MAXIMUM_PORT) return null
            return PairingCode(id, fingerprint, port)
        }
    }

    fun encode(): String = listOf(PREFIX, VERSION, id, fingerprint, port).joinToString(SEPARATOR)
}
