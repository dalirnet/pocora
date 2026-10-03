package ir.pocora.model

import ir.pocora.preset.AppGroup
import ir.pocora.preset.TestPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAccessTest {
    private val presets = TestPresets.presets
    private val rules = Rules(schedule = "school-morning", appsList = "everyday", quota = "medium")

    private val shad = InstalledApp("ir.medu.shad", "Shad", "1.0", AppGroup.SCHOOL)

    private fun app(
        group: String,
        system: Boolean = false,
        vpn: Boolean = false,
    ) = InstalledApp("com.example.app", "Example", "1.0", group, system, vpn)

    // --- By list ---

    @Test
    fun groupInList() {
        assertTrue(AppAccess.hasInternet(presets, rules, app(AppGroup.GAMES), "everyday", allowed = true))
    }

    @Test
    fun groupNotInList() {
        assertFalse(AppAccess.hasInternet(presets, rules, app(AppGroup.SOCIAL), "everyday", allowed = true))
    }

    @Test
    fun blockList() {
        assertFalse(AppAccess.hasInternet(presets, rules, app(AppGroup.GAMES), "study", allowed = true))
    }

    // --- Always and never ---

    @Test
    fun systemAlways() {
        assertTrue(AppAccess.hasInternet(presets, rules, app(AppGroup.OTHER, system = true), "school", allowed = true))
    }

    @Test
    fun vpnNever() {
        val fixed = rules.copy(apps = mapOf("com.example.app" to AppChoice.IN))
        assertFalse(
            AppAccess.hasInternet(presets, fixed, app(AppGroup.OTHER, vpn = true), "everything", allowed = true),
        )
    }

    // --- Fixed by the parent ---

    @Test
    fun fixedIn() {
        val fixed = rules.copy(apps = mapOf("com.example.app" to AppChoice.IN))
        assertTrue(AppAccess.hasInternet(presets, fixed, app(AppGroup.BROWSER), "everyday", allowed = true))
    }

    @Test
    fun fixedOut() {
        val fixed = rules.copy(apps = mapOf("com.example.app" to AppChoice.OUT))
        assertFalse(AppAccess.hasInternet(presets, fixed, app(AppGroup.GAMES), "everyday", allowed = true))
    }

    // --- In a Limited mark, or once the data ran out ---

    @Test
    fun limitedCutsTheList() {
        assertFalse(AppAccess.hasInternet(presets, rules, app(AppGroup.GAMES), "everyday", allowed = false))
        assertFalse(
            AppAccess.hasInternet(presets, rules, app(AppGroup.OTHER, system = true), "school", allowed = false),
        )
    }

    @Test
    fun limitedKeepsFixedIn() {
        val fixed = rules.copy(apps = mapOf("com.example.app" to AppChoice.IN))
        assertTrue(AppAccess.hasInternet(presets, fixed, app(AppGroup.GAMES), "study", allowed = false))
    }

    // --- Always by the presets ---

    @Test
    fun presetAlways() {
        assertTrue(AppAccess.hasInternet(presets, rules, shad, "study", allowed = false))
        assertEquals(AppChoice.IN, AppAccess.choiceOf(presets, rules, shad.`package`))
    }

    @Test
    fun presetAlwaysBeatenByNever() {
        val fixed = rules.copy(apps = mapOf(shad.`package` to AppChoice.OUT))
        assertFalse(AppAccess.hasInternet(presets, fixed, shad, "everything", allowed = true))
    }
}
