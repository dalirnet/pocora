package ir.pocora.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordTest {
    // --- Matches ---

    @Test
    fun matches_samePassword() {
        val (salt, hash) = Password.create("1234")
        assertTrue(Password.matches("1234", salt, hash))
    }

    @Test
    fun matches_rejectsOtherPassword() {
        val (salt, hash) = Password.create("1234")
        assertFalse(Password.matches("1243", salt, hash))
    }

    // --- Create ---

    @Test
    fun create_newSaltEachTime() {
        val first = Password.create("1234")
        val second = Password.create("1234")
        assertNotEquals(first.first, second.first)
        assertNotEquals(first.second, second.second)
    }
}
