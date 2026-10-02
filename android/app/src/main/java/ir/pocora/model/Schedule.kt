package ir.pocora.model

import ir.pocora.preset.Presets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

// The schedule's arithmetic: which marks are Allowed on a date, and the edits the parent makes.
// Every edit returns new rules. Nothing here reads a clock or a file.
class Schedule(
    private val presets: Presets,
) {
    companion object {
        private const val LOOK_AHEAD_DAYS = 8L
        private const val MONTH_DAYS = 30
    }

    // --- Reading ---

    fun presetDay(
        scheduleId: String,
        weekday: Int,
    ): DayPlan = DayPlan(presets.schedule(scheduleId).week[weekday].map { Block(it[0], it[1]) })

    fun day(
        rules: Rules,
        date: LocalDate,
    ): DayPlan = changeOn(rules, date)?.let { DayPlan(it.blocks) } ?: presetDay(rules.schedule, Week.weekdayOf(date))

    // The day without its this-week change: what "every week" means for this weekday.
    fun everyWeekDay(
        rules: Rules,
        weekday: Int,
    ): DayPlan =
        rules.changes.firstOrNull { it.week == null && it.weekday == weekday }?.let { DayPlan(it.blocks) }
            ?: presetDay(rules.schedule, weekday)

    // This week's change for the date first, then the weekday's every-week change.
    fun changeOn(
        rules: Rules,
        date: LocalDate,
    ): Change? {
        val weekday = Week.weekdayOf(date)
        val week = Week.startOf(date).toEpochDay()
        return rules.changes.firstOrNull { it.weekday == weekday && it.week == week }
            ?: rules.changes.firstOrNull { it.weekday == weekday && it.week == null }
    }

    fun differences(
        rules: Rules,
        date: LocalDate,
    ): List<Difference> = Difference.between(presetDay(rules.schedule, Week.weekdayOf(date)), day(rules, date))

    fun state(
        rules: Rules,
        now: LocalDateTime,
    ): ScheduleState {
        val today = day(rules, now.toLocalDate())
        val mark = markOf(now)
        val allowed = today.isAllowed(mark)
        val appsList = today.blockAt(mark)?.appsList ?: rules.appsList
        return ScheduleState(allowed, nextChange(rules, now, allowed), appsList)
    }

    // The first mark boundary after now where Allowed turns into Limited, or the other way.
    private fun nextChange(
        rules: Rules,
        now: LocalDateTime,
        allowed: Boolean,
    ): LocalDateTime? {
        val start = now.toLocalDate()
        var mark = markOf(now) + 1
        for (offset in 0 until LOOK_AHEAD_DAYS) {
            val date = start.plusDays(offset)
            val plan = day(rules, date)
            while (mark < Mark.MARKS_PER_DAY) {
                if (plan.isAllowed(mark) !=
                    allowed
                ) {
                    return date.atStartOfDay().plusMinutes(mark * Mark.DURATION_MINUTES.toLong())
                }
                mark++
            }
            mark = 0
        }
        return null
    }

    fun allowedMarksPerWeek(scheduleId: String): Int =
        (0 until Week.DAYS).sumOf { presetDay(scheduleId, it).allowedMarks }

    // The most a child can use in a 30-day month: the quota of every Allowed mark.
    fun monthlyCeilingBytes(
        scheduleId: String,
        bytesPerMark: Long?,
    ): Long? = bytesPerMark?.let { allowedMarksPerWeek(scheduleId).toLong() * MONTH_DAYS * it / Week.DAYS }

    // --- Editing ---

    // Sets a day's Allowed times, for this week only or for that weekday every week.
    fun setDay(
        rules: Rules,
        date: LocalDate,
        blocks: List<Block>,
        everyWeek: Boolean,
    ): Rules {
        val weekday = Week.weekdayOf(date)
        val week = Week.startOf(date).toEpochDay()
        val plan = DayPlan(blocks).normalized()
        // A change for every week also replaces this week's change of that weekday, or it would hide the new one.
        val kept =
            rules.changes.filterNot {
                it.weekday == weekday &&
                    (it.week == week || (everyWeek && it.week == null))
            }
        val base =
            if (everyWeek) {
                presetDay(
                    rules.schedule,
                    weekday,
                )
            } else {
                everyWeekDay(rules.copy(changes = kept), weekday)
            }
        val same = DayPlan(base.blocks).normalized() == plan
        val added = if (same) emptyList() else listOf(Change(weekday, plan, if (everyWeek) null else week))
        return rules.copy(changes = kept + added)
    }

    // Back to the preset: both the this-week and the every-week change of that weekday go.
    fun resetDay(
        rules: Rules,
        date: LocalDate,
    ): Rules {
        val weekday = Week.weekdayOf(date)
        val week = Week.startOf(date).toEpochDay()
        return rules.copy(
            changes =
                rules.changes.filterNot {
                    it.weekday == weekday &&
                        (it.week == null || it.week == week)
                },
        )
    }

    fun removeChange(
        rules: Rules,
        change: Change,
    ): Rules = rules.copy(changes = rules.changes - change)

    // Allowed over Limited, for this week: "+30m", "+1h", "Allowed now".
    fun addTime(
        rules: Rules,
        date: LocalDate,
        start: Int,
        marks: Int,
    ): Rules = paint(rules, date, start, marks, allowed = true)

    // Limited over Allowed, for this week: "Cut a break", "Limited now".
    fun cut(
        rules: Rules,
        date: LocalDate,
        start: Int,
        marks: Int,
    ): Rules = paint(rules, date, start, marks, allowed = false)

    private fun paint(
        rules: Rules,
        date: LocalDate,
        start: Int,
        marks: Int,
        allowed: Boolean,
    ): Rules {
        val old = day(rules, date)
        return setDay(rules, date, old.with(start, marks, allowed).blocks, everyWeek = false)
    }

    // One more half hour or hour today: after the Allowed time that is on now, or from now.
    fun extend(
        rules: Rules,
        now: LocalDateTime,
        marks: Int,
    ): Rules {
        val date = now.toLocalDate()
        val mark = markOf(now)
        val start = day(rules, date).blockAt(mark)?.end ?: mark
        return addTime(rules, date, start, marks)
    }

    // More data for the Allowed block on now. A second gift for the same block adds to the first.
    // Outside an Allowed block there is nothing to give data to, and the rules come back unchanged.
    fun addData(
        rules: Rules,
        now: LocalDateTime,
        megabytes: Int,
    ): Rules {
        val date = now.toLocalDate()
        val block = day(rules, date).blockAt(markOf(now)) ?: return rules
        val day = date.toEpochDay()
        val same = rules.extraData.firstOrNull { it.day == day && it.start == block.start && it.end == block.end }
        val total = (same?.megabytes ?: 0) + megabytes
        return rules.copy(
            extraData =
                rules.extraData - listOfNotNull(same) + ExtraData(day, block.start, block.end, total),
        )
    }

    // How much one mark may use: its quota, and what the block's extra data has left after the marks before it.
    fun markLimit(
        rules: Rules,
        date: LocalDate,
        mark: Int,
        bytesPerMark: Long,
        bytesOfMark: (Int) -> Long,
    ): Long {
        val extra =
            rules.extraData.firstOrNull { it.day == date.toEpochDay() && mark >= it.start && mark < it.end }
                ?: return bytesPerMark
        val usedBefore = (extra.start until mark).sumOf { maxOf(0L, bytesOfMark(it) - bytesPerMark) }
        return bytesPerMark + maxOf(0L, extra.bytes - usedBefore)
    }

    // A holiday in the week: give one day the hours of another, for this week.
    fun copyDay(
        rules: Rules,
        date: LocalDate,
        fromWeekday: Int,
    ): Rules {
        val source = Week.startOf(date).plusDays(fromWeekday.toLong())
        return setDay(rules, date, day(rules, source).blocks, everyWeek = false)
    }

    fun markOf(time: LocalDateTime): Int = Mark.of(time.hour, time.minute)
}

