package ir.pocora.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.pocora.R
import ir.pocora.model.Rules
import ir.pocora.model.Schedule
import ir.pocora.model.Seasons
import ir.pocora.model.Week
import ir.pocora.preset.SchedulePreset
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.WeekLegend
import ir.pocora.ui.common.ceilingText
import ir.pocora.ui.common.rememberPresets
import ir.pocora.ui.common.scheduleSummary
import ir.pocora.ui.component.BottomAction
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.CardTitle
import ir.pocora.ui.component.Categories
import ir.pocora.ui.component.Chip
import ir.pocora.ui.component.IconHeader
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.MainButton
import ir.pocora.ui.component.OptionCard
import ir.pocora.ui.component.Screen
import ir.pocora.ui.component.SectionTitle
import ir.pocora.ui.component.Strip
import ir.pocora.ui.component.WeekGrid
import ir.pocora.ui.component.WeekRow
import ir.pocora.ui.rememberFormat
import ir.pocora.ui.text
import java.time.LocalDate

// Choosing and previewing a schedule template.

private val SECTIONS =
    listOf(
        SchedulePreset.SECTION_SCHOOL_YEAR to R.string.section_school_year,
        SchedulePreset.SECTION_HOLIDAYS to R.string.section_holidays,
        SchedulePreset.SECTION_SIMPLE_RULES to R.string.section_simple_rules,
    )

// Every template as a card with its school day and Friday as small timelines. The one for this time of year is
// on top, the current one has a tick. Tapping a card opens its preview, where it is chosen.
@Composable
fun ChooseScheduleScreen(
    model: ChildModel?,
    chosen: String?,
    onBack: () -> Unit,
    onPreset: (String) -> Unit,
) {
    val palette = LocalPalette.current
    val presets = rememberPresets()
    val today = LocalDate.now()
    val seasons = remember { Seasons(presets) }
    val fitting = remember { seasons.fitting(today) }
    val upcoming = remember { seasons.upcoming(today) }
    val current = model?.rules?.schedule ?: chosen
    val suggestion = (upcoming?.first ?: fitting).firstOrNull()?.takeIf { it != current }

    Screen(title = stringResource(R.string.choose_a_schedule), onBack = onBack) {
        Text(text = stringResource(R.string.schedule_intro), color = palette.muted, fontSize = Dimens.caption)
        suggestion?.let { id ->
            SectionTitle(stringResource(R.string.suggested))
            TemplateCard(presets.schedule(id), current = false, suggested = true) { onPreset(id) }
        }
        for ((section, label) in SECTIONS) {
            SectionTitle(stringResource(label))
            for (preset in presets.schedules.filter { it.section == section }) {
                TemplateCard(preset, current = preset.id == current, suggested = false) { onPreset(preset.id) }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    preset: SchedulePreset,
    current: Boolean,
    suggested: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val presets = rememberPresets()
    val schedule = remember { Schedule(presets) }
    val style = Categories.ofSection(preset.section)
    OptionCard(
        icon = style.icon,
        color = style.color,
        title = preset.name.text(),
        selected = current,
        onClick = onClick,
        tags = listOfNotNull(preset.seasons.firstOrNull()?.let { AppIcons.Event to format.season(it.from, it.to) }),
        chip =
            when {
                current -> stringResource(R.string.current)
                suggested -> stringResource(R.string.suggested)
                else -> null
            },
        opensMore = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (weekday in listOf(Week.SATURDAY, Week.FRIDAY)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = format.shortDayName(weekday),
                        color = palette.muted,
                        fontSize = Dimens.label,
                        modifier = Modifier.width(28.dp),
                    )
                    Strip(plan = schedule.presetDay(preset.id, weekday), modifier = Modifier.weight(1f), height = 8.dp)
                }
            }
        }
    }
}

// A template's whole week, and what using it changes, one line each. The changes it removes are named here,
// so there is no separate warning. The button stays at the bottom.
@Composable
fun PreviewScheduleScreen(
    model: ChildModel?,
    scheduleId: String,
    onBack: () -> Unit,
    onUse: (Rules) -> Unit,
) {
    val format = rememberFormat()
    val presets = rememberPresets()
    val schedule = remember { Schedule(presets) }
    val preset = presets.schedule(scheduleId)
    val fits = remember { Seasons(presets).fitting(LocalDate.now()) }
    val rules = model?.rules
    val perMark = rules?.let { presets.quota(it.quota).bytesPerMark }
    val style = Categories.ofSection(preset.section)

    Screen(
        title = preset.name.text(),
        onBack = onBack,
        bottom = {
            BottomAction {
                MainButton(
                    text = stringResource(R.string.use_this_schedule),
                    enabled = model == null || model.canEdit,
                    onClick = {
                        onUse(
                            rules?.withSchedule(scheduleId) ?: Rules(scheduleId, appsList = "", quota = ""),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Card {
            IconHeader(
                style.icon,
                style.color,
                preset.name.text(),
                subtitle = scheduleSummary(schedule, scheduleId),
                chip = if (scheduleId in fits) ({ Chip(stringResource(R.string.suggested)) }) else null,
            )
        }
        Card {
            WeekGrid(
                rows = (0 until Week.DAYS).map { WeekRow(format.shortDayName(it), schedule.presetDay(scheduleId, it)) },
            )
            WeekLegend()
        }
        Card {
            CardTitle(stringResource(R.string.what_changes))
            Fact(
                AppIcons.AccessTime,
                AppColors.cyan,
                stringResource(R.string.row_internet_time),
                stringResource(R.string.hours_a_week, format.durationOfMarks(schedule.allowedMarksPerWeek(scheduleId))),
            )
            if (rules != null) {
                Fact(
                    AppIcons.DataUsage,
                    AppColors.green,
                    stringResource(R.string.row_data),
                    ceilingText(schedule.monthlyCeilingBytes(scheduleId, perMark)),
                )
                if (rules.schedule != scheduleId) {
                    Fact(
                        AppIcons.SwapHoriz,
                        AppColors.violet,
                        stringResource(R.string.row_replaces),
                        presets.schedule(rules.schedule).name.text(),
                    )
                }
                if (rules.changes.isNotEmpty()) {
                    val lines =
                        rules.changes.map { change ->
                            val day =
                                stringResource(
                                    if (change.week == null) R.string.change_every_week else R.string.change_this_week,
                                    format.dayName(change.weekday),
                                )
                            stringResource(R.string.label_and_value, day, blocksText(format, change.blocks))
                        }
                    Fact(
                        AppIcons.DeleteSweep,
                        AppColors.orange,
                        stringResource(R.string.row_removes),
                        (
                            listOf(
                                stringResource(R.string.your_changes_count, format.number(rules.changes.size)),
                            ) + lines
                        ).joinToString("\n"),
                    )
                }
            }
            Fact(
                AppIcons.PlayArrow,
                AppColors.blue,
                stringResource(R.string.row_starts),
                stringResource(R.string.now_every_week),
            )
        }
    }
}

// One line of what using the template changes: an icon, what, and its value.
@Composable
private fun Fact(
    icon: ImageVector,
    color: Color,
    label: String,
    value: String,
) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.row)) {
        IconTile(icon, color, 32.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, color = palette.muted, fontSize = Dimens.label)
            Text(text = value, color = palette.text, fontSize = Dimens.body)
        }
    }
}
