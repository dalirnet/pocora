package ir.pocora.model

import kotlinx.serialization.Serializable

// Everything the parent sets for one child. The child's phone holds the only copy that counts.
@Serializable
data class Rules(
    val schedule: String,
    val changes: List<Change> = emptyList(),
    val appsList: String,
    val apps: Map<String, AppChoice> = emptyMap(),
    val quota: String,
    val watch: List<String> = emptyList(),
    val extraData: List<ExtraData> = emptyList(),
    // When the parent last changed anything, in the parent's clock. The newest edit wins.
    val changedAt: Long = 0,
) {
    companion object {
        const val FIRST_WEEK_SCHEDULE = "watch-only"
    }

    // The schedule this week and every week: a new preset removes every change, of both kinds.
    fun withSchedule(id: String): Rules = copy(schedule = id, changes = emptyList())

    // Old this-week changes and extra data are dropped, so the lists never grow.
    fun withoutPastChanges(thisWeek: Long): Rules =
        copy(
            changes =
                changes.filter {
                    it.week == null ||
                        it.week >= thisWeek
                },
            extraData = extraData.filter { it.day >= thisWeek },
        )
}

// The parts of the rules a parent can change, as the Events list names them.
enum class RulePart {
    TIMES,
    APPS,
    QUOTA,
    EXTRA_DATA,
    WATCH,
    ;

    companion object {
        // What new rules change, past this-week changes left out on both sides. Nothing for the first rules.
        fun changed(
            old: Rules?,
            new: Rules,
            thisWeek: Long,
        ): List<RulePart> {
            if (old == null) return emptyList()
            val before = old.withoutPastChanges(thisWeek)
            val after = new.withoutPastChanges(thisWeek)
            return buildList {
                if (before.schedule != after.schedule || before.changes != after.changes) add(TIMES)
                if (before.appsList != after.appsList || before.apps != after.apps) add(APPS)
                if (before.quota != after.quota) add(QUOTA)
                if ((after.extraData - before.extraData.toSet()).isNotEmpty()) add(EXTRA_DATA)
                if (before.watch != after.watch) add(WATCH)
            }
        }
    }
}

// A custom block: one weekday's Allowed times, replacing the preset's.
// With a week it is for that week only, the epoch day of its Saturday. Without one it is for that weekday every week.
@Serializable
data class Change(
    val weekday: Int,
    val blocks: List<Block>,
    val week: Long? = null,
)

// One Allowed time in a day, from its first mark up to, not including, its end mark.
// It may carry its own apps list, such as School only for class hours.
@Serializable
data class Block(
    val start: Int,
    val end: Int,
    val appsList: String? = null,
)

// More data for one Allowed block of one day, on top of the quota: "100 MB more for now".
// The block's marks share it: once a mark has used its own quota, it draws on what is left.
@Serializable
data class ExtraData(
    // The epoch day.
    val day: Long,
    val start: Int,
    val end: Int,
    val megabytes: Int,
) {
    val bytes: Long
        get() = megabytes * BYTES_PER_MEGABYTE

    companion object {
        private const val BYTES_PER_MEGABYTE = 1_000_000L
    }
}

// A single app the parent fixed by hand. It beats the apps list.
enum class AppChoice {
    IN,
    OUT,
}
