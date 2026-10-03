package ir.pocora.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pocora.R
import ir.pocora.model.AppAccess
import ir.pocora.model.AppChoice
import ir.pocora.model.Mark
import ir.pocora.model.Schedule
import ir.pocora.model.Week
import ir.pocora.preset.AppGroup
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.rememberPresets
import ir.pocora.ui.component.ButtonPair
import ir.pocora.ui.component.CardTitle
import ir.pocora.ui.component.Categories
import ir.pocora.ui.component.ChoiceTile
import ir.pocora.ui.component.HourAxis
import ir.pocora.ui.component.OptionCard
import ir.pocora.ui.component.Sheet
import ir.pocora.ui.component.Strip
import ir.pocora.ui.component.SwitchRow
import ir.pocora.ui.rememberFormat
import ir.pocora.ui.text
import java.time.LocalDate
import java.time.LocalDateTime

private val DURATIONS = listOf(1, 2, 4)

private val EXTRA_MEGABYTES = listOf(100, 250, 500, 1000)

private const val BYTES_PER_MEGABYTE = 1_000_000L

// "Stop internet" and "Allow internet": a change for today from now. It asks only how long, and each answer sends at once.
@Composable
fun DurationSheet(
    model: ChildModel,
    allowed: Boolean,
    onClose: () -> Unit,
) {
    val format = rememberFormat()
    val presets = rememberPresets()
    val schedule = remember { Schedule(presets) }
    val rules = model.rules ?: return
    val color = if (allowed) AppColors.blue else AppColors.orange
    Sheet(
        onDismiss = onClose,
        title = stringResource(if (allowed) R.string.allow_internet_now else R.string.stop_internet_now),
        icon = if (allowed) AppIcons.Wifi else AppIcons.WifiOff,
        color = color,
        subtitle = stringResource(R.string.for_how_long),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
            for (marks in DURATIONS) {
                ChoiceTile(format.shortDuration(marks), color, {
                    val now = LocalDateTime.now()
                    val mark = schedule.markOf(now)
                    val changed =
                        if (allowed) {
                            schedule.addTime(rules, now.toLocalDate(), mark, marks)
                        } else {
                            schedule.cut(rules, now.toLocalDate(), mark, marks)
                        }
                    model.apply(changed, onClose)
                }, Modifier.weight(1f), enabled = model.canEdit)
            }
        }
    }
}

// "More data": extra for the Allowed block on now, on top of the quota. Each amount sends at once.
@Composable
fun DataSheet(
    model: ChildModel,
    onClose: () -> Unit,
) {
    val format = rememberFormat()
    val presets = rememberPresets()
    val schedule = remember { Schedule(presets) }
    val rules = model.rules ?: return
    val now = LocalDateTime.now()
    val block = schedule.day(rules, now.toLocalDate()).blockAt(schedule.markOf(now)) ?: return
    val end = now.toLocalDate().atStartOfDay().plusMinutes(block.end * Mark.DURATION_MINUTES.toLong())
    Sheet(
        onDismiss = onClose,
        title = stringResource(R.string.more_data_title),
        icon = AppIcons.DataSaverOn,
        color = AppColors.green,
        subtitle = stringResource(R.string.until_capital, format.time(end)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
            for (megabytes in EXTRA_MEGABYTES) {
                ChoiceTile(format.size(megabytes * BYTES_PER_MEGABYTE), AppColors.green, {
                    model.apply(schedule.addData(rules, LocalDateTime.now(), megabytes), onClose)
                }, Modifier.weight(1f), enabled = model.canEdit)
            }
        }
    }
}

