package ir.pocora.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import ir.pocora.config.Look

val LocalPalette = staticCompositionLocalOf { AppColors.light }

// Every text in both apps a little smaller than the sizes written in the code, on top of the phone's own text
// size setting. Spacing and icons keep their size.
private const val TEXT_SCALE = 0.9f

@Composable
fun PocoraTheme(
    look: Look,
    content: @Composable () -> Unit,
) {
    val palette = AppColors.current(look)
    val scheme =
        if (palette.dark) {
            darkColorScheme(
                primary = palette.brand,
                onPrimary = palette.card,
                background = palette.background,
                onBackground = palette.text,
                surface = palette.card,
                onSurface = palette.text,
            )
        } else {
            lightColorScheme(
                primary = palette.brand,
                onPrimary = palette.card,
                background = palette.background,
                onBackground = palette.text,
                surface = palette.card,
                onSurface = palette.text,
            )
        }
    val fontFamily = AppFonts.current()
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalDensity provides Density(density.density, density.fontScale * TEXT_SCALE),
    ) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily),
                content = content,
            )
        }
    }
}
