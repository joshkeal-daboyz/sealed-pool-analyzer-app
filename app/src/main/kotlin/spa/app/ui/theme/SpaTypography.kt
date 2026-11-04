package spa.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, weight: FontWeight, lineHeight: Int): TextStyle =
    TextStyle(
        fontFamily = SpaFonts.sans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
    )

private val Bold = FontWeight.Bold
private val Regular = FontWeight.Normal

/** The spec's type scale, all in sp, mapped onto Material 3's roles. */
val SpaTypography: Typography =
    Typography(
        displayLarge = style(size = 36, weight = Bold, lineHeight = 44),
        // Big row value: 34 bold, tabular figures.
        displayMedium =
            style(size = 34, weight = Bold, lineHeight = 42).copy(fontFeatureSettings = "tnum"),
        // Pool heading ("Add your pool").
        displaySmall = style(size = 30, weight = Bold, lineHeight = 38),
        headlineLarge = style(size = 26, weight = Bold, lineHeight = 32),
        headlineMedium = style(size = 24, weight = Bold, lineHeight = 30),
        // Section headings.
        headlineSmall = style(size = 22, weight = Bold, lineHeight = 28),
        // Screen title.
        titleLarge = style(size = 20, weight = Bold, lineHeight = 26),
        // Row labels.
        titleMedium = style(size = 18, weight = Bold, lineHeight = 24),
        titleSmall = style(size = 16, weight = Bold, lineHeight = 22),
        // Secondary and typical lines.
        bodyLarge = style(size = 16, weight = Regular, lineHeight = 22),
        bodyMedium = style(size = 15, weight = Regular, lineHeight = 21),
        bodySmall = style(size = 14, weight = Regular, lineHeight = 20),
        // Primary button.
        labelLarge = style(size = 19, weight = Bold, lineHeight = 24),
        // Outlined button.
        labelMedium = style(size = 16, weight = Bold, lineHeight = 22),
        labelSmall = style(size = 14, weight = Regular, lineHeight = 20),
    )
