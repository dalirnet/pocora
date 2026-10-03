package ir.pocora.model

import ir.pocora.preset.TestPresets
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SuggestedTest {
    private val presets = TestPresets.presets
    private val today = LocalDate.of(2026, 10, 3)

    private fun quota(age: Int?) = Suggested.rules(presets, age, today).quota

    // --- Quota by age ---

    @Test
    fun quota_followsTheAge() {
        assertEquals("medium", quota(8))
        assertEquals("fairly-high", quota(11))
        assertEquals("high", quota(15))
    }

    @Test
    fun quota_withoutAnAge() {
        assertEquals("fairly-high", quota(null))
        assertEquals("fairly-high", quota(18))
    }

    @Test
    fun everyAgeQuota_isAPreset() {
        for (list in presets.appsLists) list.quota?.let { id -> assertEquals(list.id, id, presets.quota(id).id) }
    }
}
