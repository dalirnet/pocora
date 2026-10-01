package ir.pocora.model

import java.time.LocalDate
import java.util.Locale

// A date in the Iranian (Solar Hijri) calendar, the only calendar the apps show.
// The conversion is the Borkowski algorithm, as in jalaali-js, so it runs anywhere and is unit tested.
data class IranianDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<IranianDate> {
    companion object {
        private val BREAKS =
            intArrayOf(
                -61,
                9,
                38,
                199,
                426,
                686,
                756,
                818,
                1111,
                1181,
                1210,
                1635,
                2060,
                2097,
                2192,
                2262,
                2324,
                2394,
                2456,
                3178,
            )
        private const val JULIAN_DAY_OF_EPOCH = 2_440_588L
        private const val FIRST_HALF_DAYS = 186
        private const val SECOND_HALF_LONG_MONTH = 30

        fun of(date: LocalDate): IranianDate = fromJulianDay(date.toEpochDay() + JULIAN_DAY_OF_EPOCH)

        // "1405-11-19" or, with a year given, "11-19".
        fun parse(
            text: String,
            year: Int? = null,
        ): IranianDate {
            val parts = text.split("-").map { it.toInt() }
            return if (parts.size ==
                3
            ) {
                IranianDate(parts[0], parts[1], parts[2])
            } else {
                IranianDate(year ?: 0, parts[0], parts[1])
            }
        }

        private fun fromJulianDay(julianDay: Long): IranianDate {
            val gregorianYear = LocalDate.ofEpochDay(julianDay - JULIAN_DAY_OF_EPOCH).year
            var year = gregorianYear - 621
            val calendar = calendar(year)
            val firstDay = gregorianToJulianDay(gregorianYear, 3, calendar.march)
            var offset = (julianDay - firstDay).toInt()
            if (offset >= 0) {
                if (offset < FIRST_HALF_DAYS) return IranianDate(year, 1 + offset / 31, offset % 31 + 1)
                offset -= FIRST_HALF_DAYS
            } else {
                year -= 1
                offset += 179
                if (calendar.leap == 1) offset += 1
            }
            return IranianDate(year, 7 + offset / SECOND_HALF_LONG_MONTH, offset % SECOND_HALF_LONG_MONTH + 1)
        }

        private class Calendar(
            val leap: Int,
            val gregorianYear: Int,
            val march: Int,
        )

        private fun calendar(year: Int): Calendar {
            val gregorianYear = year + 621
            var leapIranian = -14
            var previous = BREAKS[0]
            var jump = 0
            for (index in 1 until BREAKS.size) {
                val next = BREAKS[index]
                jump = next - previous
                if (year < next) break
                leapIranian += jump / 33 * 8 + (jump % 33) / 4
                previous = next
            }
            var since = year - previous
            leapIranian += since / 33 * 8 + (since % 33 + 3) / 4
            if (jump % 33 == 4 && jump - since == 4) leapIranian += 1
            val leapGregorian = gregorianYear / 4 - (gregorianYear / 100 + 1) * 3 / 4 - 150
            val march = 20 + leapIranian - leapGregorian
            if (jump - since < 6) since = since - jump + (jump + 4) / 33 * 33
            var leap = ((since + 1) % 33 - 1) % 4
            if (leap == -1) leap = 4
            return Calendar(leap, gregorianYear, march)
        }

        private fun gregorianToJulianDay(
            year: Int,
            month: Int,
            day: Int,
        ): Long = LocalDate.of(year, month, day).toEpochDay() + JULIAN_DAY_OF_EPOCH

        fun isLeapYear(year: Int): Boolean = calendar(year).leap == 0

        fun daysInMonth(
            year: Int,
            month: Int,
        ): Int =
            when {
                month <= 6 -> 31
                month <= 11 -> 30
                isLeapYear(year) -> 30
                else -> 29
            }
    }

    fun toLocalDate(): LocalDate {
        val calendar = calendar(year)
        val firstDay = gregorianToJulianDay(calendar.gregorianYear, 3, calendar.march)
        val offset = (month - 1) * 31 - month / 7 * (month - 7) + day - 1
        return LocalDate.ofEpochDay(firstDay + offset - JULIAN_DAY_OF_EPOCH)
    }

    // "07-01": the month and day, for comparing with the season of a preset.
    val monthDay: String
        get() = "%02d-%02d".format(Locale.ROOT, month, day)

    override fun compareTo(other: IranianDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    override fun toString(): String = "%04d-%02d-%02d".format(Locale.ROOT, year, month, day)
}
