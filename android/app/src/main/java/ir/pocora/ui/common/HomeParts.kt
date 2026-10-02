package ir.pocora.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pocora.R
import ir.pocora.model.DayPlan
import ir.pocora.model.Difference
import ir.pocora.model.Mark
import ir.pocora.model.Snapshot
import ir.pocora.preset.AppGroup
import ir.pocora.preset.Presets
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.Format
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.Categories
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.FeatureTile
import ir.pocora.ui.component.HourAxis
import ir.pocora.ui.component.IconHeader
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.StackedBar
import ir.pocora.ui.component.Strip
import ir.pocora.ui.rememberFormat
import ir.pocora.ui.text
import java.time.LocalDate
import java.time.LocalDateTime

private const val TILES_PER_ROW = 4
private val SECTION_TITLE_HEIGHT = 48.dp
private const val CATEGORIES_SHOWN = 3
private const val MINUTE_MILLISECONDS = 60_000L

// Home in both apps: a gradient top layer with who, how they are now and what can be done,
// then a white sheet with rounded corners for what is worth knowing today, then the bottom bar.
@Composable
fun LayeredHome(
    top: @Composable ColumnScope.() -> Unit,
    sheet: @Composable ColumnScope.() -> Unit,
    bottom: @Composable () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    var topHeight by remember { mutableIntStateOf(0) }
    Box(modifier = Modifier.fillMaxSize().background(palette.card)) {
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                // The sheet reaches at least the bottom of the screen, and an empty state on it centres in what is left.
                val sheetHeight = maxHeight - with(density) { topHeight.toDp() } + Dimens.sheetCorner
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged { topHeight = it.height }
                                .background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.background)))
                                .statusBarsPadding()
                                .padding(
                                    start = Dimens.edge,
                                    end = Dimens.edge,
                                    top = Dimens.inside,
                                    bottom =
                                        Dimens.sheetCorner + Dimens.section,
                                ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.section),
                        content = top,
                    )
                    CompositionLocalProvider(
                        LocalSheetSpace provides (sheetHeight - Dimens.section * 2).coerceAtLeast(0.dp),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = sheetHeight)
                                    .offset(y = -Dimens.sheetCorner)
                                    .background(
                                        palette.card,
                                        RoundedCornerShape(topStart = Dimens.sheetCorner, topEnd = Dimens.sheetCorner),
                                    ).padding(horizontal = Dimens.edge, vertical = Dimens.section),
                            verticalArrangement = Arrangement.spacedBy(Dimens.inside),
                            content = sheet,
                        )
                    }
                }
            }
            bottom()
        }
        overlay()
    }
}

// How tall the white sheet of Home is inside its padding, so an empty state can centre in it.
val LocalSheetSpace = compositionLocalOf { 0.dp }

// The top of Home: a picture, a name, and a dot that says whether the other phone is in touch.
@Composable
fun HomeHeader(
    picture: @Composable () -> Unit,
    title: String,
    online: Boolean,
    status: String,
    onClick: (() -> Unit)? = null,
    action: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.row)) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = onClick != null) { onClick?.invoke() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.row),
        ) {
            picture()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, color = palette.text, fontSize = Dimens.title, fontWeight = FontWeight.Bold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(
                                    8.dp,
                                ).background(if (online) palette.done else palette.muted, CircleShape),
                    )
                    Text(text = status, color = palette.muted, fontSize = Dimens.caption)
                }
            }
        }
        action()
    }
}

// The status card's sentence, the same rule in both apps: with or without internet, and until when.
// The child app speaks to the child, the parent app about them.
@Composable
fun internetSentence(
    allowed: Boolean,
    until: LocalDateTime?,
    toChild: Boolean,
): String {
    val format = rememberFormat()
    return when {
        allowed && until != null -> {
            stringResource(
                if (toChild) R.string.you_have_internet_until else R.string.has_internet_until,
                format.time(until),
            )
        }

        allowed -> {
            stringResource(if (toChild) R.string.you_have_internet else R.string.has_internet)
        }

        until != null -> {
            stringResource(
                if (toChild) R.string.notify_no_internet_until else R.string.no_internet_until,
                format.time(until),
            )
        }

        else -> {
            stringResource(if (toChild) R.string.no_internet_now else R.string.no_internet)
        }
    }
}