// Where the schedule stands at one moment. Until is when this state ends: the end of Allowed time,
// or the next Allowed time. It is null when nothing changes within a week.
data class ScheduleState(
    val allowed: Boolean,
    val until: LocalDateTime?,
    val appsList: String,
)

// A day as its 48 marks, true where Allowed. Built from the day's blocks.
class DayPlan(
    val blocks: List<Block>,
) {
    companion object {
        fun of(allowed: BooleanArray): DayPlan {
            val blocks = mutableListOf<Block>()
            var start = -1
            for (index in 0..Mark.MARKS_PER_DAY) {
                val on = index < Mark.MARKS_PER_DAY && allowed[index]
                if (on && start < 0) start = index
                if (!on && start >= 0) {
                    blocks += Block(start, index)
                    start = -1
                }
            }
            return DayPlan(blocks)
        }
    }

    val allowed: BooleanArray =
        BooleanArray(Mark.MARKS_PER_DAY).also { marks ->
            for (block in blocks) {
                for (index in block.start.coerceAtLeast(0) until
                    block.end.coerceAtMost(Mark.MARKS_PER_DAY)) {
                    marks[index] = true
                }
            }
        }

    val allowedMarks: Int
        get() = allowed.count { it }

    fun isAllowed(mark: Int): Boolean = allowed[mark]

    fun blockAt(mark: Int): Block? = blocks.firstOrNull { mark >= it.start && mark < it.end }

    // The day with marks start until start + count set to allowed. Times rebuilt from marks would lose their apps
    // lists, so each new block takes the list of the old block it starts in.
    fun with(
        start: Int,
        count: Int,
        allowed: Boolean,
    ): DayPlan {
        val marks = this.allowed.copyOf()
        for (index in start.coerceAtLeast(0) until (start + count).coerceAtMost(Mark.MARKS_PER_DAY)) {
            marks[index] =
                allowed
        }
        return DayPlan(of(marks).blocks.map { it.copy(appsList = blockAt(it.start)?.appsList) })
    }

    // The same times as one list per Allowed run, adjacent blocks with the same apps list joined.
    fun normalized(): List<Block> {
        val sorted = blocks.filter { it.end > it.start }.sortedBy { it.start }
        val joined = mutableListOf<Block>()
        for (block in sorted) {
            val last = joined.lastOrNull()
            if (last != null && block.start <= last.end && block.appsList == last.appsList) {
                joined[joined.lastIndex] = last.copy(end = maxOf(last.end, block.end))
            } else {
                joined += block
            }
        }
        return joined
    }
}

