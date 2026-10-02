package ir.pocora.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.R
import ir.pocora.model.Block
import ir.pocora.model.DayPlan
import ir.pocora.model.Difference
import ir.pocora.model.Mark
import ir.pocora.model.Schedule
import ir.pocora.model.Week
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.WeekLegend
import ir.pocora.ui.common.rememberPresets
import ir.pocora.ui.component.ActionButton
import ir.pocora.ui.component.BottomAction
import ir.pocora.ui.component.ButtonPair
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.CardTitle
import ir.pocora.ui.component.Categories
import ir.pocora.ui.component.ChoiceTile
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.HourAxis
import ir.pocora.ui.component.IconAction
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.LinkRow
import ir.pocora.ui.component.MainButton
import ir.pocora.ui.component.OptionCard
import ir.pocora.ui.component.Screen
import ir.pocora.ui.component.Segmented
import ir.pocora.ui.component.Sheet
import ir.pocora.ui.component.SmallButton
import ir.pocora.ui.component.Strip
import ir.pocora.ui.rememberFormat
import ir.pocora.ui.text
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

private const val NEW_BLOCK_START = 32
private const val NEW_BLOCK_MARKS = 2
private val BREAK_LENGTHS = listOf(1, 2, 3, 4)

// Which end of an internet time the time sheet is choosing.
private sealed interface Picking {
    data class Start(
        val index: Int,
    ) : Picking

    data class End(
        val index: Int,
    ) : Picking
}

