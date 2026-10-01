package ir.pocora.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ir.pocora.R
import ir.pocora.config.Language

object AppFonts {
    // Dana in its FaNum cut, which draws every digit as a Persian one.
    private val dana =
        FontFamily(
            Font(R.font.dana_regular, FontWeight.Normal),
            Font(R.font.dana_bold, FontWeight.Bold),
        )

    // English keeps Android's own font: this cut of Dana has no Latin digits, and English shows Latin ones.
    @Composable
    fun current(): FontFamily =
        if (LocalConfiguration.current.locales[0].language ==
            Language.PERSIAN
        ) {
            dana
        } else {
            FontFamily.Default
        }
}
