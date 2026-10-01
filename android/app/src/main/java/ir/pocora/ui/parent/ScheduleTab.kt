package ir.pocora.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import ir.pocora.R
import ir.pocora.model.Block
import ir.pocora.model.Change
import ir.pocora.model.Rules
import ir.pocora.model.Schedule
import ir.pocora.model.Week
import ir.pocora.ui.AppColors
import ir.pocora.ui.Dimens
import ir.pocora.ui.Format
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.TemplateCard
import ir.pocora.ui.common.WeekCard
import ir.pocora.ui.common.rememberPresets
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.LinkRow
import ir.pocora.ui.component.MainButton
import ir.pocora.ui.component.SectionTitle
import ir.pocora.ui.component.SmallButton
import ir.pocora.ui.rememberFormat
import ir.pocora.ui.text
import java.time.LocalDate

private const val MORNING = "school-morning"
private const val AFTERNOON = "school-afternoon"

// The schedule, the changes for today, the week to edit by day, and the parent's changes as sentences.
@Composable
fun ScheduleTab(
    model: ChildModel,
    rules: Rules,
    copyFriday: Boolean,
    go: (Route) -> Unit,
) {
    val format = rememberFormat()
    val presets = rememberPresets()
    val schedule = remember { Schedule(presets) }
    val today = LocalDate.now()
    val enabled = model.canEdit

    LaunchedEffect(copyFriday) {
        if (copyFriday) SheetState.open = ChildSheet.Copy(today.plusDays(1).toEpochDay(), Week.FRIDAY)
    }

    TemplateCard(presets, rules) {
        val other =
            when (rules.schedule) {
                MORNING -> AFTERNOON to R.string.switch_to_afternoon
                AFTERNOON -> MORNING to R.string.switch_to_morning
                else -> null
            }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
            if (other != null) {
                MainButton(
                    text = stringResource(other.second),
                    onClick = { model.apply(rules.withSchedule(other.first)) },
                    enabled = enabled,
                    quiet = true,
                    modifier = Modifier.weight(1f),
                )
            }
            MainButton(
                text = stringResource(R.string.change_schedule),
                onClick = { go(Route.ChooseSchedule(model.child.id)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }

    WeekCard(
        presets,
        rules,
        stringResource(R.string.tap_a_day),
        byParent = false,
        onDay = if (enabled) ({ date -> go(Route.EditDay(model.child.id, date.toEpochDay())) }) else null,
    ) {
        LinkRow(
            title = stringResource(R.string.same_as_another_day),
            icon = Icons.Filled.ContentCopy,
            iconColor = AppColors.slate,
            onClick = { if (enabled) SheetState.open = ChildSheet.Copy(today.toEpochDay(), null) },
        )
    }

    if (rules.changes.isNotEmpty()) {
        SectionTitle(stringResource(R.string.your_changes))
        for (change in rules.changes.sortedWith(compareBy({ it.week ?: Long.MAX_VALUE }, { it.weekday }))) {
            ChangeCard(format, change, enabled) { model.apply(schedule.removeChange(rules, change)) }
        }
    }
}

// "Thursday, this week only" over "Internet 10:00 to 13:00, 17:00 to 22:00".
@Composable
private fun ChangeCard(
    format: Format,
    change: Change,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val palette = LocalPalette.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.tiny)) {
                val day = format.dayName(change.weekday)
                Text(
                    text =
                        stringResource(
                            if (change.week ==
                                null
                            ) {
                                R.string.change_every_week
                            } else {
                                R.string.change_this_week
                            },
                            day,
                        ),
                    color = palette.text,
                    fontSize = Dimens.body,
                )
                Text(text = blocksText(format, change.blocks), color = palette.muted, fontSize = 14.sp)
            }
            SmallButton(text = stringResource(R.string.remove), onClick = onRemove, enabled = enabled)
        }
    }
}

// "Internet 10:00 to 13:00, 17:00 to 22:00", or "No internet all day".
@Composable
fun blocksText(
    format: Format,
    blocks: List<Block>,
): String {
    if (blocks.isEmpty()) return stringResource(R.string.no_internet_all_day)
    val separator = stringResource(R.string.list_separator)
    val ranges = blocks.map { stringResource(R.string.time_range, format.mark(it.start), format.mark(it.end)) }
    return stringResource(R.string.internet_times, ranges.joinToString(separator))
}
