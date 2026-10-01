package ir.pocora.model

import ir.pocora.preset.TestPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SeasonsTest {
    private val seasons = Seasons(TestPresets.presets)
    private val rules = Rules(schedule = "school-morning", appsList = "everyday", quota = "medium")

    // --- Fitting ---

    @Test
    fun fitting_schoolYearGivesBothShifts() {
        assertEquals(
            listOf("school-morning", "school-afternoon"),
            seasons.fitting(IranianDate(1405, 7, 9).toLocalDate()),
        )
    }

    @Test
    fun fitting_examsBeforeSchool() {
        assertEquals(listOf("exams"), seasons.fitting(IranianDate(1405, 10, 5).toLocalDate()))
    }

    @Test
    fun fitting_holidaysOverTheNewYear() {
        assertEquals(listOf("holidays"), seasons.fitting(IranianDate(1405, 12, 28).toLocalDate()))
        assertEquals(listOf("holidays"), seasons.fitting(IranianDate(1406, 1, 5).toLocalDate()))
    }

    @Test
    fun fitting_ramadanReplacesSchool() {
        assertEquals(listOf("ramadan"), seasons.fitting(IranianDate(1405, 11, 25).toLocalDate()))
    }

    @Test
    fun fitting_summer() {
        assertEquals(listOf("summer"), seasons.fitting(IranianDate(1405, 5, 1).toLocalDate()))
    }

    // --- Upcoming ---

    @Test
    fun upcoming_threeDaysBeforeExams() {
        val today = IranianDate(1405, 9, 28).toLocalDate()
        assertEquals(listOf("exams") to IranianDate(1405, 10, 1).toLocalDate(), seasons.upcoming(today))
    }

    @Test
    fun upcoming_nothingMidSeason() {
        assertNull(seasons.upcoming(IranianDate(1405, 8, 10).toLocalDate()))
    }

    // --- Holidays ---

    @Test
    fun holidayTomorrow_suggestsForAWeekday() {
        // Dey 2 1405, Birthday of Imam Ali, is a Wednesday.
        val today = IranianDate(1405, 10, 1).toLocalDate()
        assertEquals(IranianDate(1405, 10, 2).toLocalDate(), seasons.holidayTomorrow(rules, today)?.first)
    }

    @Test
    fun holidayTomorrow_noneOnAFriday() {
        // Aban 22 1405, Martyrdom of Fatimah, is a Friday.
        assertNull(seasons.holidayTomorrow(rules, IranianDate(1405, 8, 21).toLocalDate()))
    }

    @Test
    fun holidayTomorrow_noneWithAUniformPreset() {
        assertNull(seasons.holidayTomorrow(rules.withSchedule("summer"), IranianDate(1405, 10, 1).toLocalDate()))
    }

    @Test
    fun holidayOn_none() {
        assertNull(seasons.holidayOn(LocalDate.of(2026, 10, 1)))
    }
}
