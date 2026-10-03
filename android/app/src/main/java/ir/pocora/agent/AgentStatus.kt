package ir.pocora.agent

import ir.pocora.model.DayPlan
import ir.pocora.model.Rules
import java.time.LocalDateTime

// What the child app shows on its home screen and in the notification panel.
data class AgentStatus(
    val allowed: Boolean,
    val until: LocalDateTime?,
    val markBytes: Long,
    val bytesPerMark: Long?,
    val quotaReached: Boolean,
    val today: DayPlan,
    val lastParentContact: Long,
    // The name the parent gave this child, once a parent has sent it.
    val childName: String? = null,
    // The rules in force, for the child's own view of the schedule.
    val rules: Rules? = null,
    // Paused by the parent on this same phone: nothing is limited.
    val paused: Boolean = false,
) {
    val hasRules: Boolean
        get() = rules != null

    companion object {
        val EMPTY = AgentStatus(false, null, 0, null, false, DayPlan(emptyList()), 0, null)
    }
}