// One day: its timeline on top, then each internet time as a card with when it starts and ends,
// which a tap changes. Add a time, add a break, choose this week or every week, then Save at the bottom.
@Composable
fun EditDayScreen(
    model: ChildModel,
    date: LocalDate,
    onBack: () -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val presets = rememberPresets()
    val schedule = remember { Schedule(presets) }
    val rules = model.rules ?: return
    val weekday = Week.weekdayOf(date)
    val serializer = ListSerializer(Block.serializer())
    var blocksText by rememberSaveable {
        mutableStateOf(Json.encodeToString(serializer, schedule.day(rules, date).normalized()))
    }
    val blocks = Json.decodeFromString(serializer, blocksText)
    val setBlocks: (List<Block>) -> Unit = { blocksText = Json.encodeToString(serializer, DayPlan(it).normalized()) }
    var everyWeek by rememberSaveable { mutableStateOf(false) }
    var cutting by rememberSaveable { mutableStateOf(false) }
    var listFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf<Picking?>(null) }
    val plan = DayPlan(blocks)
    val differences = Difference.between(schedule.presetDay(rules.schedule, weekday), plan)

    Box(modifier = Modifier.fillMaxSize()) {
        Screen(
            title = format.date(date),
            onBack = onBack,
            trailing = {
                SmallButton(text = stringResource(R.string.undo_changes), enabled = model.canEdit, onClick = {
                    model.apply(schedule.resetDay(rules, date), onBack)
                })
            },
            bottom = {
                BottomAction {
                    MainButton(
                        text = stringResource(R.string.save),
                        enabled = model.canEdit,
                        onClick = { model.apply(schedule.setDay(rules, date, blocks, everyWeek), onBack) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        ) {
            Card {
                CardTitle(blocksText(format, blocks))
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.tiny)) {
                    Strip(plan = plan, differences = differences, height = 18.dp)
                    HourAxis()
                }
                WeekLegend()
            }
            Segmented(
                options =
                    listOf(
                        stringResource(R.string.only_this_weekday, format.dayName(weekday)),
                        stringResource(R.string.every_weekday, format.dayName(weekday)),
                    ),
                selected = if (everyWeek) 1 else 0,
                onSelect = { everyWeek = it == 1 },
                modifier = Modifier.fillMaxWidth(),
            )

            if (blocks.isEmpty()) {
                EmptyState(
                    icon = AppIcons.WifiOff,
                    color = palette.muted,
                    title = stringResource(R.string.no_internet_all_day),
                    text = stringResource(R.string.no_internet_this_day),
                    compact = true,
                )
            }
            blocks.forEachIndexed { index, block ->
                Card {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.row),
                    ) {
                        IconTile(AppIcons.AccessTime, AppColors.cyan, Dimens.rowIcon)
                        Text(
                            text =
                                stringResource(
                                    R.string.internet_from_to,
                                    format.mark(block.start),
                                    format.mark(block.end),
                                ),
                            color = palette.text,
                            fontSize = Dimens.body,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconAction(AppIcons.Delete, stringResource(R.string.remove), { setBlocks(blocks - block) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
                        TimePill(stringResource(R.string.starts_at), format.mark(block.start), Modifier.weight(1f)) {
                            picking =
                                Picking.Start(index)
                        }
                        TimePill(stringResource(R.string.ends_at), format.mark(block.end), Modifier.weight(1f)) {
                            picking =
                                Picking.End(index)
                        }
                    }
                    LinkRow(
                        title =
                            stringResource(
                                R.string.apps_in_this_time,
                                block.appsList?.let { presets.appsList(it).name.text() }
                                    ?: stringResource(R.string.usual_apps_list),
                            ),
                        icon = AppIcons.Apps,
                        iconColor = AppColors.blue,
                        onClick = { listFor = index },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
                ActionButton(AppIcons.Add, AppColors.cyan, stringResource(R.string.add_internet_time), {
                    setBlocks(blocks + newBlock(plan))
                }, Modifier.weight(1f))
                ActionButton(AppIcons.ContentCut, AppColors.orange, stringResource(R.string.add_a_break), {
                    cutting =
                        true
                }, Modifier.weight(1f), blocks.isNotEmpty())
            }
        }

        picking?.let { pick ->
            val (index, isStart) =
                when (pick) {
                    is Picking.Start -> pick.index to true
                    is Picking.End -> pick.index to false
                }
            val block = blocks.getOrNull(index)
            if (block == null) {
                picking = null
            } else {
                TimeSheet(
                    title = stringResource(if (isStart) R.string.starts_at else R.string.ends_at),
                    initial = if (isStart) block.start else block.end,
                    range = if (isStart) 0..(block.end - 1) else (block.start + 1)..Mark.MARKS_PER_DAY,
                    onCancel = { picking = null },
                ) { mark ->
                    setBlocks(replace(blocks, index, if (isStart) block.copy(start = mark) else block.copy(end = mark)))
                    picking = null
                }
            }
        }
        if (cutting) {
            BreakSheet(plan = plan, onCancel = { cutting = false }) { start, marks ->
                setBlocks(plan.with(start, marks, allowed = false).blocks)
                cutting = false
            }
        }
        listFor?.let { index ->
            val block = blocks.getOrNull(index)
            if (block == null) {
                listFor = null
            } else {
                val choose: (String?) -> Unit = { list ->
                    setBlocks(replace(blocks, index, block.copy(appsList = list)))
                    listFor = null
                }
                Sheet(
                    onDismiss = { listFor = null },
                    title = stringResource(R.string.which_apps_this_time),
                    icon = AppIcons.Apps,
                    color = AppColors.blue,
                    subtitle =
                        stringResource(
                            R.string.internet_from_to,
                            format.mark(block.start),
                            format.mark(block.end),
                        ),
                ) {
                    OptionCard(
                        AppIcons.Apps,
                        AppColors.slate,
                        stringResource(R.string.usual_apps_list),
                        block.appsList == null,
                        { choose(null) },
                    )
                    for (list in presets.appsLists) {
                        val style = Categories.ofList(list.id)
                        OptionCard(
                            style.icon,
                            style.color,
                            list.name.text(),
                            block.appsList == list.id,
                            { choose(list.id) },
                        )
                    }
                }
            }
        }
    }
}

// When a time starts or ends: a small label over the time, on a soft background. Tapping it opens the time sheet.
@Composable
private fun TimePill(
    label: String,
    time: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(palette.limited.copy(alpha = 0.5f))
                .clickable(onClick = onClick)
                .padding(vertical = Dimens.row),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, color = palette.muted, fontSize = Dimens.label)
        Text(text = time, color = palette.brand, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// Choosing a time in half hours: the time in large type between − and + (half an hour each),
// and a row of whole hours to jump to. The row starts near the current time.
@Composable
private fun TimeSheet(
    title: String,
    initial: Int,
    range: IntRange,
    onCancel: () -> Unit,
    onDone: (Int) -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    var mark by rememberSaveable { mutableIntStateOf(initial.coerceIn(range)) }
    val hours = (0..HOURS_PER_DAY).filter { it * 2 in range }
    val list =
        rememberLazyListState(
            initialFirstVisibleItemIndex = (hours.indexOfFirst { it * 2 >= mark } - 2).coerceAtLeast(0),
        )
    Sheet(onDismiss = onCancel, title = title, icon = AppIcons.AccessTime, color = AppColors.cyan) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundStep(AppIcons.Remove, mark - 1 in range) { mark -= 1 }
            Text(
                text = format.mark(mark),
                color = palette.text,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            RoundStep(AppIcons.Add, mark + 1 in range) { mark += 1 }
        }
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
            items(hours) { hour ->
                val chosen = mark == hour * 2
                Text(
                    text = format.mark(hour * 2),
                    color = if (chosen) AppColors.onColor else palette.text,
                    fontSize = Dimens.caption,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (chosen) palette.brand else palette.limited.copy(alpha = 0.6f))
                            .clickable { mark = hour * 2 }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        ButtonPair(stringResource(R.string.cancel), onCancel, stringResource(R.string.save), { onDone(mark) })
    }
}

// A round − or + beside a large number.
@Composable
private fun RoundStep(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        modifier =
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (enabled) palette.brand.copy(alpha = 0.12f) else palette.limited.copy(alpha = 0.4f))
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) palette.brand else palette.muted, modifier = Modifier.size(26.dp))
    }
}

private const val HOURS_PER_DAY = 24

// On the day editor: a break splits an internet time in two: when it starts and how long, with the day redrawn as it would be.
@Composable
private fun BreakSheet(
    plan: DayPlan,
    onCancel: () -> Unit,
    onCut: (Int, Int) -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val first = plan.blocks.firstOrNull()
    var start by rememberSaveable { mutableIntStateOf(first?.let { (it.start + it.end) / 2 } ?: 0) }
    var marks by rememberSaveable { mutableIntStateOf(2) }
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    val preview = plan.with(start, marks, allowed = false)
    if (pickingStart) {
        TimeSheet(stringResource(R.string.break_from), start, 0 until Mark.MARKS_PER_DAY, { pickingStart = false }) {
            start = it
            pickingStart = false
        }
        return
    }
    Sheet(
        onDismiss = onCancel,
        title = stringResource(R.string.add_a_break),
        icon = AppIcons.ContentCut,
        color = AppColors.orange,
    ) {
        TimePill(
            stringResource(R.string.break_from),
            format.mark(start),
            Modifier.fillMaxWidth(),
        ) { pickingStart = true }
        Text(text = stringResource(R.string.break_length), color = palette.muted, fontSize = Dimens.caption)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
            for (length in BREAK_LENGTHS) {
                ChoiceTile(
                    format.shortDuration(length),
                    AppColors.orange,
                    { marks = length },
                    Modifier.weight(1f),
                    selected =
                        length == marks,
                )
            }
        }
        Strip(plan = preview, differences = Difference.between(plan, preview), height = 14.dp)
        ButtonPair(
            stringResource(R.string.cancel),
            onCancel,
            stringResource(R.string.add_a_break),
            { onCut(start, marks) },
        )
    }
}

private fun replace(
    blocks: List<Block>,
    index: Int,
    block: Block,
): List<Block> = blocks.toMutableList().also { it[index] = block }

// A new hour where the day has room: after the last internet time, or from 16:00.
private fun newBlock(plan: DayPlan): Block {
    val start =
        (plan.blocks.maxOfOrNull { it.end } ?: NEW_BLOCK_START).coerceAtMost(Mark.MARKS_PER_DAY - NEW_BLOCK_MARKS)
    return Block(start, start + NEW_BLOCK_MARKS)
}
