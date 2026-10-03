package ir.pocora.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.Role
import ir.pocora.config.Look

// Every colour of both apps, light and dark. No colour is written anywhere else.
// Each app has its own brand, as its icon: violet in the parent app, sky in the child app. The greys of each app
// lean towards its brand, so nothing in the child app reads as violet.
object AppColors {
    private val parentLight =
        light(
            brand = Color(0xFF7B61FF),
            allowed = Color(0xFF22B8F0),
            backgroundTop = Color(0xFFEFEAFF),
            background = Color(0xFFF8F7FC),
            text = Color(0xFF1E1B2E),
            muted = Color(0xFF8A87A0),
            limited = Color(0xFFE6E2F3),
        )

    private val parentDark =
        dark(
            brand = Color(0xFF9483FF),
            allowed = Color(0xFF4DD0FF),
            backgroundTop = Color(0xFF1A1630),
            background = Color(0xFF100E1C),
            card = Color(0xFF221E3A),
            text = Color(0xFFF1EFFA),
            muted = Color(0xFF9A96B5),
            limited = Color(0xFF34305A),
        )

    // The child's sky is the icon's deep end on light and its bright end on dark. It is also the colour of internet
    // time, so the brand and the allowed times are one colour in the child app.
    private val childLight =
        light(
            brand = Color(0xFF0E9FCC),
            backgroundTop = Color(0xFFE4F5FB),
            background = Color(0xFFF5FAFC),
            text = Color(0xFF17262E),
            muted = Color(0xFF7E929D),
            limited = Color(0xFFDFEBF1),
        )

    private val childDark =
        dark(
            brand = Color(0xFF3DD6F5),
            backgroundTop = Color(0xFF0F2530),
            background = Color(0xFF0A151B),
            card = Color(0xFF17303A),
            text = Color(0xFFEDF6FA),
            muted = Color(0xFF8FA7B3),
            limited = Color(0xFF28444F),
        )

    val light: Palette = if (Role.current == Role.CHILD) childLight else parentLight

    val dark: Palette = if (Role.current == Role.CHILD) childDark else parentDark

    // What both apps share in a theme: white cards with a soft shadow of the brand, orange alerts, green done.
    private fun light(
        brand: Color,
        backgroundTop: Color,
        background: Color,
        text: Color,
        muted: Color,
        limited: Color,
        allowed: Color = brand,
    ) = Palette(
        backgroundTop = backgroundTop,
        background = background,
        card = Color(0xFFFFFFFF),
        text = text,
        muted = muted,
        brand = brand,
        allowed = allowed,
        limited = limited,
        alert = Color(0xFFFF8A3D),
        done = Color(0xFF22C55E),
        shadow = brand.copy(alpha = 0.2f),
        dark = false,
    )

    private fun dark(
        brand: Color,
        backgroundTop: Color,
        background: Color,
        card: Color,
        text: Color,
        muted: Color,
        limited: Color,
        allowed: Color = brand,
    ) = Palette(
        backgroundTop = backgroundTop,
        background = background,
        card = card,
        text = text,
        muted = muted,
        brand = brand,
        allowed = allowed,
        limited = limited,
        alert = Color(0xFFFF9F5A),
        done = Color(0xFF3DDC97),
        shadow = Color(0x66000000),
        dark = true,
    )

    // A pairing code is black on white in both themes, so every camera can read it.
    val code = Color(0xFF000000)
    val codeBackground = Color(0xFFFFFFFF)

    // The dimmed screen behind a sheet.
    val scrim = Color(0x66000000)

    // Text and icons on a coloured tile or hero.
    val onColor = Color(0xFFFFFFFF)

    // The tile colour of each kind of app, and of the feature tiles.
    val blue = Color(0xFF3B82F6)
    val teal = Color(0xFF14B8A6)
    val pink = Color(0xFFEC4899)
    val green = Color(0xFF22C55E)
    val red = Color(0xFFEF4444)
    val violet = Color(0xFF7B61FF)
    val orange = Color(0xFFF59E0B)
    val cyan = Color(0xFF06B6D4)
    val magenta = Color(0xFFD946EF)
    val amber = Color(0xFFEAB308)
    val slate = Color(0xFF64748B)
    val grey = Color(0xFF9CA3AF)

    // A time the parent changed, on every strip: internet added, and internet taken away. Added time is violet in
    // both apps, the parent's own colour, so the child sees the parent's hand in it.
    val changeAdded = violet
    val changeCut = orange

    @Composable
    fun current(look: Look): Palette =
        when (look) {
            Look.LIGHT -> light
            Look.DARK -> dark
            Look.SYSTEM -> if (isSystemInDarkTheme()) dark else light
        }
}

data class Palette(
    val backgroundTop: Color,
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val brand: Color,
    val allowed: Color,
    val limited: Color,
    val alert: Color,
    val done: Color,
    val shadow: Color,
    val dark: Boolean,
)

// One scale for space and type, so every screen lines up the same way. Space is on a 4 dp grid.
object Dimens {
    // Space
    val edge = 20.dp
    val section = 24.dp
    val inside = 16.dp
    val row = 12.dp
    val small = 8.dp
    val tiny = 4.dp

    // Shapes
    val cardCorner = 24.dp
    val sheetCorner = 32.dp
    val tileCorner = 18.dp

    // Sizes
    val tile = 56.dp
    val rowIcon = 36.dp
    val headerButton = 44.dp
    val avatar = 52.dp

    // Type
    val title = 24.sp
    val heading = 17.sp
    val body = 15.sp
    val caption = 13.sp
    val label = 12.sp
    val number = 28.sp
}
