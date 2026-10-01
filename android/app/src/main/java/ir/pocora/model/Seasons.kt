package ir.pocora.model

import ir.pocora.preset.Holiday
import ir.pocora.preset.Period
import ir.pocora.preset.Presets
import ir.pocora.preset.SchedulePreset
import java.time.LocalDate

// Which schedule fits a date, and the official holidays. The app suggests; it never switches by itself.
class Seasons(
    private val presets: Presets,
) {
    companion object {
        const val NOTICE_DAYS = 3L
        private const val SCHOOL_YEAR = SchedulePreset.SECTION_SCHOOL_YEAR
        private const val RAMADAN = "ramadan"
        private const val HOLIDAYS = "holidays"
        private const val EXAMS = "exams"

        // Presets that already treat every day alike, so a holiday changes nothing.
        private val UNIFORM = setOf(HOLIDAYS, "summer", "watch-only", "nights-off", "one-hour")
    }

    // The presets that fit a date, best first. School gives both shifts.
    fun fitting(date: LocalDate): List<String> {
        val day = IranianDate.of(date)
        val inSeason =
            presets.schedules
                .filter { preset ->
                    preset.seasons.any { contains(it, day.monthDay) }
                }.map { it.id }
        val first = inSeason.firstOrNull { it == HOLIDAYS || it == EXAMS }
        if (first != null) return listOf(first)
        val school = inSeason.filter { presets.schedule(it).section == SCHOOL_YEAR }
        if (school.isNotEmpty() && isRamadan(day)) return listOf(RAMADAN)
        return inSeason
    }

    // The next period, when it starts within the notice days: "Exam season starts in 3 days".
    fun upcoming(today: LocalDate): Pair<List<String>, LocalDate>? {
        val now = fitting(today)
        for (offset in 1..NOTICE_DAYS) {
            val date = today.plusDays(offset)
            val next = fitting(date)
            if (next.isNotEmpty() && next != now) return next to date
        }
        return null
    }

    // The next period, unless the child's schedule already fits it.
    fun upcomingFor(
        rules: Rules,
        today: LocalDate,
    ): Pair<List<String>, LocalDate>? = upcoming(today)?.takeIf { rules.schedule !in it.first }

    fun holidayOn(date: LocalDate): Holiday? {
        val text = IranianDate.of(date).toString()
        return presets.holidays.firstOrNull { it.date == text }
    }

    // The day before a holiday: suggest giving it Friday's hours, unless it is a Friday or nothing would change.
    fun holidayTomorrow(
        rules: Rules,
        today: LocalDate,
    ): Pair<LocalDate, Holiday>? {
        val tomorrow = today.plusDays(1)
        val holiday = holidayOn(tomorrow) ?: return null
        if (Week.weekdayOf(tomorrow) == Week.FRIDAY || rules.schedule in UNIFORM) return null
        return tomorrow to holiday
    }

    private fun isRamadan(day: IranianDate): Boolean =
        presets.ramadan.any { day >= IranianDate.parse(it.from) && day <= IranianDate.parse(it.to) }

    // A season may run over the new year, as Esfand 25 to Farvardin 13.
    private fun contains(
        period: Period,
        monthDay: String,
    ): Boolean =
        if (period.from <=
            period.to
        ) {
            monthDay in period.from..period.to
        } else {
            monthDay >= period.from || monthDay <= period.to
        }
}
