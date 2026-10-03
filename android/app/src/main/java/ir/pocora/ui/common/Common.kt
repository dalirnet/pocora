package ir.pocora.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.Role
import ir.pocora.config.Language
import ir.pocora.config.Look
import ir.pocora.model.Rules
import ir.pocora.model.Schedule
import ir.pocora.model.Seasons
import ir.pocora.model.Week
import ir.pocora.preset.PresetStore
import ir.pocora.preset.Presets
import ir.pocora.transport.Device
import ir.pocora.transport.Releases
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.AppIcon
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.CardTitle
import ir.pocora.ui.component.Chip
import ir.pocora.ui.component.IconHeader
import ir.pocora.ui.component.ProgressLine
import ir.pocora.ui.component.SectionTitle
import ir.pocora.ui.component.Segmented
import ir.pocora.ui.component.SmallButton
import ir.pocora.ui.component.ToastMessage
import ir.pocora.ui.component.Toasts
import ir.pocora.ui.component.WeekGrid
import ir.pocora.ui.component.WeekRow
import ir.pocora.ui.rememberFormat
import ir.pocora.ui.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// Parts both apps use.

// What the colours of a strip mean: internet, no internet, and a time the parent added or took away.
// The child app says "your parent", the parent app "you".
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekLegend(byParent: Boolean = false) {
    val palette = LocalPalette.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.row),
        verticalArrangement = Arrangement.spacedBy(Dimens.tiny),
    ) {
        for ((color, label) in listOf(
            palette.allowed to R.string.legend_internet,
            palette.limited to R.string.legend_no_internet,
            AppColors.changeAdded to if (byParent) R.string.legend_added_by_parent else R.string.legend_added,
            AppColors.changeCut to if (byParent) R.string.legend_cut_by_parent else R.string.legend_cut,
        )) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.tiny),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                Text(text = stringResource(label), color = palette.muted, fontSize = Dimens.label)
            }
        }
    }
}

// Language and theme, the same card in both apps' settings.
@Composable
fun LanguageAndLook(
    onLanguage: (String) -> Unit,
    onLook: (Look) -> Unit,
) {
    val config = (LocalContext.current.applicationContext as PocoraApp).configStore
    var look by remember { mutableStateOf(config.look) }
    val language = LocalConfiguration.current.locales[0].language
    SectionTitle(stringResource(R.string.language))
    Card {
        Segmented(
            modifier =
                Modifier
                    .fillMaxWidth(),
            options = listOf(stringResource(R.string.language_persian), stringResource(R.string.language_english)),
            selected = if (language == Language.PERSIAN) 0 else 1,
            onSelect = { onLanguage(if (it == 0) Language.PERSIAN else Language.ENGLISH) },
        )
    }
    SectionTitle(stringResource(R.string.look))
    Card {
        Segmented(
            modifier =
                Modifier
                    .fillMaxWidth(),
            options =
                listOf(
                    stringResource(R.string.look_system),
                    stringResource(R.string.look_light),
                    stringResource(R.string.look_dark),
                ),
            selected = look.ordinal,
            onSelect = {
                look = Look.entries[it]
                onLook(look)
            },
        )
    }
}

