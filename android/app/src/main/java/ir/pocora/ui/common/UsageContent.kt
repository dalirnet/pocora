package ir.pocora.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.pocora.R
import ir.pocora.model.AppDay
import ir.pocora.model.Contact
import ir.pocora.model.DayRecord
import ir.pocora.model.Event
import ir.pocora.model.InstalledApp
import ir.pocora.model.Mark
import ir.pocora.model.Schedule
import ir.pocora.model.Snapshot
import ir.pocora.model.Week
import ir.pocora.model.alerts
import ir.pocora.model.localDateOf
import ir.pocora.preset.AppGroup
import ir.pocora.preset.Presets
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.Format
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.AppRow
import ir.pocora.ui.component.Bar
import ir.pocora.ui.component.BarChart
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.CardTitle
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.HourAxis
import ir.pocora.ui.component.NumberPair
import ir.pocora.ui.component.PeriodStepper
import ir.pocora.ui.component.Segmented
import ir.pocora.ui.rememberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val DAY = 0

// Less than both of these is noise, as an app that only synced in the background. It still counts in the totals.
private const val LEAST_SCREEN_MILLISECONDS = 60_000L
private const val LEAST_BYTES = 1_000_000L

// Closer to the day before than this is about the same.
private const val SAME_SCREEN_MILLISECONDS = 5 * 60_000L

// Usage in both apps. Day or week: the two totals, the data over time against the limit, then the apps used.
// The contacts are the parent's record of when the child's phone was in touch; the child app has none.
@Composable
fun UsageContent(
    snapshot: Snapshot,
    presets: Presets,
    contacts: List<Contact> = emptyList(),
) {
    val format = rememberFormat()
    val today = LocalDate.now()
    var period by rememberSaveable { mutableStateOf(DAY) }
    var shown by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val isDay = period == DAY
    val date = LocalDate.ofEpochDay(shown)
    val days =
        if (isDay) {
            listOf(date)
        } else {
            Week.startOf(date).let { start ->
                (0L until Week.DAYS).map { start.plusDays(it) }
            }
        }
    val epochDays = days.map { it.toEpochDay() }.toSet()
    val allUsage = snapshot.childUsage()
    val usage = allUsage.filter { it.date in epochDays }
    val records = snapshot.days.filter { it.date in epochDays }
    val alerts = snapshot.events.alerts().filter { dayOf(it.start) in epochDays }

    PeriodHeader(format, isDay, date, today, onPeriod = { period = it }, onShow = { shown = it.toEpochDay() })

    val screen = usage.sumOf { it.screenMilliseconds }
    val bytes = records.sumOf { it.totalBytes }
    val (screenNote, bytesNote) =
        if (isDay) {
            dayNotes(format, snapshot.days, allUsage, date, today, screen, bytes)
        } else {
            weekNotes(format, snapshot.days, allUsage, days, today)
        }
    Card {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            NumberPair(value = format.duration(screen), label = stringResource(R.string.on_screen), note = screenNote)
            NumberPair(value = format.size(bytes), label = stringResource(R.string.data_used), note = bytesNote)
        }
    }

    Card {
        Text(
            text = stringResource(if (isDay) R.string.data_each_half_hour else R.string.data_each_day),
            color = LocalPalette.current.text,
            fontSize = Dimens.body,
            fontWeight = FontWeight.Bold,
        )
        if (isDay) {
            DayChart(format, snapshot, presets, date, records.firstOrNull(), alerts)
            NoContactLine(format, contacts, date)
        } else {
            WeekChart(format, days, today, records, alerts)
        }
    }

    AppRows(format, snapshot.apps, usage, bytes)
}

// Day or week, and the one shown. Days go back as far as the record is kept, never past today.
@Composable
private fun PeriodHeader(
    format: Format,
    isDay: Boolean,
    date: LocalDate,
    today: LocalDate,
    onPeriod: (Int) -> Unit,
    onShow: (LocalDate) -> Unit,
) {
    val oldest = today.minusDays(Snapshot.DAYS_KEPT - 1L)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Segmented(
            options = listOf(stringResource(R.string.period_day), stringResource(R.string.period_week)),
            selected = if (isDay) DAY else 1,
            onSelect = onPeriod,
            modifier = Modifier.width(150.dp),
        )
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            if (isDay) {
                PeriodStepper(
                    text = format.dayName(date),
                    onPrevious = { onShow(date.minusDays(1)) },
                    onNext = { onShow(date.plusDays(1)) },
                    hasPrevious = date.isAfter(oldest),
                    hasNext = date.isBefore(today),
                )
            } else {
                val start = Week.startOf(date)
                val next = start.plusDays(Week.DAYS.toLong())
                PeriodStepper(
                    text = format.range(start, next.minusDays(1)),
                    onPrevious = { onShow(start.minusDays(1)) },
                    onNext = { onShow(minOf(next, today)) },
                    hasPrevious = start.isAfter(oldest),
                    hasNext = next <= today,
                )
            }
        }
    }
}

// Under the day's totals: yesterday's, while today is still running, else the change from the day before.
// Nothing when the day before has no record, as before pairing.
@Composable
private fun dayNotes(
    format: Format,
    records: List<DayRecord>,
    usage: List<AppDay>,
    date: LocalDate,
    today: LocalDate,
    screen: Long,
    bytes: Long,
): Pair<String?, String?> {
    val before = date.minusDays(1).toEpochDay()
    val record = records.firstOrNull { it.date == before } ?: return null to null
    val beforeScreen = usage.filter { it.date == before }.sumOf { it.screenMilliseconds }
    if (date == today) {
        return stringResource(R.string.yesterday_was, format.duration(beforeScreen)) to
            stringResource(R.string.yesterday_was, format.size(record.totalBytes))
    }
    return change(screen, beforeScreen, SAME_SCREEN_MILLISECONDS) { format.duration(it) } to
        change(bytes, record.totalBytes, LEAST_BYTES) { format.size(it) }
}

