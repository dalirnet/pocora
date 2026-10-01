package ir.pocora.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingCodeTest {
    private val fingerprint = "0123456789abcdef".repeat(4)
    private val code = PairingCode("3f2b9c1e-7a44-4d0e-9b51-5c8e2a6f1d03", fingerprint, 47601)

    // --- Encode ---

    @Test
    fun encode_joinsTheParts() {
        assertEquals("pocora:1:3f2b9c1e-7a44-4d0e-9b51-5c8e2a6f1d03:$fingerprint:47601", code.encode())
    }

    // --- Parse ---

    @Test
    fun parse_roundTrip() {
        assertEquals(code, PairingCode.parse(code.encode()))
    }

    @Test
    fun parse_ignoresSurroundingSpace() {
        assertEquals(code, PairingCode.parse(" ${code.encode()}\n"))
    }

    @Test
    fun parse_otherText() {
        assertNull(PairingCode.parse("https://example.com"))
    }

    @Test
    fun parse_otherVersion() {
        assertNull(PairingCode.parse("pocora:2:id:$fingerprint:47601"))
    }

    @Test
    fun parse_shortFingerprint() {
        assertNull(PairingCode.parse("pocora:1:id:abc123:47601"))
    }

    @Test
    fun parse_uppercaseFingerprint() {
        assertNull(PairingCode.parse("pocora:1:id:${fingerprint.uppercase()}:47601"))
    }

    @Test
    fun parse_emptyId() {
        assertNull(PairingCode.parse("pocora:1::$fingerprint:47601"))
    }

    @Test
    fun parse_portOutOfRange() {
        assertNull(PairingCode.parse("pocora:1:id:$fingerprint:0"))
        assertNull(PairingCode.parse("pocora:1:id:$fingerprint:65536"))
        assertNull(PairingCode.parse("pocora:1:id:$fingerprint:port"))
    }
}