// One stretch where a changed day differs from its preset: Allowed added, or Allowed taken away.
data class Difference(
    val start: Int,
    val end: Int,
    val added: Boolean,
) {
    companion object {
        fun between(
            preset: DayPlan,
            changed: DayPlan,
        ): List<Difference> {
            val result = mutableListOf<Difference>()
            var start = -1
            var added = false
            for (index in 0..Mark.MARKS_PER_DAY) {
                val differs = index < Mark.MARKS_PER_DAY && preset.isAllowed(index) != changed.isAllowed(index)
                val kind = index < Mark.MARKS_PER_DAY && changed.isAllowed(index)
                if (start >= 0 && (!differs || kind != added)) {
                    result += Difference(start, index, added)
                    start = -1
                }
                if (differs && start < 0) {
                    start = index
                    added = kind
                }
            }
            return result
        }
    }
}

// A mark is one half hour of the day: 48 a day, numbered from midnight.
object Mark {
    const val DURATION_MINUTES = 30
    const val MARKS_PER_DAY = 48
    const val PER_HOUR = 60 / DURATION_MINUTES

    fun of(
        hour: Int,
        minute: Int,
    ): Int = hour * PER_HOUR + minute / DURATION_MINUTES
}

// The week starts on Saturday. Weekdays are numbered from it: Saturday 0 to Friday 6.
object Week {
    const val DAYS = 7
    const val SATURDAY = 0
    const val THURSDAY = 5
    const val FRIDAY = 6

    fun weekdayOf(date: LocalDate): Int = (date.dayOfWeek.value + 1) % DAYS

    // The Saturday that starts the week of this date.
    fun startOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
}