// On the schedule tab: give one day the hours of another, for this week: a holiday in the middle of the week.
// The days are round buttons, Saturday first; the day being changed is left out. The day is redrawn as it would be.
@Composable
fun CopySheet(
    model: ChildModel,
    epochDay: Long,
    from: Int?,
    onClose: () -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val presets = rememberPresets()
    val schedule = remember { Schedule(presets) }
    val rules = model.rules ?: return
    val target = LocalDate.ofEpochDay(epochDay)
    val weekday = Week.weekdayOf(target)
    var source by rememberSaveable {
        mutableIntStateOf(from ?: if (weekday == Week.FRIDAY) Week.THURSDAY else Week.FRIDAY)
    }
    val preview = schedule.copyDay(rules, target, source)
    Sheet(
        onDismiss = onClose,
        title = stringResource(R.string.same_as_another_day),
        icon = AppIcons.ContentCopy,
        color = AppColors.violet,
        subtitle = format.date(target),
    ) {
        CardTitle(stringResource(R.string.use_hours_of))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (day in 0 until Week.DAYS) {
                val chosen = day == source
                val usable = day != weekday
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(
                                when {
                                    chosen -> palette.brand
                                    usable -> palette.limited.copy(alpha = 0.6f)
                                    else -> palette.limited.copy(alpha = 0.2f)
                                },
                            ).clickable(enabled = usable) { source = day },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = format.shortDayName(day),
                        color =
                            when {
                                chosen -> AppColors.onColor
                                usable -> palette.text
                                else -> palette.muted
                            },
                        fontSize = Dimens.body,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.tiny)) {
            Strip(
                plan = schedule.day(preview, target),
                differences = schedule.differences(preview, target),
                height = 14.dp,
            )
            HourAxis()
        }
        Text(
            text = blocksText(format, schedule.day(preview, target).blocks),
            color = palette.muted,
            fontSize = Dimens.caption,
        )
        ButtonPair(stringResource(R.string.cancel), onClose, stringResource(R.string.use_these_hours), {
            model.apply(preview, onClose)
        }, model.canEdit)
    }
}

// One app: its kind and this week's use on top, then whether it gets internet, watching it, and asking
// the child to remove it. Each choice is sent at once.
@Composable
fun OneAppSheet(
    model: ChildModel,
    packageName: String,
    onClose: () -> Unit,
) {
    val format = rememberFormat()
    val presets = rememberPresets()
    val snapshot = model.snapshot ?: return
    val rules = snapshot.rules
    val app = snapshot.apps.firstOrNull { it.`package` == packageName }
    val name = app?.name ?: packageName
    val style = Categories.of(app?.group ?: AppGroup.OTHER)
    val week = Week.startOf(LocalDate.now()).toEpochDay()
    val usage = snapshot.usage.filter { it.`package` == packageName && it.date >= week }
    // An app the presets keep always on offers no "as the list says": always is its default, and never the other way.
    val always = presets.isAlways(packageName)
    val choice = AppAccess.choiceOf(presets, rules, packageName)
    val setChoice: (AppChoice?) -> Unit = { next ->
        if (next != choice) {
            val own = next.takeUnless { always && it == AppChoice.IN }
            model.apply(
                rules.copy(apps = if (own == null) rules.apps - packageName else rules.apps + (packageName to own)),
            )
        }
    }
    Sheet(
        onDismiss = onClose,
        title = name,
        icon = style.icon,
        color = style.color,
        subtitle =
            listOfNotNull(
                app?.let { presets.group(it.group).name.text() },
                stringResource(
                    R.string.app_week_use,
                    format.duration(
                        usage.sumOf {
                            it.screenMilliseconds
                        },
                    ),
                    format.size(usage.sumOf { it.bytes }),
                ),
            ).joinToString("\n"),
    ) {
        Text(
            text = stringResource(R.string.internet_for_app),
            color = LocalPalette.current.text,
            fontSize = Dimens.body,
            fontWeight = FontWeight.Bold,
        )
        if (!always) {
            OptionCard(AppIcons.Apps, AppColors.blue, stringResource(R.string.choice_by_list), choice == null, {
                setChoice(null)
            }, enabled = model.canEdit)
        }
        OptionCard(AppIcons.Wifi, AppColors.green, stringResource(R.string.choice_always), choice == AppChoice.IN, {
            setChoice(AppChoice.IN)
        }, enabled = model.canEdit)
        OptionCard(
            AppIcons.WifiOff,
            AppColors.orange,
            stringResource(R.string.choice_never),
            choice == AppChoice.OUT,
            { setChoice(AppChoice.OUT) },
            enabled = model.canEdit,
        )
        SwitchRow(
            icon = AppIcons.Visibility,
            iconColor = AppColors.violet,
            title = stringResource(R.string.watch_this_app),
            checked = packageName in rules.watch,
            enabled = model.canEdit,
            onChange = { on ->
                model.apply(
                    rules.copy(
                        watch =
                            if (on) {
                                rules.watch + packageName
                            } else {
                                rules.watch -
                                    packageName
                            },
                    ),
                )
            },
        )
    }
}

// A change got no answer. Nothing is queued, so the parent tries again.
@Composable
fun NotReachableSheet(
    childName: String,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Sheet(
        onDismiss = onCancel,
        title = stringResource(R.string.cant_reach, childName),
        icon = AppIcons.WifiFind,
        color = AppColors.orange,
        subtitle = stringResource(R.string.cant_reach_text),
    ) {
        ButtonPair(stringResource(R.string.cancel), onCancel, stringResource(R.string.try_again), onRetry)
    }
}
