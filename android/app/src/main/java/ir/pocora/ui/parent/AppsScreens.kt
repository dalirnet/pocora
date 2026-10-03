package ir.pocora.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.pocora.R
import ir.pocora.model.AppAccess
import ir.pocora.model.AppChoice
import ir.pocora.model.Snapshot
import ir.pocora.model.Suggested
import ir.pocora.model.Week
import ir.pocora.preset.AppGroup
import ir.pocora.preset.AppsListPreset
import ir.pocora.preset.Presets
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.rememberPresets
import ir.pocora.ui.component.AppRow
import ir.pocora.ui.component.BottomAction
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.CardTitle
import ir.pocora.ui.component.Categories
import ir.pocora.ui.component.Chip
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.IconHeader
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.LinkRow
import ir.pocora.ui.component.MainButton
import ir.pocora.ui.component.OptionCard
import ir.pocora.ui.component.Screen
import ir.pocora.ui.component.SectionTitle
import ir.pocora.ui.component.SmallButton
import ir.pocora.ui.rememberFormat
import ir.pocora.ui.text
import java.time.LocalDate

// The child's apps: the tab, a kind of apps, and choosing an apps list.

private const val GROUPS_SHOWN = 6

// The apps list, every kind of app with whether it has internet, and the apps the parent chose one by one.
@Composable
fun AppsTab(
    model: ChildModel,
    snapshot: Snapshot,
    go: (Route) -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val presets = rememberPresets()
    val rules = snapshot.rules
    val list = presets.appsList(rules.appsList)
    val groups = presets.groups.filter { it.id != AppGroup.SYSTEM }
    val hasInternet: (String) -> Boolean = { AppAccess.groupHasInternet(presets, rules.appsList, it) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card {
        IconHeader(
            AppIcons.Apps,
            AppColors.blue,
            list.name.text(),
            subtitle =
                stringResource(
                    R.string.groups_with_internet,
                    format.number(
                        groups.count {
                            hasInternet(it.id)
                        },
                    ),
                    format.number(groups.size),
                ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            list.ages?.let { Chip(stringResource(R.string.age_range, format.number(it[0]), format.number(it[1]))) }
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                SmallButton(text = stringResource(R.string.change), onClick = {
                    go(Route.ChooseApps(model.child.id))
                }, enabled = model.canEdit)
            }
        }
    }

    Card {
        CardTitle(stringResource(R.string.kinds_of_apps))
        val sorted = groups.sortedByDescending { hasInternet(it.id) }
        for (group in if (expanded) sorted else sorted.take(GROUPS_SHOWN)) {
            val open = hasInternet(group.id)
            val style = Categories.of(group.id)
            val count = snapshot.apps.count { it.group == group.id && !it.system }
            LinkRow(
                title = group.name.text(),
                subtitle = stringResource(if (open) R.string.group_has_internet else R.string.group_no_internet),
                note = format.appsCount(count),
                icon = style.icon,
                iconColor = if (open) style.color else palette.muted,
                onClick = { go(Route.Group(model.child.id, group.id)) },
            )
        }
        if (!expanded && sorted.size > GROUPS_SHOWN) {
            SmallButton(text = stringResource(R.string.show_all_kinds, format.number(sorted.size)), onClick = {
                expanded =
                    true
            })
        }
    }

    if (rules.apps.isNotEmpty()) {
        Card {
            CardTitle(stringResource(R.string.your_choices))
            for ((packageName, choice) in rules.apps) {
                val app = snapshot.apps.firstOrNull { it.`package` == packageName }
                val style = Categories.of(app?.group ?: AppGroup.OTHER)
                LinkRow(
                    title = app?.name ?: packageName,
                    note =
                        stringResource(
                            if (choice ==
                                AppChoice.IN
                            ) {
                                R.string.always_internet
                            } else {
                                R.string.never_internet
                            },
                        ),
                    icon = style.icon,
                    iconColor = style.color,
                    onClick = { SheetState.open = ChildSheet.OneApp(packageName) },
                )
            }
        }
    }
}

// The child's apps of one kind, with this week's use. Tapping one opens its sheet.
@Composable
fun GroupScreen(
    model: ChildModel,
    groupId: String,
    onBack: () -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val presets = rememberPresets()
    val snapshot = model.snapshot ?: return
    val rules = snapshot.rules
    val group = presets.group(groupId)
    val style = Categories.of(groupId)
    val open = AppAccess.groupHasInternet(presets, rules.appsList, groupId)
    val week = Week.startOf(LocalDate.now()).toEpochDay()
    val apps =
        snapshot.apps
            .filter { it.group == groupId && !it.system }
            .map { app ->
                val usage = snapshot.usage.filter { it.`package` == app.`package` && it.date >= week }
                Triple(app, usage.sumOf { it.screenMilliseconds }, usage.sumOf { it.bytes })
            }.sortedByDescending { it.second }
    val top = apps.maxOfOrNull { maxOf(it.second, 1L) } ?: 1L

    Box(modifier = Modifier.fillMaxSize()) {
        Screen(title = group.name.text(), onBack = onBack) {
            Card {
                IconHeader(
                    style.icon,
                    if (open) style.color else palette.muted,
                    stringResource(if (open) R.string.group_has_internet else R.string.group_no_internet),
                    subtitle = format.appsCount(apps.size),
                )
            }
            if (apps.isEmpty()) {
                EmptyState(style.icon, style.color, stringResource(R.string.no_apps_in_group), compact = true)
            }
            if (apps.isNotEmpty()) {
                Card {
                    for ((app, screen, bytes) in apps) {
                        val choice = AppAccess.choiceOf(presets, rules, app.`package`)
                        val chip =
                            when {
                                app.`package` in rules.watch -> stringResource(R.string.watched)
                                choice == AppChoice.IN -> stringResource(R.string.always_internet)
                                choice == AppChoice.OUT -> stringResource(R.string.never_internet)
                                else -> null
                            }
                        AppRow(
                            name = app.name,
                            group = app.group,
                            share = screen.toFloat() / top,
                            top = if (screen > 0) format.duration(screen) else stringResource(R.string.not_used),
                            bottom = if (bytes > 0) format.size(bytes) else null,
                            chip = chip,
                            onClick = { SheetState.open = ChildSheet.OneApp(app.`package`) },
                        )
                    }
                    Text(text = stringResource(R.string.this_week), color = palette.muted, fontSize = Dimens.label)
                }
            }
        }
        ChildSheets(model)
    }
}

private val SECTIONS =
    listOf(
        "by_age" to R.string.section_by_age,
        "by_purpose" to R.string.section_by_purpose,
        "open" to R.string.section_open,
    )
private const val KINDS_PER_ROW = 6

// P11b. Choosing an apps list. On top, the chosen list as a picture: every kind of app, coloured if it gets internet.
// Below, every list as a card, grouped by age, by purpose, and everything. The button stays at the bottom.
@Composable
fun ChooseAppsScreen(
    model: ChildModel?,
    age: Int?,
    chosen: String?,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val presets = rememberPresets()
    val start = model?.rules?.appsList ?: chosen ?: Suggested.appsList(presets, age)
    var selected by rememberSaveable { mutableStateOf(start) }
    val list = presets.appsList(selected)
    val kinds = presets.groups.filter { it.id != AppGroup.SYSTEM }

    Screen(
        title = stringResource(R.string.choose_apps_list),
        onBack = onBack,
        bottom = {
            BottomAction {
                MainButton(
                    text = stringResource(R.string.use_this_list),
                    enabled = (model == null || model.canEdit) && selected != (model?.rules?.appsList ?: ""),
                    onClick = { onSave(selected) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Text(text = stringResource(R.string.apps_list_intro), color = palette.muted, fontSize = Dimens.caption)
        Preview(presets, list, kinds.map { it.id })

        for ((section, label) in SECTIONS) {
            SectionTitle(stringResource(label))
            for (option in presets.appsLists.filter { it.section == section }) {
                val style = Categories.ofList(option.id)
                val ages = option.ages?.takeIf { section == "by_age" }
                val open = option.groups.count { it != AppGroup.SYSTEM }
                OptionCard(
                    icon = style.icon,
                    color = style.color,
                    title = option.name.text(),
                    selected = option.id == selected,
                    onClick = { selected = option.id },
                    tags =
                        listOfNotNull(
                            ages?.let {
                                AppIcons.Cake to
                                    stringResource(R.string.years_range, format.number(it[0]), format.number(it[1]))
                            },
                            AppIcons.Apps to
                                stringResource(R.string.kinds_short, format.number(open), format.number(kinds.size)),
                        ),
                    chip =
                        if (section == "by_age" &&
                            option.fitsAge(age)
                        ) {
                            stringResource(R.string.fits_age, format.number(age ?: 0))
                        } else {
                            null
                        },
                )
            }
        }
    }
}

// The chosen list: its name, and every kind of app as a small tile, coloured if it gets internet.
@Composable
private fun Preview(
    presets: Presets,
    list: AppsListPreset,
    kinds: List<String>,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val style = Categories.ofList(list.id)
    val open = kinds.count { it in list.groups }
    Card {
        IconHeader(
            style.icon,
            style.color,
            list.name.text(),
            subtitle = stringResource(R.string.kinds_get_internet, format.number(open), format.number(kinds.size)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
            for (row in kinds.chunked(KINDS_PER_ROW)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (kind in row) {
                        val kindStyle = Categories.of(kind)
                        val has = kind in list.groups
                        Column(
                            modifier = Modifier.weight(1f).alpha(if (has) 1f else 0.35f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimens.tiny),
                        ) {
                            IconTile(kindStyle.icon, if (has) kindStyle.color else palette.muted, 36.dp)
                            Text(
                                text = presets.group(kind).name.text(),
                                color = palette.text,
                                fontSize = Dimens.label,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(KINDS_PER_ROW - row.size) { Column(modifier = Modifier.weight(1f)) {} }
                }
            }
        }
    }
}
