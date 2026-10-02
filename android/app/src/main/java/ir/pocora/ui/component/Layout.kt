package ir.pocora.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.ui.AppColors
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.rememberFormat

// The frames every screen is built from: the screen, the hero, the card, the sheet, the bottom bar.

// The soft gradient behind every screen.
@Composable
fun Background(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        modifier =
            modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(palette.backgroundTop, palette.background)),
            ),
        content = content,
    )
}

// Every screen, top to bottom: the top bar, then the content in one scrolling column, then what stays at the bottom.
// Without a title, the content starts at the top, as Home does with its own header.
@Composable
fun Screen(
    title: String?,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
    // When the screen shows only an empty state: no scrolling, and the content centred in the full height.
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    Background {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            if (title != null || onBack != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.small),
                ) {
                    if (onBack != null) BackButton(onBack)
                    Text(
                        text = title ?: "",
                        color = palette.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = if (onBack == null) 8.dp else 0.dp),
                    )
                    trailing()
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .let { if (centered) it else it.verticalScroll(rememberScrollState()) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement =
                    if (centered) {
                        Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterVertically,
                        )
                    } else {
                        Arrangement.spacedBy(Dimens.inside)
                    },
                content = content,
            )
            bottom?.invoke()
        }
    }
}

// A coloured top with a large icon and the screen's title, over a white sheet that holds the rest.
// The small icons float around the large one, like a simple illustration.
@Composable
fun Hero(
    color: Color,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    around: List<ImageVector> = emptyList(),
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Background {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color)),
                            RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp),
                        ).statusBarsPadding()
                        .padding(top = 8.dp, bottom = 56.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    trailing()
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 36.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.row),
                ) {
                    Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        around.take(3).forEachIndexed { index, small ->
                            val (x, y) = listOf(-58 to -40, 60 to -30, 50 to 52)[index]
                            Box(
                                modifier =
                                    Modifier
                                        .offset(x.dp, y.dp)
                                        .size(40.dp)
                                        .background(AppColors.onColor.copy(alpha = 0.22f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(small, null, tint = AppColors.onColor, modifier = Modifier.size(22.dp))
                            }
                        }
                        Box(
                            modifier =
                                Modifier
                                    .size(96.dp)
                                    .shadow(16.dp, CircleShape)
                                    .background(AppColors.onColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(52.dp))
                        }
                    }
                    Text(
                        text = title,
                        color = AppColors.onColor,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = AppColors.onColor.copy(alpha = 0.9f),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().offset(y = (-32).dp).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.inside),
                content = content,
            )
        }
    }
}

private val CARD_SHAPE = RoundedCornerShape(Dimens.cardCorner)

// A white panel with a soft shadow, holding one idea. Clickable when it leads somewhere.
@Composable
fun Card(
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.card,
    onClick: (() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(10.dp, CARD_SHAPE, ambientColor = palette.shadow, spotColor = palette.shadow)
                .clip(CARD_SHAPE)
                .background(color)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(Dimens.edge),
        verticalArrangement = Arrangement.spacedBy(Dimens.row),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

// True while the parent app's lock covers the screens. A sheet opens a window of its own, above the lock,
// so it waits, hidden, until the password is typed.
val LocalLocked = compositionLocalOf { false }

// A card that slides up from the bottom over a dimmed screen. Swiping it down, tapping outside it, or Back
// closes it, through onDismiss. It scrolls when it is taller than the screen, and makes room for the keyboard.
// Every sheet starts with the same header: an icon tile, a title, and a line under it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(
    onDismiss: () -> Unit,
    title: String? = null,
    icon: ImageVector? = null,
    color: Color = LocalPalette.current.brand,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalLocked.current) return
    val palette = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Dimens.sheetCorner, topEnd = Dimens.sheetCorner),
        containerColor = palette.card,
        scrimColor = AppColors.scrim,
        dragHandle = {
            Box(
                modifier =
                    Modifier
                        .padding(top = Dimens.row, bottom = Dimens.tiny)
                        .width(40.dp)
                        .height(5.dp)
                        .background(palette.limited, RoundedCornerShape(3.dp)),
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = Dimens.section, end = Dimens.section, top = Dimens.small, bottom = Dimens.section),
            verticalArrangement = Arrangement.spacedBy(Dimens.inside),
        ) {
            if (title != null) {
                if (icon !=
                    null
                ) {
                    IconHeader(icon, color, title, subtitle = subtitle, size = 48.dp)
                } else {
                    CardTitle(title, subtitle)
                }
            }
            content()
        }
    }
}

