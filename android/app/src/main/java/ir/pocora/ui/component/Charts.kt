package ir.pocora.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.model.DayPlan
import ir.pocora.model.Difference
import ir.pocora.model.Mark
import ir.pocora.ui.AppColors
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.rememberFormat

// Pictures of time and data: the day strip, the week, the bars, and the bars split by kind.

// A day as one smooth track: internet times are rounded capsules on it, a time the parent added is violet,
// a time the parent took away is orange, and now is a thin line at the exact minute.
// Time always runs left to right, midnight to midnight, in both languages, like a clock's digits.
// Now is in marks and may fall inside one: 17:08 is 34.27.
@Composable
fun Strip(
    plan: DayPlan,
    modifier: Modifier = Modifier,
    differences: List<Difference> = emptyList(),
    height: Dp = 12.dp,
    now: Float? = null,
) {
    val palette = LocalPalette.current
    // Room above and below for the now line, which is a little taller than the track.
    val overhang = if (now != null) 4.dp else 0.dp
    Canvas(modifier = modifier.fillMaxWidth().height(height + overhang * 2)) {
        val top = overhang.toPx()
        val trackHeight = height.toPx()
        val unit = size.width / Mark.MARKS_PER_DAY
        val radius = CornerRadius(trackHeight / 2)

        drawRoundRect(palette.limited, Offset(0f, top), Size(size.width, trackHeight), radius)

        val runs = runsOf(plan)
        for (run in runs) {
            drawRoundRect(
                palette.allowed,
                Offset(run.first * unit, top),
                Size((run.last - run.first) * unit, trackHeight),
                radius,
            )
        }
        // A change has its own solid colour over the blue or the empty track: violet where the parent added internet,
        // orange where they took it away.
        for (difference in differences) {
            val x = difference.start * unit
            val width = (difference.end - difference.start) * unit
            // Added time lies inside a blue capsule, so it is cut to that capsule's shape and joins it without a notch.
            // Taken-away time stands alone on the track, as its own capsule.
            val run = runs.firstOrNull { difference.added && difference.start >= it.first && difference.end <= it.last }
            if (run != null) {
                val capsule =
                    Path().apply {
                        addRoundRect(
                            RoundRect(run.first * unit, top, run.last * unit, top + trackHeight, radius),
                        )
                    }
                clipPath(capsule) { drawRect(AppColors.changeAdded, Offset(x, top), Size(width, trackHeight)) }
            } else {
                drawRoundRect(
                    if (difference.added) AppColors.changeAdded else AppColors.changeCut,
                    Offset(x, top),
                    Size(width, trackHeight),
                    radius,
                )
            }
        }
        if (now != null) {
            // A thin line with a card-coloured edge, so it never hides which side of a change it is on.
            val x = (now * unit).coerceIn(0f, size.width)
            val lineWidth = 2.dp.toPx()
            val edge = 1.dp.toPx()
            drawRoundRect(
                palette.card,
                Offset(x - lineWidth / 2 - edge, 0f),
                Size(lineWidth + edge * 2, size.height),
                CornerRadius(lineWidth),
            )
            drawRoundRect(
                palette.brand,
                Offset(x - lineWidth / 2, edge),
                Size(lineWidth, size.height - edge * 2),
                CornerRadius(lineWidth / 2),
            )
        }
    }
}

// The day's internet times as runs of marks, start included and end excluded.
private fun runsOf(plan: DayPlan): List<IntRange> = DayPlan.of(plan.allowed).blocks.map { it.start..it.end }

// The hours over a strip: 0, 6, 12, 18, 24, each centred over its place on the strip. Left to right, as the strip.
@Composable
fun HourAxis(modifier: Modifier = Modifier) {
    val format = rememberFormat()
    val hours = listOf(0, 6, 12, 18, 24)
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            for (hour in hours) {
                Text(text = format.number(hour), color = LocalPalette.current.muted, fontSize = 11.sp)
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val center = width * hours[index] / HOURS_PER_DAY
                placeable.place((center - placeable.width / 2).coerceIn(0, width - placeable.width), 0)
            }
        }
    }
}

private const val HOURS_PER_DAY = 24

// One day of the week grid.
class WeekRow(
    val label: String,
    val plan: DayPlan,
    val differences: List<Difference> = emptyList(),
    val holiday: Boolean = false,
    val today: Boolean = false,
)

