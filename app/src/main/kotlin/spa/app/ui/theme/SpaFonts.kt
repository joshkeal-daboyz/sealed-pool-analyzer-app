package spa.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import spa.app.R

/** The bundled fonts, from `res/font` (the app has no network, so no downloadable fonts). */
object SpaFonts {
    /** Atkinson Hyperlegible, regular 400 and bold 700: everything but pasted text. */
    val sans: FontFamily =
        FontFamily(
            Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
            Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
        )

    /**
     * Atkinson Hyperlegible Mono at 400, for pasted text and unread lines. Google Fonts ships it
     * only as a variable font, so its weight axis is pinned to 400.
     */
    @OptIn(ExperimentalTextApi::class)
    val mono: FontFamily =
        FontFamily(
            Font(
                R.font.atkinson_hyperlegible_mono,
                FontWeight.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(REGULAR)),
            )
        )

    private const val REGULAR = 400
}
