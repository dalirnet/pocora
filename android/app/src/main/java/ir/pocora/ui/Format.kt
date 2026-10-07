package ir.pocora.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import ir.pocora.R
import ir.pocora.model.EventKind
import ir.pocora.model.IranianDate
import ir.pocora.model.RulePart
import ir.pocora.model.Week
import ir.pocora.model.localDateOf
import ir.pocora.preset.Names
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

// Numbers, times, sizes and dates as the app writes them: Persian digits in Persian, Latin ones in English,
// and the Iranian calendar in both. Every text comes from the string resources of the context's language.
class Format(
    private val context: Context,
) {
    companion object {
        private const val MEGABYTE = 1_000_000L
        private const val GIGABYTE = 1_000_000_000L
        private const val TEN_GIGABYTES = 10 * GIGABYTE
        private const val MINUTE_MILLISECONDS = 60_000L
        private const val MINUTES_PER_HOUR = 60L

        private val MONTHS =
            intArrayOf(
                R.string.month_1,
                R.string.month_2,
                R.string.month_3,
                R.string.month_4,
                R.string.month_5,
                R.string.month_6,
                R.string.month_7,
                R.string.month_8,
                R.string.month_9,
                R.string.month_10,
                R.string.month_11,
                R.string.month_12,
            )
        private val DAYS =
            intArrayOf(
                R.string.day_0,
                R.string.day_1,
                R.string.day_2,
                R.string.day_3,
                R.string.day_4,
                R.string.day_5,
                R.string.day_6,
            )
        private val DATE_DAYS =
            intArrayOf(
                R.string.day_date_0,
                R.string.day_date_1,
                R.string.day_date_2,
                R.string.day_date_3,
                R.string.day_date_4,
                R.string.day_date_5,
                R.string.day_date_6,
            )
        private val SHORT_DAYS =
            intArrayOf(
                R.string.day_short_0,
                R.string.day_short_1,
                R.string.day_short_2,
                R.string.day_short_3,
                R.string.day_short_4,
                R.string.day_short_5,
                R.string.day_short_6,
            )
    }

    val locale: Locale
        get() = context.resources.configuration.locales[0]

    fun number(value: Number): String = String.format(locale, "%d", value.toLong())

    // One decimal place with "." as the mark in every language, and none when it is zero: "1.5", "5".
    fun decimal(value: Double): String {
        val zero = DecimalFormatSymbols.getInstance(locale).zeroDigit
        return String
            .format(Locale.ROOT, "%.1f", value)
            .removeSuffix(".0")
            .map { if (it.isDigit()) zero + (it - '0') else it }
            .joinToString("")
    }

    fun time(time: LocalTime): String = String.format(locale, "%02d:%02d", time.hour, time.minute)

    fun time(time: LocalDateTime): String = time(time.toLocalTime())

    fun time(epochMilliseconds: Long): String = time(dateTime(epochMilliseconds))

    fun dateTime(epochMilliseconds: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilliseconds), ZoneId.systemDefault())

    // A mark index as the time it starts: 35 is 17:30. 48 is the end of the day, 24:00.
    fun mark(mark: Int): String = String.format(locale, "%02d:%02d", mark / 2, mark % 2 * 30)

    // "2h 10m", "40m", "3h". Never seconds, and nothing is a word, not "0m".
    fun duration(milliseconds: Long): String {
        val minutes = milliseconds / MINUTE_MILLISECONDS
        val hours = minutes / MINUTES_PER_HOUR
        val rest = minutes % MINUTES_PER_HOUR
        return when {
            minutes == 0L -> context.getString(R.string.none)
            hours == 0L -> context.getString(R.string.duration_minutes, rest)
            rest == 0L -> context.getString(R.string.duration_hours, hours)
            else -> context.getString(R.string.duration_hours_minutes, hours, rest)
        }
    }

    fun durationOfMarks(marks: Int): String = duration(marks * 30 * MINUTE_MILLISECONDS)

    // A length in half hours, kept short for a button: "30m", "1h", "1.5h".
    fun shortDuration(marks: Int): String =
        when {
            marks < 2 -> context.getString(R.string.duration_minutes, marks * 30)
            marks % 2 == 0 -> context.getString(R.string.duration_hours, marks / 2)
            else -> context.getString(R.string.duration_hours_short, decimal(marks / 2.0))
        }

    // "62 MB", "2.1 GB", "26 GB". Nothing is a word, not "0 MB".
    fun size(bytes: Long): String =
        when {
            bytes <= 0 -> {
                context.getString(R.string.none)
            }

            bytes < MEGABYTE -> {
                context.getString(R.string.size_under_megabyte)
            }

            bytes < GIGABYTE -> {
                context.getString(R.string.size_megabytes, number(bytes / MEGABYTE))
            }

            bytes < TEN_GIGABYTES -> {
                context.getString(
                    R.string.size_gigabytes,
                    decimal(bytes.toDouble() / GIGABYTE),
                )
            }

            else -> {
                context.getString(R.string.size_gigabytes, number(bytes / GIGABYTE))
            }
        }

    // Just the number, for "62 of 100 MB".
    fun megabytes(bytes: Long): String = number(bytes / MEGABYTE)

    // "2h on screen". Null for none, so the line can leave it out.
    fun screenTime(milliseconds: Long): String? =
        milliseconds.takeIf { it > 0 }?.let { context.getString(R.string.screen_time_amount, duration(it)) }

    // "2h on screen, 62 MB": only what there is. Null when nothing was used.
    fun use(
        screenMilliseconds: Long,
        bytes: Long,
    ): String? = listed(screenTime(screenMilliseconds), bytes.takeIf { it > 0 }?.let { size(it) })

    // "3 apps", or that there are none. Never "0 apps".
    fun appsCount(count: Int): String =
        if (count == 0) context.getString(R.string.no_apps) else context.getString(R.string.apps_count, number(count))

    // The parts there are, in one line: "2h on screen, 3 new alerts". Null when there is none.
    fun listed(vararg parts: String?): String? =
        parts.filterNotNull().takeIf { it.isNotEmpty() }?.joinToString(context.getString(R.string.list_separator))

    private fun monthName(month: Int): String = context.getString(MONTHS[month - 1])

    fun dayName(weekday: Int): String = context.getString(DAYS[weekday])

    fun shortDayName(weekday: Int): String = context.getString(SHORT_DAYS[weekday])

    // "11 Mehr".
    private fun dayAndMonth(date: LocalDate): String = dayAndMonth(IranianDate.of(date))

    private fun dayAndMonth(date: IranianDate): String =
        context.getString(R.string.date_day_month, number(date.day), monthName(date.month))

    // "Sat 11 Mehr".
    fun date(date: LocalDate): String =
        context.getString(
            R.string.date_weekday_day_month,
            context.getString(DATE_DAYS[Week.weekdayOf(date)]),
            dayAndMonth(date),
        )

    fun date(epochMilliseconds: Long): String = date(localDateOf(epochMilliseconds))

    // "5 - 11 Mehr", or "28 Shahrivar - 3 Mehr" across months.
    fun range(
        first: LocalDate,
        last: LocalDate,
    ): String {
        val from = IranianDate.of(first)
        val to = IranianDate.of(last)
        return if (from.month == to.month) {
            context.getString(R.string.date_range_same_month, number(from.day), number(to.day), monthName(to.month))
        } else {
            context.getString(R.string.date_range, dayAndMonth(first), dayAndMonth(last))
        }
    }

    fun today(): String = context.getString(R.string.today)

    // "today", or "Sat 11 Mehr" for any other day.
    fun dayName(date: LocalDate): String = if (date == LocalDate.now()) today() else date(date)

    fun dayAndTime(
        day: String,
        time: String,
    ): String = context.getString(R.string.day_and_time, day, time)

    // "today 14:30" or "Sat 11 Mehr 14:30".
    fun dayAndTime(epochMilliseconds: Long): String =
        dayAndTime(dayName(localDateOf(epochMilliseconds)), time(epochMilliseconds))

    // "Mehr 1 to 30": the season text of a preset, from month-day pairs.
    fun season(
        from: String,
        to: String,
    ): String =
        context.getString(
            R.string.date_range,
            dayAndMonth(IranianDate.parse(from, 0)),
            dayAndMonth(IranianDate.parse(to, 0)),
        )
}

