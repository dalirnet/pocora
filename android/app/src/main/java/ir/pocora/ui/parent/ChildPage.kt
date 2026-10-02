package ir.pocora.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.model.IranianDate
import ir.pocora.model.Mark
import ir.pocora.model.Schedule
import ir.pocora.model.Snapshot
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.UsageContent
import ir.pocora.ui.common.rememberPresets
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.LoadingCards
import ir.pocora.ui.component.NumberPair
import ir.pocora.ui.component.OptionCard
import ir.pocora.ui.component.ProgressLine
import ir.pocora.ui.component.Screen
import ir.pocora.ui.component.SectionTitle
import ir.pocora.ui.rememberFormat
import ir.pocora.ui.text
import java.time.LocalDate

// One of a child's screens: Usage, Internet times, Apps, Data limit. It reads the child's phone when it opens,
// and away from it says the screen shows the last update.
@Composable
fun ChildPage(
    model: ChildModel,
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.(Snapshot) -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    LaunchedEffect(model.child.id) { model.refresh() }
    Box(modifier = Modifier.fillMaxSize()) {
        Screen(title = title, onBack = onBack, centered = model.snapshot == null) {
            val snapshot = model.snapshot
            if (snapshot == null) {
                if (model.loading) {
                    LoadingCards(3)
                } else {
                    EmptyState(
                        icon = AppIcons.CloudOff,
                        color = palette.brand,
                        title = stringResource(R.string.empty_nothing_title, model.child.name),
                        text = stringResource(R.string.empty_nothing_text),
                        action = stringResource(R.string.try_again),
                        onAction = model::refresh,
                    )
                }
                return@Screen
            }
            if (!model.online) {
                Card {
                    Text(
                        text = stringResource(R.string.offline_banner, format.dayAndTime(snapshot.takenAt)),
                        color = palette.muted,
                        fontSize = 14.sp,
                    )
                }
            }
            content(snapshot)
        }
        ChildSheets(model)
    }
}

// The sheets a tab opens over the child's page. One at a time.
sealed interface ChildSheet {
    data class Duration(
        val allowed: Boolean,
    ) : ChildSheet

    data class Copy(
        val epochDay: Long,
        val from: Int?,
    ) : ChildSheet

    data class OneApp(
        val packageName: String,
    ) : ChildSheet

}

object SheetState {
    var open by mutableStateOf<ChildSheet?>(null)
}

@Composable
fun ChildSheets(model: ChildModel) {
    when (val sheet = SheetState.open) {
        is ChildSheet.Duration -> DurationSheet(model, sheet.allowed) { SheetState.open = null }
        is ChildSheet.Copy -> CopySheet(model, sheet.epochDay, sheet.from) { SheetState.open = null }
        is ChildSheet.OneApp -> OneAppSheet(model, sheet.packageName) { SheetState.open = null }
        null -> Unit
    }
}

// The child's usage, with the times their phone was out of touch.
@Composable
fun UsageTab(
    model: ChildModel,
    snapshot: Snapshot,
) {
    val parent = (LocalContext.current.applicationContext as PocoraApp).parent
    UsageContent(snapshot, parent.presets, parent.contacts.all(model.child.id))
}

// The data limit in a sentence, this month against the most the schedule allows, then the levels.
@Composable
fun DataTab(
    model: ChildModel,
    snapshot: Snapshot,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val presets = rememberPresets()
    val schedule = remember { Schedule(presets) }
    val rules = snapshot.rules
    val quota = presets.quota(rules.quota)
    val today = LocalDate.now()
    val monthStart =
        today.minusDays(
            IranianDate
                .of(today)
                .day - 1L,
        )
    // Only the days the snapshot keeps, so early in a long month this is a floor.
    val used = snapshot.days.filter { it.date >= monthStart.toEpochDay() }.sumOf { it.totalBytes }
    val ceiling = schedule.monthlyCeilingBytes(rules.schedule, quota.bytesPerMark)

    Card {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NumberPair(value = format.size(used), label = stringResource(R.string.used_this_month))
            NumberPair(value = ceiling?.let { format.size(it) } ?: "-", label = stringResource(R.string.at_most))
        }
        if (ceiling != null && ceiling > 0) ProgressLine(share = used.toFloat() / ceiling, color = AppColors.green)
        Text(
            text = stringResource(R.string.with_schedule, presets.schedule(rules.schedule).name.text()),
            color = palette.muted,
            fontSize = Dimens.caption,
        )
    }

    SectionTitle(stringResource(R.string.change_the_limit))
    Text(
        text = stringResource(R.string.data_limit_text),
        color = palette.muted,
        fontSize = Dimens.caption,
        lineHeight = 20.sp,
    )
    for (level in presets.quotas) {
        OptionCard(
            icon = AppIcons.DataUsage,
            color = levelColor(level.megabytesPerMark),
            title = level.name.text(),
            selected = level.id == rules.quota,
            enabled = model.canEdit,
            tags =
                listOfNotNull(
                    // Shown per hour, which reads more easily. The limit itself still applies to each half hour.
                    level.bytesPerMark?.let {
                        AppIcons.Timer to stringResource(R.string.per_hour, format.size(it * Mark.PER_HOUR))
                    },
                    schedule.monthlyCeilingBytes(rules.schedule, level.bytesPerMark)?.let {
                        AppIcons.CalendarMonth to stringResource(R.string.a_month_short, format.size(it))
                    },
                ),
            onClick = { if (level.id != rules.quota) model.apply(rules.copy(quota = level.id)) },
        )
    }
}

// Less data is a cooler colour, no limit violet.
private fun levelColor(megabytes: Int?) =
    when {
        megabytes == null -> AppColors.violet
        megabytes <= LIGHT_MEGABYTES -> AppColors.teal
        megabytes <= MEDIUM_MEGABYTES -> AppColors.green
        megabytes <= HIGH_MEGABYTES -> AppColors.orange
        else -> AppColors.red
    }

private const val LIGHT_MEGABYTES = 25
private const val MEDIUM_MEGABYTES = 100
private const val HIGH_MEGABYTES = 250
