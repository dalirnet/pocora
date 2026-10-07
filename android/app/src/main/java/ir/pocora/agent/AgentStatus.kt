package ir.pocora.agent

import ir.pocora.model.DayPlan
import ir.pocora.model.Difference
import ir.pocora.model.Rules
import java.time.LocalDateTime

// What the child app shows on its home screen and in the notification panel. All of it is worked out by the agent,
// on its own clock, so the screens show the day the agent is in even when the phone's clock is changed.
data class AgentStatus(
    val allowed: Boolean,
    val until: LocalDateTime?,
    val markBytes: Long,
    val bytesPerMark: Long?,
    val quotaReached: Boolean,
    val today: DayPlan,
    val lastParentContact: Long,
    // Where today differs from the schedule's own day: what a parent added or took away.
    val changes: List<Difference> = emptyList(),
    // The name the parent gave this child, once a parent has sent it.
    val childName: String? = null,
    // The rules in force, for the child's own view of the schedule.
    val rules: Rules? = null,
    // Paused by the parent on this same phone: nothing is limited.
    val paused: Boolean = false,
    // The parent's phone called and asked for Wi-Fi, and the two phones are not in touch yet.
    val wifiAsked: Boolean = false,
) {
    val hasRules: Boolean
        get() = rules != null

    companion object {
        val EMPTY = AgentStatus(false, null, 0, null, false, DayPlan(emptyList()), 0)
    }
}
