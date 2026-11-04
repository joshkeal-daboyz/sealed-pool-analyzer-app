package spa.app.results

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Criterion 2 (every value of the model on screen, R2) and criterion 5 (the legend, A7), on
 * `trk-manabox-01.csv`. Numbers are read from the golden and the baseline.
 */
@RunWith(AndroidJUnit4::class)
class ResultsValuesTest {
    @get:Rule val compose = createComposeRule()

    private val success = ResultsKit.success(FILE)
    private val ui = ResultsUi.from(success, ResultsKit.baseline)

    private fun show() {
        compose.setContent { ResultsKit.Screen(ResultsKit.loaded(success)) }
        compose.waitForIdle()
    }

    /** Expands every colour row, so its rarity lines are on screen. */
    private fun expandColours() {
        ResultsKit.COLOR_NAMES.forEach { name ->
            compose.onNode(hasText(name) and hasClickAction()).performScrollTo().performClick()
        }
        compose.waitForIdle()
    }

    /** The texts of the node tagged [pointer] and below it, scrolled into view. */
    private fun rowTexts(pointer: String): List<String> {
        val row = compose.onNode(hasTestTag(pointer), useUnmergedTree = true)
        row.performScrollTo()
        return ResultsKit.texts(row)
    }

    @Test
    fun everyValueInTheModelIsOnScreenWithItsTypicalLine() {
        show()
        expandColours()
        assertEquals(ResultsKit.ALL_POINTERS.size, ui.values.size)
        ui.values.forEach { shown ->
            val joined = rowTexts(shown.pointer).joinToString(" | ")
            val typical = ResultsKit.typicalText(shown.pointer)
            assertTrue("${shown.pointer}: '$joined' lacks '$typical'", typical in joined)
            val value = ResultsKit.goldenValue(FILE, shown.pointer).toInt()
            val rest = joined.replace(typical, "")
            assertTrue(
                "${shown.pointer}: '$joined' lacks the value $value",
                Regex("\\b$value\\b").containsMatchIn(rest),
            )
        }
    }

    @Test
    fun theRemovalRowIsOneNodeWithLabelValueAndTypical() {
        show()
        val rows = compose.onAllNodes(hasText("Removal")).fetchSemanticsNodes()
        assertEquals(1, rows.size)
        val texts = ResultsKit.mergedTexts(rows.single())
        val value = ResultsKit.goldenValue(FILE, "/removalTotal").toInt()
        val t = ResultsKit.typical("/removalTotal")
        assertTrue("$texts", "$value" in texts)
        assertTrue("$texts", "typical ${t.median} (${t.p10}–${t.p90})" in texts)
    }

    @Test
    fun aStatWithP10EqualToP90ShowsTheMedianAlone() {
        show()
        val t = ResultsKit.typical("/totalCards")
        assertEquals(t.p10, t.p90)
        val texts =
            ResultsKit.mergedTexts(compose.onNode(hasText("Total cards")).fetchSemanticsNode())
        assertTrue("$texts", "typical ${t.median}" in texts)
    }

    @Test
    fun theLegendNamesTheBaselinesKits() {
        show()
        compose
            .onNodeWithText(
                "Typical: the median of ${ResultsKit.kits} simulated trk pools, with the middle " +
                    "80% (10th to 90th percentile)."
            )
            .assertExists()
    }

    @Test
    fun everyValueOutsideItsRangeSaysSoAndNoOtherDoes() {
        show()
        expandColours()
        var outside = 0
        ResultsKit.ALL_POINTERS.forEach { pointer ->
            val words = ResultsKit.outsideWords(pointer, ResultsKit.goldenValue(FILE, pointer))
            val texts = rowTexts(pointer)
            if (words != null) {
                outside++
                assertTrue("$pointer: $texts lacks '$words'", words in texts)
            } else {
                assertFalse(
                    "$pointer: $texts",
                    "below typical" in texts || "above typical" in texts,
                )
            }
        }
        assertTrue("expected some outside values", outside > 0)
    }

    @Test
    fun fixingSourcesIsBelowTypicalAndBlueCardsAboveInTheirMergedNodes() {
        show()
        val fixing = "/fixingSources/count"
        assertEquals(
            "below typical",
            ResultsKit.outsideWords(fixing, ResultsKit.goldenValue(FILE, fixing)),
        )
        val fixingRow = compose.onNode(hasText("Fixing sources") and hasTestTag(fixing))
        assertTrue("below typical" in ResultsKit.mergedTexts(fixingRow.fetchSemanticsNode()))

        val blue = "/byColor/U/cards/total"
        assertEquals(
            "above typical",
            ResultsKit.outsideWords(blue, ResultsKit.goldenValue(FILE, blue)),
        )
        assertTrue("above typical" in rowTexts(blue))
        // The Blue row is one merged node, and the words are in it.
        val blueRow = compose.onNode(hasText("Blue") and hasClickAction()).fetchSemanticsNode()
        assertTrue(
            "above typical" in
                blueRow.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        )
    }

    private companion object {
        const val FILE = "trk-manabox-01.csv"
    }
}
