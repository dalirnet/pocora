package ir.pocora.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RulePartTest {
    private val thisWeek = 20_000L
    private val rules = Rules(schedule = "school-morning", appsList = "everyday", quota = "medium")

    // --- What changed ---

    @Test
    fun firstRules_changeNothing() {
        assertEquals(emptyList<RulePart>(), RulePart.changed(null, rules, thisWeek))
    }

    @Test
    fun sameRules_changeNothing() {
        assertEquals(emptyList<RulePart>(), RulePart.changed(rules, rules.copy(changedAt = 1), thisWeek))
    }

    @Test
    fun eachPart() {
        assertEquals(listOf(RulePart.TIMES), RulePart.changed(rules, rules.withSchedule("summer"), thisWeek))
        assertEquals(listOf(RulePart.APPS), RulePart.changed(rules, rules.copy(appsList = "teen"), thisWeek))
        assertEquals(listOf(RulePart.QUOTA), RulePart.changed(rules, rules.copy(quota = "high"), thisWeek))
        assertEquals(listOf(RulePart.WATCH), RulePart.changed(rules, rules.copy(watch = listOf("a.b")), thisWeek))
        assertEquals(
            listOf(RulePart.EXTRA_DATA),
            RulePart.changed(rules, rules.copy(extraData = listOf(ExtraData(thisWeek, 30, 32, 100))), thisWeek),
        )
    }

    @Test
    fun severalParts_inOrder() {
        assertEquals(
            listOf(RulePart.APPS, RulePart.QUOTA),
            RulePart.changed(rules, rules.copy(appsList = "teen", quota = "high"), thisWeek),
        )
    }

    @Test
    fun pastChanges_dropped_isNoChange() {
        val past = Change(weekday = 0, blocks = emptyList(), week = thisWeek - 7)
        assertEquals(emptyList<RulePart>(), RulePart.changed(rules.copy(changes = listOf(past)), rules, thisWeek))
    }

    @Test
    fun extraData_removed_isNoChange() {
        val extra = ExtraData(thisWeek, 30, 32, 100)
        assertEquals(emptyList<RulePart>(), RulePart.changed(rules.copy(extraData = listOf(extra)), rules, thisWeek))
    }
}