// Seven strips under an hour axis, Saturday on top. Tapping a day opens it, a long press is not needed.
@Composable
fun WeekGrid(
    rows: List<WeekRow>,
    modifier: Modifier = Modifier,
    onDay: ((Int) -> Unit)? = null,
) {
    val palette = LocalPalette.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "", modifier = Modifier.width(LABEL_WIDTH))
            HourAxis(modifier = Modifier.weight(1f))
        }
        rows.forEachIndexed { index, row ->
            val color: Color =
                when {
                    row.holiday -> palette.alert
                    row.today -> palette.allowed
                    else -> palette.text
                }
            Row(
                modifier =
                    Modifier
                        .let { if (onDay != null) it.clickable { onDay(index) } else it }
                        .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = row.label, color = color, fontSize = Dimens.caption, modifier = Modifier.width(LABEL_WIDTH))
                Strip(plan = row.plan, differences = row.differences, modifier = Modifier.weight(1f), height = 14.dp)
            }
        }
    }
}

private val LABEL_WIDTH = 36.dp

// One bar of the chart. A Limited one is a pale track, and a flagged one has an alert over it.
// A bar with nothing to show yet, such as a day still to come, is a pale track too.
class Bar(
    val value: Long,
    val allowed: Boolean = true,
    val alert: Boolean = false,
    val noData: Boolean = false,
)

// Rounded bars over a time axis, in a Row that follows the layout direction: weekday bars run right to left in Persian,
// and a caller that wants hours left to right sets the direction itself. The line is the quota, when there is one.
@Composable
fun BarChart(
    bars: List<Bar>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    line: Long? = null,
) {
    val palette = LocalPalette.current
    val highest = bars.maxOfOrNull { it.value } ?: 0L
    // A limit far above the bars would flatten them, so then it is left out. The caller writes its value above.
    val drawnLine = line?.takeIf { highest * LINE_RATIO >= it }
    val top = maxOf(highest, drawnLine ?: 0L, 1L)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.tiny)) {
        Box(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            if (drawnLine != null) {
                Box(
                    modifier =
                        Modifier
                            .padding(top = CHART_HEIGHT * (1f - drawnLine.toFloat() / top))
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.muted),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(if (bars.size > 10) 2.dp else 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (bar in bars) {
                    // Each slot is a faint full-height track; the bar inside it is a rounded capsule.
                    // Internet time is cyan, no internet a pale lilac track, an alert an orange dot on top.
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (bar.allowed &&
                                        !bar.noData
                                    ) {
                                        palette.allowed.copy(alpha = 0.08f)
                                    } else {
                                        palette.limited.copy(alpha = 0.45f)
                                    },
                                    RoundedCornerShape(50),
                                ),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val share = (bar.value.toFloat() / top).coerceIn(0f, 1f)
                        if (share > 0f) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(maxOf(CHART_HEIGHT * share, 4.dp))
                                        .background(
                                            if (bar.allowed) palette.allowed else palette.muted,
                                            RoundedCornerShape(50),
                                        ),
                            )
                        }
                        if (bar.alert) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 2.dp)
                                        .size(5.dp)
                                        .background(palette.alert, RoundedCornerShape(50)),
                            )
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            for (label in labels) {
                Text(
                    text = label,
                    color = palette.muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val CHART_HEIGHT = 120.dp
private const val LINE_RATIO = 4

// One bar split into coloured parts, each the share of one kind of app.
@Composable
fun StackedBar(
    parts: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(
                    12.dp,
                ).clip(RoundedCornerShape(6.dp))
                .background(LocalPalette.current.limited),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (total > 0f) {
            for ((value, color) in parts) {
                if (value > 0f) Box(modifier = Modifier.weight(value / total).fillMaxHeight().background(color))
            }
        }
    }
}

// A thin bar for a share used: data of a half hour, of a month.
@Composable
fun ProgressLine(
    share: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.allowed,
    height: Dp = 6.dp,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(height / 2)
    Box(modifier = modifier.fillMaxWidth().height(height).background(palette.limited, shape)) {
        if (share > 0f) {
            Box(modifier = Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(height).background(color, shape))
        }
    }
}

// A thin number over a small label, and a note under them when there is one.
@Composable
fun NumberPair(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.text,
    note: String? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Light)
        Text(text = label, color = LocalPalette.current.muted, fontSize = Dimens.label)
        if (note != null) Text(text = note, color = LocalPalette.current.muted, fontSize = Dimens.caption)
    }
}
