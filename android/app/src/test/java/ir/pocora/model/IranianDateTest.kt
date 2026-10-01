package ir.pocora.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IranianDateTest {
    // --- From Gregorian ---

    @Test
    fun of_nowruz1405() {
        assertEquals(IranianDate(1405, 1, 1), IranianDate.of(LocalDate.of(2026, 3, 21)))
    }

    @Test
    fun of_firstOfMehr() {
        assertEquals(IranianDate(1405, 7, 1), IranianDate.of(LocalDate.of(2026, 9, 23)))
    }

    @Test
    fun of_lastDayOfYear() {
        assertEquals(IranianDate(1404, 12, 29), IranianDate.of(LocalDate.of(2026, 3, 20)))
    }

    @Test
    fun of_leapYearEnd() {
        // 1403 is a leap year: Esfand 30 is 20 March 2025.
        assertEquals(IranianDate(1403, 12, 30), IranianDate.of(LocalDate.of(2025, 3, 20)))
    }

    // --- To Gregorian ---

    @Test
    fun toLocalDate_roundTripsEveryDayOfFiveYears() {
        var date = LocalDate.of(2026, 3, 1)
        repeat(5 * 366) {
            assertEquals(date, IranianDate.of(date).toLocalDate())
            date = date.plusDays(1)
        }
    }

    @Test
    fun toLocalDate_endOfSecondHalf() {
        assertEquals(LocalDate.of(2026, 10, 22), IranianDate(1405, 7, 30).toLocalDate())
    }

    // --- Months ---

    @Test
    fun daysInMonth() {
        assertEquals(31, IranianDate.daysInMonth(1405, 1))
        assertEquals(30, IranianDate.daysInMonth(1405, 7))
        assertEquals(29, IranianDate.daysInMonth(1405, 12))
        assertEquals(30, IranianDate.daysInMonth(1403, 12))
    }

    @Test
    fun isLeapYear() {
        assertTrue(IranianDate.isLeapYear(1403))
        assertFalse(IranianDate.isLeapYear(1405))
    }

    // --- Text ---

    @Test
    fun parse_withAndWithoutYear() {
        assertEquals(IranianDate(1405, 11, 19), IranianDate.parse("1405-11-19"))
        assertEquals(IranianDate(1405, 7, 1), IranianDate.parse("07-01", 1405))
    }

    @Test
    fun monthDay() {
        assertEquals("07-09", IranianDate(1405, 7, 9).monthDay)
    }
}
