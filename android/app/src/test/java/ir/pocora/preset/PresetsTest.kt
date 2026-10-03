package ir.pocora.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {
    private val presets = TestPresets.presets

    // --- Shape ---

    @Test
    fun schedules_haveSevenDays() {
        for (schedule in presets.schedules) assertEquals(schedule.id, 7, schedule.week.size)
    }

    @Test
    fun schedules_blocksInsideTheDay() {
        for (schedule in presets.schedules) {
            for (day in schedule.week) {
                for (block in day) {
                    assertTrue(
                        schedule.id,
                        block[0] in 0 until block[1] && block[1] <= 48,
                    )
                }
            }
        }
    }

    @Test
    fun appsLists_knownGroups() {
        val groups = presets.groups.map { it.id }.toSet()
        for (list in presets.appsLists) assertTrue(list.id, groups.containsAll(list.groups))
    }

    // --- Lookups ---

    @Test
    fun knownGroupOf_listedApp() {
        assertEquals(AppGroup.GAMES, presets.knownGroupOf("com.supercell.clashofclans"))
    }

    @Test
    fun knownGroupOf_unlistedApp() {
        assertNull(presets.knownGroupOf("com.example.unknown"))
    }

    @Test
    fun quota_bytesPerMark() {
        assertEquals(200_000_000L, presets.quota("medium").bytesPerMark)
        assertNull(presets.quota("no-limit").bytesPerMark)
    }

    @Test
    fun schedule_unknownIdFallsBack() {
        assertEquals(presets.schedules.first(), presets.schedule("no-such-preset"))
    }

    @Test
    fun fitsAge() {
        assertTrue(presets.appsList("everyday").fitsAge(11))
        assertTrue(!presets.appsList("everyday").fitsAge(13))
        assertTrue(!presets.appsList("everything").fitsAge(11))
    }
}
