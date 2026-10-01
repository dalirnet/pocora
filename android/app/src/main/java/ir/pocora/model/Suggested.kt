package ir.pocora.model

import ir.pocora.preset.Presets
import java.time.LocalDate

// What adding a child fills in: the schedule that fits the season, the apps list that fits the age, a medium quota.
object Suggested {
    private const val QUOTA = "medium"

    // Before a parent has sent any rules: nothing is limited by data.
    const val NO_LIMIT_QUOTA = "no-limit"
    const val OPEN_APPS_LIST = "everything"
    private const val BY_AGE = "by_age"

    fun rules(
        presets: Presets,
        age: Int?,
        today: LocalDate,
    ): Rules =
        Rules(
            schedule = Seasons(presets).fitting(today).firstOrNull() ?: Rules.FIRST_WEEK_SCHEDULE,
            appsList = appsList(presets, age),
            quota = QUOTA,
        )

    fun appsList(
        presets: Presets,
        age: Int?,
    ): String = presets.appsLists.firstOrNull { it.section == BY_AGE && it.fitsAge(age) }?.id ?: OPEN_APPS_LIST
}
