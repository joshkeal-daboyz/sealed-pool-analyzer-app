package spa.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import spa.app.R

/** Criterion 2: the type scale is Atkinson Hyperlegible from `res/font`, sized in sp. */
class SpaTypographyTest {
    private val t = SpaTypography

    private val styles: Map<String, TextStyle> =
        mapOf(
            "displayLarge" to t.displayLarge,
            "displayMedium" to t.displayMedium,
            "displaySmall" to t.displaySmall,
            "headlineLarge" to t.headlineLarge,
            "headlineMedium" to t.headlineMedium,
            "headlineSmall" to t.headlineSmall,
            "titleLarge" to t.titleLarge,
            "titleMedium" to t.titleMedium,
            "titleSmall" to t.titleSmall,
            "bodyLarge" to t.bodyLarge,
            "bodyMedium" to t.bodyMedium,
            "bodySmall" to t.bodySmall,
            "labelLarge" to t.labelLarge,
            "labelMedium" to t.labelMedium,
            "labelSmall" to t.labelSmall,
        )

    @Test
    fun theSansFamilyIsTheBundledRegularAndBold() {
        assertEquals(
            setOf(
                R.font.atkinson_hyperlegible_regular to FontWeight.Normal,
                R.font.atkinson_hyperlegible_bold to FontWeight.Bold,
            ),
            resources(SpaFonts.sans),
        )
    }

    @Test
    fun theMonoFamilyIsTheBundledMonoAt400() {
        assertEquals(
            setOf(R.font.atkinson_hyperlegible_mono to FontWeight.Normal),
            resources(SpaFonts.mono),
        )
    }

    @Test
    fun everyTextStyleUsesTheSansFamily() {
        assertEquals(15, styles.size)
        styles.forEach { (name, style) -> assertEquals(name, SpaFonts.sans, style.fontFamily) }
    }

    @Test
    fun noTextStyleSetsASizeInDpOnlySp() {
        styles.forEach { (name, style) ->
            assertTrue("$name fontSize ${style.fontSize}", style.fontSize.isSp)
            assertSpOrUnspecified("$name lineHeight", style.lineHeight)
            assertSpOrUnspecified("$name letterSpacing", style.letterSpacing)
        }
    }

    @Test
    fun theSpecSizesAndWeights() {
        // Screen title 20 bold; Pool heading 30 bold; section headings 22 bold; row labels 18 bold.
        assertStyle(t.titleLarge, 20, FontWeight.Bold)
        assertStyle(t.displaySmall, 30, FontWeight.Bold)
        assertStyle(t.headlineSmall, 22, FontWeight.Bold)
        assertStyle(t.titleMedium, 18, FontWeight.Bold)
        // Secondary and typical lines, 15 to 16.
        assertStyle(t.bodyLarge, 16, FontWeight.Normal)
        assertStyle(t.bodyMedium, 15, FontWeight.Normal)
        // Big row value, 34 bold with tabular figures.
        assertStyle(t.displayMedium, 34, FontWeight.Bold)
        assertEquals("tnum", t.displayMedium.fontFeatureSettings)
        // Primary button 19 bold; outlined button 16 bold.
        assertStyle(t.labelLarge, 19, FontWeight.Bold)
        assertStyle(t.labelMedium, 16, FontWeight.Bold)
    }

    private fun assertStyle(style: TextStyle, sizeSp: Int, weight: FontWeight) {
        assertEquals(sizeSp.sp, style.fontSize)
        assertEquals(weight, style.fontWeight)
    }

    private fun assertSpOrUnspecified(name: String, unit: TextUnit) {
        assertTrue("$name is $unit", unit == TextUnit.Unspecified || unit.isSp)
    }

    private fun resources(family: FontFamily): Set<Pair<Int, FontWeight>> {
        val fonts = (family as FontListFontFamily).fonts
        fonts.forEach { assertEquals(FontStyle.Normal, it.style) }
        return fonts.map { (it as ResourceFont).resId to it.weight }.toSet()
    }
}