@Composable
private fun change(
    value: Long,
    before: Long,
    least: Long,
    text: (Long) -> String,
): String =
    when {
        value - before >= least -> stringResource(R.string.more_than_day_before, text(value - before))
        before - value >= least -> stringResource(R.string.less_than_day_before, text(before - value))
        else -> stringResource(R.string.same_as_day_before)
    }

// Under the week's totals: the average of its whole days with a record. Today is left out, as it is not over.
@Composable
private fun weekNotes(
    format: Format,
    records: List<DayRecord>,
    usage: List<AppDay>,
    days: List<LocalDate>,
    today: LocalDate,
): Pair<String?, String?> {
    val whole = days.filter { it.isBefore(today) }.map { it.toEpochDay() }.toSet()
    val counted = records.filter { it.date in whole }
    // One day's average is that day.
    if (counted.size < 2) return null to null
    val dates = counted.map { it.date }.toSet()
    val screen = usage.filter { it.date in dates }.sumOf { it.screenMilliseconds }
    return stringResource(R.string.average_a_day, format.duration(screen / counted.size)) to
        stringResource(R.string.average_a_day, format.size(counted.sumOf { it.totalBytes } / counted.size))
}

// Each half hour of the day: Allowed or Limited as it really ran, or as planned when there is no record yet.
@Composable
private fun DayChart(
    format: Format,
    snapshot: Snapshot,
    presets: Presets,
    date: LocalDate,
    record: DayRecord?,
    alerts: List<Event>,
) {
    val schedule = remember(presets) { Schedule(presets) }
    val plan = schedule.day(snapshot.rules, date)
    val perMark = presets.quota(snapshot.rules.quota).bytesPerMark
    val bars =
        (0 until Mark.MARKS_PER_DAY).map { mark ->
            Bar(
                value = record?.bytes?.getOrNull(mark) ?: 0,
                allowed = record?.isAllowed(mark) ?: plan.isAllowed(mark),
                alert = alerts.any { markOf(it.start) == mark },
            )
        }
    if (perMark != null) {
        Text(
            text = stringResource(R.string.limit_line, format.size(perMark)),
            color = LocalPalette.current.muted,
            fontSize = Dimens.label,
        )
    }
    // Half hours run left to right in both languages, as the day strip does.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BarChart(bars = bars, labels = emptyList(), line = perMark)
    }
    HourAxis()
}

// Each day of the week. Days still to come are pale tracks.
@Composable
private fun WeekChart(
    format: Format,
    days: List<LocalDate>,
    today: LocalDate,
    records: List<DayRecord>,
    alerts: List<Event>,
) {
    val bars =
        days.map { day ->
            Bar(
                value = records.firstOrNull { it.date == day.toEpochDay() }?.totalBytes ?: 0,
                alert = alerts.any { dayOf(it.start) == day.toEpochDay() },
                noData = day.isAfter(today),
            )
        }
    BarChart(bars = bars, labels = (0 until Week.DAYS).map { format.shortDayName(it) })
}

private class AppTotal(
    val `package`: String,
    val bytes: Long,
    val screenMilliseconds: Long,
)

// The apps used in the period, most time first, leaving out the ones barely used.
// The total also counts background apps and Android itself. Their share is one last row, so the list adds up.
@Composable
private fun AppRows(
    format: Format,
    apps: List<InstalledApp>,
    usage: List<AppDay>,
    totalBytes: Long,
) {
    val palette = LocalPalette.current
    val byApp =
        usage
            .groupBy { it.`package` }
            .map { (packageName, days) ->
                AppTotal(packageName, days.sumOf { it.bytes }, days.sumOf { it.screenMilliseconds })
            }.filter { it.bytes >= LEAST_BYTES || it.screenMilliseconds >= LEAST_SCREEN_MILLISECONDS }
            .sortedWith(compareByDescending<AppTotal> { it.screenMilliseconds }.thenByDescending { it.bytes })
    val others = totalBytes - byApp.sumOf { it.bytes }
    Card {
        CardTitle(stringResource(R.string.apps_used))
        if (byApp.isEmpty() && others < LEAST_BYTES) {
            EmptyState(
                AppIcons.Apps,
                palette.muted,
                stringResource(R.string.nothing_used_title),
                stringResource(R.string.no_use),
                compact = true,
            )
            return@Card
        }
        val topTime = maxOf(byApp.maxOfOrNull { it.screenMilliseconds } ?: 0L, 1L)
        val topBytes = maxOf(byApp.maxOfOrNull { it.bytes } ?: 0L, others, 1L)
        for (total in byApp) {
            val app = apps.firstOrNull { it.`package` == total.`package` }
            val screen = total.screenMilliseconds
            val data = if (total.bytes > 0) format.size(total.bytes) else stringResource(R.string.without_internet)
            AppRow(
                name = app?.name ?: total.`package`,
                group = app?.group ?: AppGroup.OTHER,
                share = if (screen > 0) screen.toFloat() / topTime else total.bytes.toFloat() / topBytes,
                top = if (screen > 0) format.duration(screen) else format.size(total.bytes),
                bottom = if (screen > 0) data else null,
            )
        }
        if (others >= LEAST_BYTES) {
            AppRow(
                name = stringResource(R.string.other_apps),
                group = AppGroup.OTHER,
                share = others.toFloat() / topBytes,
                top = format.size(others),
                bottom = null,
            )
        }
    }
}

private fun dayOf(time: Long): Long = localDateOf(time).toEpochDay()

private fun markOf(time: Long): Int {
    val local = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    return Mark.of(local.hour, local.minute)
}
