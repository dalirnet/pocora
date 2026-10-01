package ir.pocora.model

import ir.pocora.preset.AppGroup
import ir.pocora.preset.TestPresets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAccessTest {
    private val presets = TestPresets.presets
    private val rules = Rules(schedule = "school-morning", appsList = "everyday", quota = "medium")

    private fun app(
        group: String,
        system: Boolean = false,
        vpn: Boolean = false,
    ) = InstalledApp("com.example.app", "Example", "1.0", group, system, vpn)

    // --- By list ---

    @Test
    fun groupInList() {
        assertTrue(AppAccess.hasInternet(presets, rules, app(AppGroup.GAMES), "everyday"))
    }

    @Test
    fun groupNotInList() {
        assertFalse(AppAccess.hasInternet(presets, rules, app(AppGroup.SOCIAL), "everyday"))
    }

    @Test
    fun blockList() {
        assertFalse(AppAccess.hasInternet(presets, rules, app(AppGroup.GAMES), "study"))
    }

    // --- Always and never ---

    @Test
    fun systemAlways() {
        assertTrue(AppAccess.hasInternet(presets, rules, app(AppGroup.OTHER, system = true), "school"))
    }

    @Test
    fun vpnNever() {
        val fixed = rules.copy(apps = mapOf("com.example.app" to AppChoice.IN))
        assertFalse(AppAccess.hasInternet(presets, fixed, app(AppGroup.OTHER, vpn = true), "everything"))
    }

    // --- Fixed by the parent ---

    @Test
    fun fixedIn() {
        val fixed = rules.copy(apps = mapOf("com.example.app" to AppChoice.IN))
        assertTrue(AppAccess.hasInternet(presets, fixed, app(AppGroup.BROWSER), "everyday"))
    }

    @Test
    fun fixedOut() {
        val fixed = rules.copy(apps = mapOf("com.example.app" to AppChoice.OUT))
        assertFalse(AppAccess.hasInternet(presets, fixed, app(AppGroup.GAMES), "everyday"))
    }
}
