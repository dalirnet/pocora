package ir.pocora.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.R
import ir.pocora.ui.AppColors
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette

// Everything that is tapped: buttons, chips, small icons and arrows.

// The one main action of a screen: in the brand colour and rounded. The quiet one is for the choice that changes nothing.
// Give it Modifier.fillMaxWidth() to span the screen.
@Composable
fun MainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    quiet: Boolean = false,
    // For a step that is hard to undo, such as removing or turning off: orange instead of the brand colour.
    danger: Boolean = false,
) {
    val palette = LocalPalette.current
    val solid = enabled && !quiet
    val main = if (danger) palette.alert else palette.brand
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = 52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        solid -> main

                        // A main action not ready yet: still its colour, but faded, so it never looks like Cancel.
                        !enabled && !quiet -> main.copy(alpha = 0.3f)

                        else -> palette.limited
                    },
                ).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color =
                when {
                    solid -> AppColors.onColor
                    !enabled && !quiet -> AppColors.onColor.copy(alpha = 0.7f)
                    enabled -> palette.text
                    else -> palette.muted
                },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            // A button's label never wraps; a long one is cut short.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// The two answers at the bottom of a sheet, side by side and the same width: the quiet one first.
@Composable
fun ButtonPair(
    quiet: String,
    onQuiet: () -> Unit,
    main: String,
    onMain: () -> Unit,
    mainEnabled: Boolean = true,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement =
            Arrangement
                .spacedBy(Dimens.row),
    ) {
        MainButton(text = quiet, onClick = onQuiet, quiet = true, modifier = Modifier.weight(1f))
        MainButton(
            text = main,
            onClick = onMain,
            enabled = mainEnabled,
            danger = danger,
            modifier = Modifier.weight(1f),
        )
    }
}

// A small rounded button, for quick changes and links inside a card.
@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val palette = LocalPalette.current
    Text(
        text = text,
        color =
            when {
                !enabled -> palette.muted
                filled -> AppColors.onColor
                else -> palette.text
            },
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(if (filled && enabled) palette.brand else palette.limited)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// An action inside a card: a small icon tile and what it does, on a soft background. Two fit side by side.
@Composable
fun ActionButton(
    icon: ImageVector,
    color: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(palette.limited.copy(alpha = 0.5f))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else 0.4f)
                .padding(Dimens.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, color, 32.dp)
        Text(
            text = label,
            color = palette.text,
            fontSize = Dimens.caption,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// A short answer as a tile, several side by side at the same width: "30m", "1h", "1.5h".
// One line only. Chosen, it fills with its colour.
@Composable
fun ChoiceTile(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(if (selected) color else color.copy(alpha = 0.12f))
                .border(1.5.dp, if (selected) color else Color.Transparent, shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else 0.4f)
                .padding(vertical = 18.dp, horizontal = Dimens.tiny),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) AppColors.onColor else color,
            fontSize = Dimens.heading,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// A round icon button without a background, for the top bar and for a row's own action.
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        modifier =
            Modifier
                .size(Dimens.headerButton)
                .clip(CircleShape)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = palette.text,
            modifier = Modifier.size(22.dp),
        )
    }
}

// An icon on a coloured hero, drawn white.
@Composable
fun WhiteIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalPalette provides LocalPalette.current.copy(text = AppColors.onColor)) {
        IconAction(icon, label, onClick)
    }
}

// A drawn chevron. It points to where the screen came from: left in English, right in Persian.
@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.back), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Chevron(16.dp, LocalPalette.current.text, 2.dp, forward = false, inset = 0.25f)
    }
}

// A small chevron pointing forward: right in English, left in Persian.
@Composable
fun Arrow() = Chevron(10.dp, LocalPalette.current.muted, 1.6.dp, forward = true, inset = 0.2f)

// Two short strokes meeting in a point. Forward is right in English and left in Persian; inset keeps the ends
// that far from the box's sides.
@Composable
private fun Chevron(
    size: Dp,
    color: Color,
    stroke: Dp,
    forward: Boolean,
    inset: Float,
) {
    val pointsRight = forward == (LocalLayoutDirection.current == LayoutDirection.Ltr)
    Canvas(modifier = Modifier.size(size)) {
        val tip = this.size.width * if (pointsRight) 1 - inset else inset
        val tail = this.size.width * if (pointsRight) inset else 1 - inset
        val width = stroke.toPx()
        drawLine(color, Offset(tail, 0f), Offset(tip, this.size.height / 2), width, StrokeCap.Round)
        drawLine(color, Offset(tip, this.size.height / 2), Offset(tail, this.size.height), width, StrokeCap.Round)
    }
}

// Two or three options side by side. The chosen one is filled.
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    Row(modifier = modifier.clip(RoundedCornerShape(percent = 50)).background(palette.limited).padding(3.dp)) {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            Text(
                text = option,
                color = if (chosen) AppColors.onColor else palette.text,
                fontSize = Dimens.caption,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (chosen) palette.brand else palette.limited)
                        .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(index) }
                        .padding(vertical = 8.dp),
            )
        }
    }
}

private val CHIP_HEIGHT = 24.dp

// A small rounded label with a fixed height, so chips line up with each other and with the text beside them.
// The colour is the brand unless given: a soft fill with the text in the full colour.
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.brand,
    icon: ImageVector? = null,
) {
    Row(
        modifier =
            modifier
                .height(CHIP_HEIGHT)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.tiny),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            color = color,
            fontSize = Dimens.label,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// A fact about something, as a muted icon and a word or two: "6 to 9 years", "9 of 12 kinds".
@Composable
fun Tag(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.tiny),
    ) {
        Icon(icon, null, tint = palette.muted, modifier = Modifier.size(15.dp))
        Text(
            text = text,
            color = palette.muted,
            fontSize = Dimens.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
