package ir.pocora.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.pocora.R
import ir.pocora.model.Contact
import ir.pocora.model.Mark
import ir.pocora.parent.ContactLog
import ir.pocora.ui.Dimens
import ir.pocora.ui.Format
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.localDateOf
import java.time.LocalDate
import java.time.ZoneId

private const val DAY_MILLISECONDS = 86_400_000L

// A gap in contact shorter than this is left out, and a return shorter than this joins the gaps around it.
private const val LEAST_GAP_MILLISECONDS = 30 * 60_000L
private const val SHORT_RETURN_MILLISECONDS = 10 * 60_000L

// When the child's phone was out of touch on a day, in one line at most.
// A single gap shows when it was, more show how often and how long in all.
@Composable
fun NoContactLine(
    format: Format,
    contacts: List<Contact>,
    date: LocalDate,
) {
    val gaps = gaps(contacts, date, System.currentTimeMillis())
    if (gaps.isEmpty()) return
    val gap = gaps.singleOrNull()
    val text =
        when {
            gap?.ongoing == true -> {
                stringResource(R.string.no_contact_since, format.time(gap.start))
            }

            gap != null -> {
                stringResource(R.string.no_contact_from_to, format.time(gap.start), endTime(format, gap.end))
            }

            else -> {
                stringResource(
                    R.string.no_contact_times,
                    format.number(gaps.size),
                    format.duration(gaps.sumOf { it.length }),
                )
            }
        }
    Text(text = text, color = LocalPalette.current.muted, fontSize = Dimens.caption)
}

private class Gap(
    val start: Long,
    val end: Long,
    val ongoing: Boolean,
) {
    val length: Long
        get() = end - start
}

// The end of the day reads 24:00, not 00:00.
private fun endTime(
    format: Format,
    time: Long,
): String = if (localDateOf(time - 1) != localDateOf(time)) format.mark(Mark.MARKS_PER_DAY) else format.time(time)

// The long gaps in contact on a day. A brief return between two gaps joins them, short ones are left out:
// a phone that drops off Wi-Fi for a few minutes is not news.
private fun gaps(
    contacts: List<Contact>,
    date: LocalDate,
    now: Long,
): List<Gap> {
    val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = start + DAY_MILLISECONDS
    val all =
        contacts.zipWithNext { before, after -> Gap(before.end, after.start, ongoing = false) } +
            listOfNotNull(contacts.lastOrNull()?.let { Gap(it.end, now, ongoing = true) })
    val merged = mutableListOf<Gap>()
    for (gap in all.filter { it.length > ContactLog.GAP_MILLISECONDS }) {
        val last = merged.lastOrNull()
        if (last != null && gap.start - last.end < SHORT_RETURN_MILLISECONDS) {
            merged[merged.lastIndex] = Gap(last.start, gap.end, gap.ongoing)
        } else {
            merged += gap
        }
    }
    return merged
        .map { Gap(maxOf(it.start, start), minOf(it.end, end), it.ongoing && it.end <= end) }
        .filter { it.length >= LEAST_GAP_MILLISECONDS }
}