class BarItem(
    val icon: ImageVector,
    val label: String,
    val badge: Int = 0,
)

// Each app's main places: an icon over a label. The chosen one sits in a soft violet pill.
@Composable
fun BottomBar(
    items: List<BarItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(topStart = Dimens.cardCorner, topEnd = Dimens.cardCorner)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, shape, spotColor = palette.shadow, ambientColor = palette.shadow)
                .background(palette.card, shape)
                .topHairline(palette.limited, Dimens.cardCorner)
                .navigationBarsPadding()
                .padding(horizontal = Dimens.small, vertical = Dimens.small),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEachIndexed { index, item ->
            val chosen = index == selected
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(index) }
                        .padding(vertical = Dimens.tiny),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.tiny),
            ) {
                Box {
                    Box(
                        modifier =
                            Modifier
                                .background(
                                    if (chosen) palette.brand.copy(alpha = 0.14f) else palette.card,
                                    RoundedCornerShape(50),
                                ).padding(horizontal = 18.dp, vertical = Dimens.tiny),
                    ) {
                        Icon(
                            item.icon,
                            null,
                            tint = if (chosen) palette.brand else palette.muted,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    if (item.badge > 0) {
                        Text(
                            text = rememberFormat().number(item.badge),
                            color = AppColors.onColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .background(palette.alert, CircleShape)
                                    .padding(horizontal = 5.dp),
                        )
                    }
                }
                Text(
                    text = item.label,
                    color = if (chosen) palette.brand else palette.muted,
                    fontSize = Dimens.label,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// The head of a card, sheet or option: an icon tile, a bold title with an optional chip after it,
// a muted line under it, and something at the end. One line for the title, so heads line up.
@Composable
fun IconHeader(
    icon: ImageVector,
    color: Color,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    chip: (@Composable () -> Unit)? = null,
    size: Dp = 44.dp,
    titleSize: TextUnit = Dimens.heading,
    // A status sentence may need two lines; names and labels never do.
    titleLines: Int = 1,
    below: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)? = null,
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.row),
    ) {
        IconTile(icon, color, size)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.small),
            ) {
                Text(
                    text = title,
                    color = palette.text,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = titleLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                chip?.invoke()
            }
            if (subtitle != null) Text(text = subtitle, color = palette.muted, fontSize = Dimens.caption)
            below?.invoke()
        }
        end?.invoke()
    }
}

// The title of a card's content, with a muted line under it.
@Composable
fun CardTitle(
    title: String,
    hint: String? = null,
) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, color = palette.text, fontSize = Dimens.body, fontWeight = FontWeight.Bold)
        if (hint != null) Text(text = hint, color = palette.muted, fontSize = Dimens.caption)
    }
}

// A hairline along a rounded top edge, corners included, since the bar is as white as the sheet above it.
// A straight line across would stick out past the rounded corners.
private fun Modifier.topHairline(
    color: Color,
    corner: Dp,
): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        val inset = stroke / 2
        val radius = corner.toPx()
        val path =
            Path().apply {
                moveTo(inset, radius)
                arcTo(Rect(inset, inset, radius * 2, radius * 2), 180f, 90f, false)
                lineTo(size.width - radius, inset)
                arcTo(Rect(size.width - radius * 2, inset, size.width - inset, radius * 2), 270f, 90f, false)
            }
        drawPath(path, color, style = Stroke(stroke))
    }
