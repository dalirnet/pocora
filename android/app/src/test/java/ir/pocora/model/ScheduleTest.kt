package ir.pocora.model

import ir.pocora.preset.TestPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleTest {
    private val schedule = Schedule(TestPresets.presets)
    private val rules = Rules(schedule = "school-morning", appsList = "everyday", quota = "medium")

    // Thursday 9 Mehr 1405, and the Monday of the same week.
    private val thursday = LocalDate.of(2026, 10, 1)
    private val monday = LocalDate.of(2026, 9, 28)

    // --- Reading ---

    @Test
    fun day_fromPreset() {
        assertEquals(listOf(Block(34, 41)), schedule.day(rules, monday).blocks)
    }

    @Test
    fun state_allowedUntilEndOfBlock() {
        val state = schedule.state(rules, monday.atTime(18, 10))
        assertTrue(state.allowed)
        assertEquals(monday.atTime(20, 30), state.until)
        assertEquals("everyday", state.appsList)
    }

    @Test
    fun state_limitedUntilNextBlock() {
        val state = schedule.state(rules, monday.atTime(9, 0))
        assertFalse(state.allowed)
        assertEquals(monday.atTime(17, 0), state.until)
    }

    @Test
    fun state_limitedAfterLastBlockLooksToTomorrow() {
        val state = schedule.state(rules, monday.atTime(22, 0))
        assertEquals(monday.plusDays(1).atTime(17, 0), state.until)
    }

    @Test
    fun state_allowedAllDayNeverEnds() {
        val state = schedule.state(rules.withSchedule("watch-only"), monday.atTime(10, 0))
        assertTrue(state.allowed)
        assertNull(state.until)
    }

    @Test
    fun state_blockAppsList() {
        val changed = schedule.setDay(rules, monday, listOf(Block(34, 41, "study")), everyWeek = false)
        assertEquals("study", schedule.state(changed, monday.atTime(17, 30)).appsList)
    }

    // --- Arithmetic ---

    @Test
    fun allowedMarksPerWeek_schoolMorning() {
        // 3h 30m on five days, 7h on Thursday, 6h on Friday: 30h 30m.
        assertEquals(61, schedule.allowedMarksPerWeek("school-morning"))
    }

    @Test
    fun monthlyCeilingBytes_mediumSchoolMorning() {
        // The preset files call it about 26 GB.
        assertEquals(26_142_857_142L, schedule.monthlyCeilingBytes("school-morning", 100_000_000L))
    }

    @Test
    fun monthlyCeilingBytes_noLimit() {
        assertNull(schedule.monthlyCeilingBytes("school-morning", null))
    }

    // --- Editing ---

    @Test
    fun setDay_thisWeekOnly() {
        val changed = schedule.setDay(rules, monday, listOf(Block(30, 41)), everyWeek = false)
        assertEquals(listOf(Block(30, 41)), schedule.day(changed, monday).blocks)
        assertEquals(listOf(Block(34, 41)), schedule.day(changed, monday.plusWeeks(1)).blocks)
    }

    @Test
    fun setDay_everyWeek() {
        val changed = schedule.setDay(rules, monday, listOf(Block(30, 41)), everyWeek = true)
        assertEquals(listOf(Block(30, 41)), schedule.day(changed, monday.plusWeeks(3)).blocks)
    }

    @Test
    fun setDay_everyWeekReplacesThisWeek() {
        val thisWeek = schedule.setDay(rules, monday, listOf(Block(20, 22)), everyWeek = false)
        val changed = schedule.setDay(thisWeek, monday, listOf(Block(30, 41)), everyWeek = true)
        assertEquals(listOf(Block(30, 41)), schedule.day(changed, monday).blocks)
        assertEquals(1, changed.changes.size)
    }

    @Test
    fun setDay_sameAsPresetLeavesNoChange() {
        val changed = schedule.setDay(rules, monday, listOf(Block(34, 41)), everyWeek = true)
        assertTrue(changed.changes.isEmpty())
    }

    @Test
    fun withSchedule_removesEveryChange() {
        val changed = schedule.setDay(rules, monday, listOf(Block(30, 41)), everyWeek = true)
        assertTrue(changed.withSchedule("summer").changes.isEmpty())
    }

    @Test
    fun resetDay() {
        val changed =
            schedule.setDay(
                schedule.setDay(rules, monday, listOf(Block(1, 2)), true),
                monday,
                listOf(Block(3, 4)),
                false,
            )
        assertEquals(listOf(Block(34, 41)), schedule.day(schedule.resetDay(changed, monday), monday).blocks)
    }

    @Test
    fun extend_afterTheBlockThatIsOn() {
        val changed = schedule.extend(rules, monday.atTime(18, 0), marks = 2)
        assertEquals(listOf(Block(34, 43)), schedule.day(changed, monday).blocks)
    }

    @Test
    fun extend_fromNowWhenLimited() {
        val changed = schedule.extend(rules, monday.atTime(10, 15), marks = 1)
        assertEquals(listOf(Block(20, 21), Block(34, 41)), schedule.day(changed, monday).blocks)
    }

    @Test
    fun cut_splitsABlock() {
        val changed = schedule.cut(rules, monday, start = 36, marks = 2)
        assertEquals(listOf(Block(34, 36), Block(38, 41)), schedule.day(changed, monday).blocks)
    }

    @Test
    fun cut_keepsTheBlockAppsList() {
        val listed = schedule.setDay(rules, monday, listOf(Block(34, 41, "study")), everyWeek = false)
        val changed = schedule.cut(listed, monday, start = 36, marks = 2)
        assertEquals(listOf(Block(34, 36, "study"), Block(38, 41, "study")), schedule.day(changed, monday).blocks)
    }

    @Test
    fun copyDay_fridayHours() {
        val changed = schedule.copyDay(rules, monday, Week.FRIDAY)
        assertEquals(listOf(Block(20, 24), Block(32, 40)), schedule.day(changed, monday).blocks)
    }

    @Test
    fun differences_addedAndRemoved() {
        val changed = schedule.setDay(rules, monday, listOf(Block(30, 36)), everyWeek = false)
        assertEquals(listOf(Difference(30, 34, true), Difference(36, 41, false)), schedule.differences(changed, monday))
    }

    @Test
    fun withoutPastChanges() {
        val changed = schedule.setDay(rules, monday, listOf(Block(30, 41)), everyWeek = false)
        val nextWeek = Week.startOf(thursday.plusWeeks(1)).toEpochDay()
        assertTrue(changed.withoutPastChanges(nextWeek).changes.isEmpty())
    }

    @Test
    fun markOf() {
        assertEquals(37, schedule.markOf(LocalDateTime.of(2026, 10, 1, 18, 45)))
    }

    // --- Extra data ---

    @Test
    fun addData_insideBlock() {
        val changed = schedule.addData(rules, monday.atTime(18, 10), 100)
        assertEquals(listOf(ExtraData(monday.toEpochDay(), 34, 41, 100)), changed.extraData)
    }

    @Test
    fun addData_twiceAddsUp() {
        val once = schedule.addData(rules, monday.atTime(18, 10), 100)
        val twice = schedule.addData(once, monday.atTime(19, 0), 250)
        assertEquals(listOf(ExtraData(monday.toEpochDay(), 34, 41, 350)), twice.extraData)
    }

    @Test
    fun addData_outsideBlockChangesNothing() {
        assertEquals(rules, schedule.addData(rules, monday.atTime(10, 0), 100))
    }

    @Test
    fun markLimit_withoutExtraIsQuota() {
        assertEquals(MEGABYTE * 100, schedule.markLimit(rules, monday, 35, MEGABYTE * 100) { 0L })
    }

    @Test
    fun markLimit_sharesExtraAcrossBlock() {
        val given = schedule.addData(rules, monday.atTime(17, 0), 150)
        // The first mark used 80 MB over its quota, so 70 MB of the extra is left.
        val limit = schedule.markLimit(given, monday, 35, MEGABYTE * 100) { MEGABYTE * 180 }
        assertEquals(MEGABYTE * 170, limit)
    }

    @Test
    fun markLimit_usedUpExtraIsQuota() {
        val given = schedule.addData(rules, monday.atTime(17, 0), 150)
        val limit = schedule.markLimit(given, monday, 36, MEGABYTE * 100) { MEGABYTE * 200 }
        assertEquals(MEGABYTE * 100, limit)
    }

    @Test
    fun withoutPastChanges_dropsOldExtraData() {
        val given = schedule.addData(rules, monday.atTime(18, 0), 100)
        val nextWeek = Week.startOf(monday.plusDays(7)).toEpochDay()
        assertTrue(given.withoutPastChanges(nextWeek).extraData.isEmpty())
    }

    private companion object {
        const val MEGABYTE = 1_000_000L
    }
}
