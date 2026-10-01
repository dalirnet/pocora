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
    // When the parent last changed anything, in the parent's clock. The newest edit wins.
    val changedAt: Long = 0,
) {
    companion object {
        const val FIRST_WEEK_SCHEDULE = "watch-only"
    }

    // The schedule this week and every week: a new preset removes every change, of both kinds.
    fun withSchedule(id: String): Rules = copy(schedule = id, changes = emptyList())

    // Old this-week changes are dropped, so the list never grows.
    fun withoutPastChanges(thisWeek: Long): Rules =
        copy(
            changes =
                changes.filter {
                    it.week == null ||
                        it.week >= thisWeek
                },
        )
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

// A single app the parent fixed by hand. It beats the apps list.
enum class AppChoice {
    IN,
    OUT,
}
