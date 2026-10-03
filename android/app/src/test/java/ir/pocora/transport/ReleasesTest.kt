package ir.pocora.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    @Test
    fun newerByAnyPart() {
        assertTrue(Releases.isNewer("1.1.1", "1.1.0"))
        assertTrue(Releases.isNewer("1.2.0", "1.1.9"))
        assertTrue(Releases.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun partsCompareAsNumbers() {
        assertTrue(Releases.isNewer("1.10.0", "1.9.0"))
        assertFalse(Releases.isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun sameOrOlderIsNotNewer() {
        assertFalse(Releases.isNewer("1.1.0", "1.1.0"))
        assertFalse(Releases.isNewer("1.0.9", "1.1.0"))
    }

    @Test
    fun missingPartsCountAsZero() {
        assertFalse(Releases.isNewer("1.1", "1.1.0"))
        assertTrue(Releases.isNewer("1.1.0.1", "1.1.0"))
    }
}
