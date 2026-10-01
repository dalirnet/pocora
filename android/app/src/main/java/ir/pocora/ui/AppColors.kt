package ir.pocora.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.config.Look

// Every colour of both apps, light and dark. No colour is written anywhere else.
object AppColors {
    val light =
        Palette(
            backgroundTop = Color(0xFFEFEAFF),
            background = Color(0xFFF8F7FC),
            card = Color(0xFFFFFFFF),
            text = Color(0xFF1E1B2E),
            muted = Color(0xFF8A87A0),
            brand = Color(0xFF7B61FF),
            allowed = Color(0xFF22B8F0),
            limited = Color(0xFFE6E2F3),
            alert = Color(0xFFFF8A3D),
            done = Color(0xFF22C55E),
            shadow = Color(0x337B61FF),
            dark = false,
        )

    val dark =
        Palette(
            backgroundTop = Color(0xFF1A1630),
            background = Color(0xFF100E1C),
            card = Color(0xFF221E3A),
            text = Color(0xFFF1EFFA),
            muted = Color(0xFF9A96B5),
            brand = Color(0xFF9483FF),
            allowed = Color(0xFF4DD0FF),
            limited = Color(0xFF34305A),
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

    // A time the parent changed, on every strip: internet added, and internet taken away.
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