// A preset's name in the app's language.
@Composable
fun Names.text(): String = of(LocalConfiguration.current.locales[0].language)

@Composable
fun rememberFormat(): Format = Format(LocalContext.current)

// The words for things the code names, shared by the screens and the notifications.
object Labels {
    fun event(kind: EventKind): Int =
        when (kind) {
            EventKind.POCORA_STOPPED -> R.string.event_pocora_stopped
            EventKind.VPN_OFF -> R.string.event_pocora_off
            EventKind.OTHER_VPN -> R.string.event_other_vpn
            EventKind.VPN_APP_INSTALLED -> R.string.event_vpn_app_installed
            EventKind.DEVICE_ADMIN_OFF -> R.string.event_device_admin_off
            EventKind.WATCHED_APP -> R.string.event_watched_app
            EventKind.WIFI_KEPT_OFF -> R.string.event_wifi_kept_off
            EventKind.BLUETOOTH_OFF -> R.string.event_bluetooth_off
            EventKind.LOCATION_OFF -> R.string.event_location_off
            EventKind.NOTIFICATIONS_OFF -> R.string.event_notifications_off
            EventKind.REBOOT -> R.string.event_reboot
            EventKind.APP_INSTALLED -> R.string.event_app_installed
            EventKind.APP_REMOVED -> R.string.event_app_removed
            EventKind.REQUEST_IGNORED -> R.string.event_request_ignored
            EventKind.PAIRED -> R.string.event_paired
            EventKind.PARENT_ADDED -> R.string.event_parent_added
            EventKind.PAUSED -> R.string.event_paused
            EventKind.RULES_CHANGED -> R.string.event_rules_changed
            EventKind.NO_CONTACT -> R.string.event_no_contact
            EventKind.QUOTA_USED -> R.string.event_quota_used
        }

    fun part(part: RulePart): Int =
        when (part) {
            RulePart.TIMES -> R.string.part_times
            RulePart.APPS -> R.string.part_apps
            RulePart.QUOTA -> R.string.part_quota
            RulePart.EXTRA_DATA -> R.string.part_extra_data
            RulePart.WATCH -> R.string.part_watch
        }
}