// One sentence about now, a line under it, and the day's internet times as a strip with its hours.
@Composable
fun StatusPanel(
    icon: ImageVector,
    color: Color,
    title: String,
    detail: String?,
    plan: DayPlan?,
    // Where today differs from the schedule's own day: what a parent added or took away.
    differences: List<Difference> = emptyList(),
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Card {
        IconHeader(icon, color, title, subtitle = detail, titleLines = 2)
        if (plan != null) {
            val now = LocalDateTime.now()
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.tiny)) {
                Strip(
                    plan = plan,
                    differences = differences,
                    height = 8.dp,
                    now =
                        Mark.of(now.hour, 0) + now.minute / Mark.DURATION_MINUTES.toFloat(),
                )
                HourAxis()
            }
        }
        extra()
    }
}

// One feature tile of a Home grid.
class Tile(
    val icon: ImageVector,
    val color: Color,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

// The features. A short last row keeps its tiles at the same width.
@Composable
fun TileGrid(tiles: List<Tile>) {
    // As many to a row as there are, up to four, so two or three tiles spread across the width.
    val perRow = tiles.size.coerceIn(1, TILES_PER_ROW)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.row)) {
        for (row in tiles.chunked(perRow)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (tile in row) {
                    FeatureTile(
                        tile.icon,
                        tile.color,
                        tile.label,
                        tile.onClick,
                        Modifier.weight(1f),
                        tile.enabled,
                    )
                }
                repeat(perRow - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

// Today's screen time on the sheet: the total and data, a bar split by kind of app, and the top kinds.
@Composable
fun ScreenTimeSection(
    presets: Presets,
    snapshot: Snapshot,
    onViewAll: () -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val today = LocalDate.now().toEpochDay()
    val usage = snapshot.childUsage().filter { it.date == today }
    val groupOf = snapshot.apps.associate { it.`package` to it.group }
    val byGroup =
        usage
            .groupBy { groupOf[it.`package`] ?: AppGroup.OTHER }
            .mapValues { (_, days) -> days.sumOf { it.screenMilliseconds } }
            // Less than a minute shows as nothing, so it is left out.
            .filterValues { it >= MINUTE_MILLISECONDS }
            .toList()
            .sortedByDescending { it.second }
    val total = byGroup.sumOf { it.second }
    val bytes = snapshot.days.firstOrNull { it.date == today }?.totalBytes ?: 0

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.screen_time_today),
            color = palette.text,
            fontSize = Dimens.heading,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.view_all),
            color = palette.brand,
            fontSize = Dimens.caption,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onViewAll).padding(Dimens.tiny),
        )
    }
    if (total == 0L) {
        // Centred in the space under the title, which is the rest of the sheet.
        Box(
            modifier =
                Modifier.fillMaxWidth().heightIn(
                    min = (LocalSheetSpace.current - SECTION_TITLE_HEIGHT).coerceAtLeast(0.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                AppIcons.HourglassEmpty,
                AppColors.teal,
                stringResource(R.string.no_screen_time_yet),
                compact = true,
            )
        }
        return
    }
    ScreenTotal(format, total, bytes)
    StackedBar(byGroup.map { (group, time) -> time.toFloat() to Categories.of(group).color })
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.row)) {
        for ((group, time) in byGroup.take(CATEGORIES_SHOWN)) {
            val style = Categories.of(group)
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.row),
            ) {
                IconTile(style.icon, style.color, Dimens.rowIcon)
                Text(
                    text = presets.group(group).name.text(),
                    color = palette.text,
                    fontSize = Dimens.body,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = format.duration(time),
                    color = palette.text,
                    fontSize = Dimens.caption,
                    fontWeight = FontWeight.Bold,
                )
                Box(modifier = Modifier.width(16.dp).height(4.dp).background(style.color, RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
private fun ScreenTotal(
    format: Format,
    total: Long,
    bytes: Long,
) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
        Text(
            text = format.duration(total),
            color = palette.text,
            fontSize = Dimens.number,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.data_used_amount, format.size(bytes)),
            color = palette.muted,
            fontSize = Dimens.caption,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}
