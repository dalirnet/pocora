package ir.pocora.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import ir.pocora.R
import ir.pocora.preset.AppGroup
import ir.pocora.preset.SchedulePreset
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import kotlin.math.ceil

// A bold heading over a group of rows or cards.
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Dimens.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = palette.text,
            fontSize = Dimens.heading,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) Text(text = trailing, color = palette.muted, fontSize = Dimens.caption)
    }
}

// An app: its kind's icon tile, its name over a thin line for its share, and two numbers at the end.
@Composable
fun AppRow(
    name: String,
    group: String,
    share: Float,
    top: String,
    bottom: String?,
    modifier: Modifier = Modifier,
    chip: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val style = Categories.of(group)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.row),
    ) {
        IconTile(style.icon, style.color, Dimens.rowIcon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = name,
                    color = palette.text,
                    fontSize = Dimens.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (chip != null) Chip(chip)
            }
            ProgressLine(share = share, color = style.color, height = 4.dp)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = top, color = palette.text, fontSize = Dimens.caption, fontWeight = FontWeight.Bold)
            if (bottom != null) Text(text = bottom, color = palette.muted, fontSize = Dimens.label)
        }
    }
}

// A setting that is on or off.
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconColor: Color = LocalPalette.current.brand,
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.row),
    ) {
        if (icon != null) IconTile(icon, iconColor, Dimens.rowIcon)
        Text(text = title, color = palette.text, fontSize = Dimens.body, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = palette.brand,
                    checkedThumbColor = palette.card,
                    uncheckedTrackColor = palette.limited,
                    uncheckedThumbColor = palette.muted,
                    uncheckedBorderColor = palette.limited,
                ),
        )
    }
}

// A row that opens something: an icon tile or a colour mark, a title, a note, and an arrow.
@Composable
fun LinkRow(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = LocalPalette.current.brand,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = Dimens.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.row),
    ) {
        if (icon != null) IconTile(icon, iconColor, Dimens.rowIcon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = palette.text, fontSize = Dimens.body)
            if (subtitle != null) Text(text = subtitle, color = palette.muted, fontSize = Dimens.label)
        }
        if (note != null) Text(text = note, color = palette.muted, fontSize = Dimens.caption)
        Arrow()
    }
}

// One choice of several, as a card: its icon, title and details, and a clear chosen state, a violet edge and a tick.
// When choosing opens another screen instead, it ends in an arrow. More can follow under it, such as small strips.
@Composable
fun OptionCard(
    icon: ImageVector,
    color: Color,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    chip: String? = null,
    // Short facts under the title, in one row: an icon and a word or two each.
    tags: List<Pair<ImageVector, String>> = emptyList(),
    opensMore: Boolean = false,
    enabled: Boolean = true,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(Dimens.cardCorner)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(if (selected) 12.dp else 6.dp, shape, spotColor = palette.shadow, ambientColor = palette.shadow)
                .clip(shape)
                .background(palette.card)
                .border(2.dp, if (selected) palette.brand else Color.Transparent, shape)
                .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(Dimens.inside),
        verticalArrangement = Arrangement.spacedBy(Dimens.row),
    ) {
        IconHeader(
            icon = icon,
            color = color,
            title = title,
            subtitle = subtitle,
            titleSize = Dimens.body,
            chip = chip?.let { { Chip(it, color = AppColors.green, icon = AppIcons.Check) } },
            below =
                if (tags.isEmpty()) {
                    null
                } else {
                    {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.row),
                        ) {
                            for ((tagIcon, text) in tags) Tag(tagIcon, text)
                        }
                    }
                },
        ) {
            when {
                selected -> Icon(AppIcons.CheckCircle, null, tint = palette.brand, modifier = Modifier.size(26.dp))
                opensMore -> Arrow()
                else -> Box(modifier = Modifier.size(22.dp).border(2.dp, palette.limited, CircleShape))
            }
        }
        extra?.invoke(this)
    }
}

// The one action that stays at the bottom of a screen, above the system bar, on white with a hairline.
@Composable
fun BottomAction(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPalette.current
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.limited))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(palette.card)
                .navigationBarsPadding()
                .padding(horizontal = Dimens.edge, vertical = Dimens.row),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
        content = content,
    )
}

// A coloured rounded square with a white icon, lit from the top.
@Composable
fun IconTile(
    icon: ImageVector,
    color: Color,
    size: Dp = 36.dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.8f), color))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.onColor,
            modifier =
                Modifier.size(size * 0.5f),
        )
    }
}

// The app's own icon, built from its launcher layers: the gradient in the app's colour, and the shield-P on it.
// The launcher layers are 108 wide with 72 visible, so the mark is drawn half again as large as the tile.
@Composable
fun AppIcon(size: Dp = 36.dp) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(
                    Brush.linearGradient(listOf(colorResource(R.color.icon_start), colorResource(R.color.icon_end))),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * LAUNCHER_LAYER_SCALE),
        )
    }
}

private const val LAUNCHER_LAYER_SCALE = 1.5f

// One feature on Home: a large tile and its label under it.
@Composable
fun FeatureTile(
    icon: ImageVector,
    color: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else 0.4f)
                .padding(vertical = Dimens.tiny),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        Box(
            modifier =
                Modifier.shadow(
                    6.dp,
                    RoundedCornerShape(Dimens.tileCorner),
                    spotColor = color,
                    ambientColor = color,
                ),
        ) {
            IconTile(icon, color, Dimens.tile)
        }
        Text(
            text = label,
            color = palette.text,
            fontSize = Dimens.caption,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            maxLines = 2,
        )
    }
}

