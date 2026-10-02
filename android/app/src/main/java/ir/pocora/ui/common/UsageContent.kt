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
import ir.pocora.model.Mark
import ir.pocora.model.Schedule
import ir.pocora.model.Snapshot
import ir.pocora.model.Week
import ir.pocora.parent.ContactLog
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
import ir.pocora.ui.localDateOf
import ir.pocora.ui.rememberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val DAY = 0
private const val DAY_MILLISECONDS = 86_400_000L

// Less than both of these is noise, as an app that only synced in the background. It still counts in the totals.
private const val LEAST_SCREEN_MILLISECONDS = 60_000L
private const val LEAST_BYTES = 1_000_000L

// Usage in both apps. Day or week: the two numbers, the data over time against the limit, then the apps used.
// The contacts are the parent's record of when the child's phone was in touch; the child app has none.
@Composable
fun UsageContent(
    snapshot: Snapshot,
    presets: Presets,
    contacts: List<Contact> = emptyList(),
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val schedule = remember { Schedule(presets) }
    val today = LocalDate.now()
    val oldest = today.minusDays(Snapshot.DAYS_KEPT - 1L)
    var period by rememberSaveable { mutableStateOf(DAY) }
    var shown by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(shown)
    val perMark = presets.quota(snapshot.rules.quota).bytesPerMark
    val alerts = snapshot.events.filter { it.kind.alert }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Segmented(
            options = listOf(stringResource(R.string.period_day), stringResource(R.string.period_week)),
            selected = period,
            onSelect = { period = it },
            modifier = Modifier.width(150.dp),
        )
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            if (period == DAY) {
                PeriodStepper(
                    text = format.dayName(date),
                    onPrevious = { shown-- },
                    onNext = { shown++ },
                    hasPrevious = date.isAfter(oldest),
                    hasNext = date.isBefore(today),
                )
            } else {
                val start = Week.startOf(date)
                PeriodStepper(
                    text = format.range(start, start.plusDays(6)),
                    onPrevious = { shown = start.minusDays(1).toEpochDay() },
                    onNext = { shown = minOf(start.plusDays(7).toEpochDay(), today.toEpochDay()) },
                    hasPrevious = start.isAfter(oldest),
                    hasNext = start.plusDays(7) <= today,
                )
            }
        }
    }

    val days: List<LocalDate> =
        if (period ==
            DAY
        ) {
            listOf(date)
        } else {
            (0L until Week.DAYS).map { Week.startOf(date).plusDays(it) }
        }
    val epochDays = days.map { it.toEpochDay() }.toSet()
    val usage = snapshot.childUsage().filter { it.date in epochDays }
    val records = snapshot.days.filter { it.date in epochDays }
    val periodAlerts = alerts.filter { dayOf(it.start) in epochDays }

    Card {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            NumberPair(
                value = format.duration(usage.sumOf { it.screenMilliseconds }),
                label = stringResource(R.string.on_screen),
            )
            NumberPair(value = format.size(records.sumOf { it.totalBytes }), label = stringResource(R.string.data_used))
        }
    }

    Card {
        Text(
            text = stringResource(if (period == DAY) R.string.data_each_half_hour else R.string.data_each_day),
            color = palette.text,
            fontSize = Dimens.body,
            fontWeight = FontWeight.Bold,
        )
        if (period == DAY) {
            val record = records.firstOrNull()
            val plan = schedule.day(snapshot.rules, date)
            val bars =
                (0 until Mark.MARKS_PER_DAY).map { mark ->
                    Bar(
                        value = record?.bytes?.getOrNull(mark) ?: 0,
                        allowed = record?.isAllowed(mark) ?: plan.isAllowed(mark),
                        alert = periodAlerts.any { markOf(it.start) == mark },
                    )
                }
            if (perMark != null) {
                Text(
                    text = stringResource(R.string.limit_line, format.size(perMark)),
                    color = palette.muted,
                    fontSize = Dimens.label,
                )
            }
            // Half hours run left to right in both languages, as the day strip does.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BarChart(bars = bars, labels = emptyList(), line = perMark)
            }
            HourAxis()
            for (gap in noContact(contacts, date)) {
                Text(
                    text = stringResource(R.string.no_contact_until, format.time(gap)),
                    color = palette.muted,
                    fontSize = Dimens.caption,
                )
            }
        } else {
            val bars =
                days.map { day ->
                    Bar(
                        value = records.firstOrNull { it.date == day.toEpochDay() }?.totalBytes ?: 0,
                        alert = periodAlerts.any { dayOf(it.start) == day.toEpochDay() },
                        noData = day.isAfter(today),
                    )
                }
            BarChart(bars = bars, labels = (0 until Week.DAYS).map { format.shortDayName(it) })
        }
    }

    AppRows(format, snapshot, usage)
}

// The apps used in the period, most time first, leaving out the ones barely used.
@Composable
private fun AppRows(
    format: Format,
    snapshot: Snapshot,
    usage: List<AppDay>,
) {
    val palette = LocalPalette.current
    val byApp =
        usage
            .groupBy { it.`package` }
            .map { (packageName, days) ->
                Triple(packageName, days.sumOf { it.bytes }, days.sumOf { it.screenMilliseconds })
            }.filter { it.second >= LEAST_BYTES || it.third >= LEAST_SCREEN_MILLISECONDS }
            .sortedWith(compareByDescending<Triple<String, Long, Long>> { it.third }.thenByDescending { it.second })
    Card {
        CardTitle(stringResource(R.string.apps_used))
        if (byApp.isEmpty()) {
            EmptyState(
                AppIcons.Apps,
                palette.muted,
                stringResource(R.string.nothing_used_title),
                stringResource(R.string.no_use),
                compact = true,
            )
            return@Card
        }
        val topTime = byApp.maxOf { maxOf(it.third, 1L) }
        val topBytes = byApp.maxOf { maxOf(it.second, 1L) }
        for ((packageName, bytes, screen) in byApp) {
            val app = snapshot.apps.firstOrNull { it.`package` == packageName }
            AppRow(
                name = app?.name ?: packageName,
                group = app?.group ?: AppGroup.OTHER,
                share = if (screen > 0) screen.toFloat() / topTime else bytes.toFloat() / topBytes,
                top = if (screen > 0) format.duration(screen) else format.size(bytes),
                bottom =
                    if (screen >
                        0
                    ) {
                        (if (bytes > 0) format.size(bytes) else stringResource(R.string.without_internet))
                    } else {
                        null
                    },
            )
        }
    }
}

private fun dayOf(time: Long): Long = localDateOf(time).toEpochDay()

private fun markOf(time: Long): Int {
    val local = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    return Mark.of(local.hour, local.minute)
}

// The ends of the gaps in contact on a day, as "Not connected until 14:30".
private fun noContact(
    contacts: List<Contact>,
    date: LocalDate,
): List<Long> {
    val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = start + DAY_MILLISECONDS
    return contacts
        .zipWithNext()
        .filter { (before, after) ->
            after.start in start until end &&
                after.start - before.end > ContactLog.GAP_MILLISECONDS
        }.map { it.second.start }
}
