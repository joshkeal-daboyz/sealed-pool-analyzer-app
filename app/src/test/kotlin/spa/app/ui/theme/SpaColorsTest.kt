package spa.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Criterion 1 and A3: the one dark scheme, its eleven tokens, and their measured contrast. */
@RunWith(AndroidJUnit4::class)
class SpaColorsTest {
    @get:Rule val compose = createComposeRule()

    private val colors = SpaColors.Dark

    @Test
    fun holdsExactlyTheElevenTokensOfTheSpec() {
        val fields =
            SpaColors::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        assertEquals(
            listOf(
                "ground",
                "surface",
                "onSurface",
                "onSurfaceVariant",
                "muted",
                "outline",
                "divider",
                "accent",
                "onAccent",
                "accentText",
                "selectedChip",
            ),
            fields.map { it.name },
        )
        assertEquals(Color(0xFF121417), colors.ground)
        assertEquals(Color(0xFF1C2026), colors.surface)
        assertEquals(Color(0xFFECEFF3), colors.onSurface)
        assertEquals(Color(0xFFB8C0CA), colors.onSurfaceVariant)
        assertEquals(Color(0xFF8C96A3), colors.muted)
        assertEquals(Color(0xFF6B7480), colors.outline)
        assertEquals(Color(0xFF2E343C), colors.divider)
        assertEquals(Color(0xFFF2B544), colors.accent)
        assertEquals(Color(0xFF1A1406), colors.onAccent)
        assertEquals(Color(0xFFFFE2A3), colors.accentText)
        assertEquals(Color(0xFF3A2E12), colors.selectedChip)
    }

    @Test
    fun everyContrastPairMatchesTheSpecTableAndMeetsItsThreshold() {
        val c = colors
        val pairs =
            listOf(
                Row("onSurface/ground", c.onSurface, c.ground, "16.00", TEXT),
                Row("onSurface/surface", c.onSurface, c.surface, "14.18", TEXT),
                Row("onSurfaceVariant/surface", c.onSurfaceVariant, c.surface, "8.90", TEXT),
                Row("onSurfaceVariant/ground", c.onSurfaceVariant, c.ground, "10.04", TEXT),
                Row("accentText/ground", c.accentText, c.ground, "14.64", TEXT),
                Row("accentText/surface", c.accentText, c.surface, "12.97", TEXT),
                Row("onAccent/accent", c.onAccent, c.accent, "10.00", TEXT),
                Row("accentText/selectedChip", c.accentText, c.selectedChip, "10.55", TEXT),
                Row("muted/ground", c.muted, c.ground, "6.16", TEXT),
                Row("accent/ground", c.accent, c.ground, "10.08", TEXT),
                Row("outline/ground", c.outline, c.ground, "3.90", NON_TEXT),
                Row("outline/surface", c.outline, c.surface, "3.45", NON_TEXT),
            )
        assertEquals(12, pairs.size)
        pairs.forEach { row ->
            val ratio = contrast(row.foreground, row.background)
            val rounded = BigDecimal(ratio).setScale(2, RoundingMode.HALF_UP).toPlainString()
            assertEquals(row.name, row.expected, rounded)
            assertTrue("${row.name}: $ratio is below ${row.threshold}", ratio >= row.threshold)
        }
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun theSystemLightSettingChangesNothing() {
        assertThemeIsTheDarkScheme(expectSystemDark = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun theSystemDarkSettingGivesTheSameScheme() {
        assertThemeIsTheDarkScheme(expectSystemDark = true)
    }

    private fun assertThemeIsTheDarkScheme(expectSystemDark: Boolean) {
        var systemDark: Boolean? = null
        var seen: List<Color> = emptyList()
        var local: SpaColors? = null
        compose.setContent {
            systemDark = isSystemInDarkTheme()
            SpaTheme {
                val scheme = MaterialTheme.colorScheme
                seen = listOf(scheme.background, scheme.surface, scheme.onSurface, scheme.primary)
                local = LocalSpaColors.current
            }
        }
        compose.waitForIdle()
        assertEquals(expectSystemDark, systemDark)
        assertEquals(listOf(colors.ground, colors.surface, colors.onSurface, colors.accent), seen)
        assertEquals(colors, local)
    }

    private data class Row(
        val name: String,
        val foreground: Color,
        val background: Color,
        val expected: String,
        val threshold: Double,
    )

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0

        /** WCAG 2 contrast ratio. */
        fun contrast(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** WCAG 2 relative luminance, from the colour's 8-bit sRGB channels. */
        fun luminance(color: Color): Double {
            val argb = color.toArgb()
            fun channel(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF) / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
    }
}