// The app, its name and installed version, as the last card of the settings, with an Update button. The button asks
// GitHub for the newest release: a newer one is downloaded here, with its progress under the card, then handed to
// Android's installer; otherwise a toast says this is the newest, or that GitHub could not be reached.
@Composable
fun AppVersion() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalPalette.current
    val version = remember { Device.appVersion(context) }
    val name = if (Role.current == Role.PARENT) R.string.app_name_parent else R.string.app_name_child
    val newestText = stringResource(R.string.up_to_date)
    val checkFailedText = stringResource(R.string.update_check_failed)
    val downloadFailedText = stringResource(R.string.update_failed)
    // While the button's work runs: asking GitHub, then the download, whose share this is once known.
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }

    suspend fun fetchAndInstall() {
        progress = 0f
        val file = withContext(Dispatchers.IO) { Releases.download(context) { progress = it } }
        progress = null
        file?.let { Releases.install(context, it) } ?: Toasts.show(downloadFailedText, ToastMessage.Kind.PROBLEM)
    }

    fun update() {
        busy = true
        scope.launch {
            val latest = withContext(Dispatchers.IO) { Releases.latest() }
            when {
                latest == null -> Toasts.show(checkFailedText, ToastMessage.Kind.PROBLEM)
                !Releases.isNewer(latest, version) -> Toasts.show(newestText)
                else -> fetchAndInstall()
            }
            busy = false
        }
    }

    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.row)) {
            AppIcon(Dimens.rowIcon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CardTitle(stringResource(name))
                Text(text = stringResource(R.string.version, version), color = palette.muted, fontSize = Dimens.caption)
            }
            SmallButton(text = stringResource(R.string.update), onClick = ::update, enabled = !busy, filled = true)
        }
        progress?.let { ProgressLine(it, Modifier.fillMaxWidth(), color = palette.brand) }
    }
}

// The schedule in force: its name with the season chip beside it, how much internet it gives, then actions if any.
@Composable
fun TemplateCard(
    presets: Presets,
    rules: Rules,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val palette = LocalPalette.current
    val schedule = remember(presets) { Schedule(presets) }
    val fits = rules.schedule in remember(presets) { Seasons(presets) }.fitting(LocalDate.now())
    Card {
        IconHeader(
            icon = AppIcons.CalendarMonth,
            color = palette.brand,
            title = presets.schedule(rules.schedule).name.text(),
            subtitle = scheduleSummary(schedule, rules.schedule),
            chip = if (fits) ({ Chip(stringResource(R.string.fits_this_season)) }) else null,
        )
        actions()
    }
}

// This week as seven strips, today marked and holidays in orange, with the legend.
// A day can be tapped when onDay is given. More can follow under it.
@Composable
fun WeekCard(
    presets: Presets,
    rules: Rules,
    hint: String,
    byParent: Boolean,
    onDay: ((LocalDate) -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val format = rememberFormat()
    val schedule = remember(presets) { Schedule(presets) }
    val seasons = remember(presets) { Seasons(presets) }
    val today = LocalDate.now()
    val start = Week.startOf(today)
    Card {
        CardTitle(stringResource(R.string.this_week_title), hint)
        WeekGrid(
            rows =
                (0 until Week.DAYS).map { weekday ->
                    val date = start.plusDays(weekday.toLong())
                    WeekRow(
                        label = format.shortDayName(weekday),
                        plan = schedule.day(rules, date),
                        differences = schedule.differences(rules, date),
                        holiday = seasons.holidayOn(date) != null,
                        today = date == today,
                    )
                },
            onDay = onDay?.let { open -> { weekday: Int -> open(start.plusDays(weekday.toLong())) } },
        )
        WeekLegend(byParent)
        footer()
    }
}

@Composable
fun ceilingText(bytes: Long?): String =
    if (bytes == null) {
        stringResource(R.string.no_monthly_limit)
    } else {
        stringResource(R.string.at_most_a_month, rememberFormat().size(bytes))
    }

// "School days: 2h · Friday: 4h", from a Saturday and a Friday of the preset.
@Composable
fun scheduleSummary(
    schedule: Schedule,
    scheduleId: String,
): String {
    val format = rememberFormat()
    return stringResource(
        R.string.school_days_and_friday,
        format.durationOfMarks(schedule.presetDay(scheduleId, Week.SATURDAY).allowedMarks),
        format.durationOfMarks(schedule.presetDay(scheduleId, Week.FRIDAY).allowedMarks),
    )
}

// The shipped presets, read once for both apps.
@Composable
fun rememberPresets(): Presets {
    val context = LocalContext.current
    return remember { PresetStore.get(context) }
}