// The child's picture: the first letter of the name in a coloured circle.
@Composable
fun Avatar(
    name: String,
    size: Dp = 56.dp,
) {
    val colors = listOf(AppColors.violet, AppColors.pink, AppColors.teal, AppColors.orange, AppColors.blue)
    val color = colors[(name.hashCode() and Int.MAX_VALUE) % colors.size]
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color.copy(alpha = 0.7f), color))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = AppColors.onColor,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// Each kind of app has one icon and one colour, the same on every screen.
object Categories {
    class Style(
        val icon: ImageVector,
        val color: Color,
    )

    fun of(group: String): Style =
        when (group) {
            AppGroup.SCHOOL -> Style(AppIcons.School, AppColors.blue)
            AppGroup.LEARNING -> Style(AppIcons.MenuBook, AppColors.teal)
            AppGroup.KIDS -> Style(AppIcons.ChildCare, AppColors.pink)
            AppGroup.MESSAGING -> Style(AppIcons.Chat, AppColors.green)
            AppGroup.VIDEO -> Style(AppIcons.PlayCircle, AppColors.red)
            AppGroup.GAMES -> Style(AppIcons.SportsEsports, AppColors.violet)
            AppGroup.MUSIC -> Style(AppIcons.MusicNote, AppColors.orange)
            AppGroup.BROWSER -> Style(AppIcons.Language, AppColors.cyan)
            AppGroup.SOCIAL -> Style(AppIcons.Groups, AppColors.magenta)
            AppGroup.STORES -> Style(AppIcons.Storefront, AppColors.amber)
            AppGroup.DAILY_TOOLS -> Style(AppIcons.Build, AppColors.slate)
            AppGroup.SYSTEM -> Style(AppIcons.PhoneAndroid, AppColors.grey)
            else -> Style(AppIcons.Apps, AppColors.grey)
        }

    // Each schedule section has its own icon and colour.
    fun ofSection(section: String): Style =
        when (section) {
            SchedulePreset.SECTION_SCHOOL_YEAR -> Style(AppIcons.School, AppColors.blue)
            SchedulePreset.SECTION_HOLIDAYS -> Style(AppIcons.BeachAccess, AppColors.orange)
            else -> Style(AppIcons.Tune, AppColors.violet)
        }

    // Each apps list has its own icon and colour too.
    fun ofList(list: String): Style =
        when (list) {
            "kids" -> Style(AppIcons.ChildCare, AppColors.pink)
            "everyday" -> Style(AppIcons.WbSunny, AppColors.orange)
            "teen" -> Style(AppIcons.EmojiPeople, AppColors.violet)
            "school" -> Style(AppIcons.School, AppColors.blue)
            "study" -> Style(AppIcons.MenuBook, AppColors.teal)
            "reachable" -> Style(AppIcons.Call, AppColors.green)
            "no-games" -> Style(AppIcons.VideogameAssetOff, AppColors.red)
            else -> Style(AppIcons.AllInclusive, AppColors.slate)
        }
}

// A labelled box to type one line into.
@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val palette = LocalPalette.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, color = palette.muted, fontSize = Dimens.caption)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(TextStyle(color = palette.text, fontSize = Dimens.heading)),
            cursorBrush = SolidColor(palette.brand),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(palette.limited.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .border(1.dp, palette.limited, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

// "< Sat 11 Mehr >": back and forward through the days or weeks kept. Back is toward the past.
@Composable
fun PeriodStepper(
    text: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.tiny)) {
        Box(
            modifier =
                Modifier
                    .size(
                        32.dp,
                    ).clip(
                        CircleShape,
                    ).clickable(enabled = hasPrevious, onClick = onPrevious)
                    .alpha(if (hasPrevious) 1f else 0.3f),
            contentAlignment = Alignment.Center,
        ) { Box(modifier = Modifier.scale(-1f, 1f)) { Arrow() } }
        Text(text = text, color = LocalPalette.current.text, fontSize = 14.sp)
        Box(
            modifier =
                Modifier
                    .size(
                        32.dp,
                    ).clip(CircleShape)
                    .clickable(enabled = hasNext, onClick = onNext)
                    .alpha(if (hasNext) 1f else 0.3f),
            contentAlignment = Alignment.Center,
        ) { Arrow() }
    }
}

// Draws text as a QR code. It is never mirrored and never themed: a camera has to read it.
@Composable
fun QrCode(
    text: String,
    modifier: Modifier = Modifier,
) {
    // With no size asked for, the writer gives one cell per module. The margin is the padding below.
    val modules =
        remember(text) {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        }
    Canvas(
        modifier =
            modifier
                .aspectRatio(1f)
                .background(AppColors.codeBackground, RoundedCornerShape(12.dp))
                .padding(16.dp),
    ) {
        val cell = size.width / modules.width
        // Rounded up, so neighbouring modules leave no hairline between them.
        val drawn = Size(ceil(cell), ceil(cell))
        for (row in 0 until modules.height) {
            for (column in 0 until modules.width) {
                if (modules[column, row]) drawRect(AppColors.code, Offset(column * cell, row * cell), drawn)
            }
        }
    }
}
