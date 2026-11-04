package spa.app.results

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.PoolUiState
import spa.app.importing.PoolSource
import spa.app.testing.TestImports
import spa.app.ui.theme.SpaFonts

/** Criterion 3 (the Problems section's states) and criterion 4 (R1, problems before numbers). */
@RunWith(AndroidJUnit4::class)
class ProblemsSectionTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: PoolUiState) {
        compose.setContent { ResultsKit.Screen(state) }
        compose.waitForIdle()
    }

    private fun count(text: String, substring: Boolean = false): Int =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().size

    /** The spec's reason words, by code. */
    private fun reasonLine(code: String): String =
        when (code) {
            "unknown-name" -> "No trk or sds card has this name (unknown-name)"
            "unknown-collector-number" ->
                "No trk or sds card has this collector number (unknown-collector-number)"
            "wrong-set" -> "This card is from another set (wrong-set)"
            else -> code
        }

    /** Every golden unresolved line is an item: "Line <n>", its full text in mono, its reason. */
    private fun assertLinesShown(file: String) {
        val lines = ResultsKit.goldenUnresolved(file)
        assertTrue("$file has no unresolved lines", lines.isNotEmpty())
        compose
            .onNodeWithText("${lines.size} lines not read. The numbers below leave them out.")
            .assertExists()
        lines.forEach { (line, text, reason) ->
            val item =
                compose
                    .onNode(hasText("Line $line") and hasText(text) and hasText(reasonLine(reason)))
                    .fetchSemanticsNode()
            assertTrue("$file line $line", item.config.contains(SemanticsProperties.Text))
            val textNode = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            assertEquals(
                "$file line $line font",
                SpaFonts.mono,
                ResultsKit.textStyleOf(textNode)?.fontFamily,
            )
            val layout = checkNotNull(ResultsKit.layoutOf(textNode))
            assertEquals("$file line $line is cut", Int.MAX_VALUE, layout.layoutInput.maxLines)
            assertNotEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow)
            assertEquals(text, layout.layoutInput.text.text)
        }
    }

    private fun assertWarningsShown(file: String) {
        val warnings = ResultsKit.goldenWarnings(file)
        assertTrue("$file has no kit warning", warnings.isNotEmpty())
        warnings.forEach { w ->
            val title =
                when (w.code) {
                    "card-count" -> "Card count"
                    "high-count" -> "Too many copies"
                    else -> w.code
                }
            compose
                .onNode(
                    hasText(title) and
                        hasText("${w.actual}, expected ${w.min} to ${w.max}") and
                        hasText(w.message)
                )
                .assertExists()
        }
    }

    @Test
    fun aCleanPoolHasTheOneAllClearLine() {
        val file = "trk-manabox-01.csv"
        assertTrue(ResultsKit.goldenUnresolved(file).isEmpty())
        assertTrue(ResultsKit.goldenWarnings(file).isEmpty())
        show(ResultsKit.loaded(file))
        compose.onNodeWithText("Problems").assertExists()
        compose.onNodeWithText("Every line read. Kit check passed.").assertIsDisplayed()
        assertEquals(0, count("lines not read", substring = true))
        assertEquals(0, count("Skip to numbers"))
        assertEquals(0, count("Some cards are from another set", substring = true))
    }

    @Test
    fun misspelledLinesAndTheKitWarningAreShown() {
        val file = "trk-text-misspelled-01.txt"
        show(ResultsKit.loaded(ResultsKit.success(file)))
        assertLinesShown(file)
        assertWarningsShown(file)
        assertEquals(0, count("Every line read. Kit check passed."))
        assertEquals(0, count("Some cards are from another set", substring = true))
        // Four items: not a long list.
        assertEquals(0, count("Skip to numbers"))
    }

    @Test
    fun wrongSetLinesAddTheAnotherSetNote() {
        val file = "trk-manabox-wrong-set-01.csv"
        show(ResultsKit.loaded(file))
        assertLinesShown(file)
        assertWarningsShown(file)
        compose
            .onNodeWithText(
                "Some cards are from another set. This app reads trk and its sds bonus sheet only."
            )
            .assertExists()
    }

    @Test
    fun badCollectorNumbersAreShown() {
        val file = "trk-collector-bad-number-01.txt"
        show(ResultsKit.loaded(ResultsKit.success(file)))
        assertLinesShown(file)
        assertWarningsShown(file)
    }

    @Test
    fun moreThanFiveItemsAddSkipToNumbers() {
        val text =
            TestImports.pool("trk-text-01.txt") +
                "\n" +
                (1..SIX).joinToString("\n") { "1 Misspelled Card Number $it" }
        val success = TestImports.success(text)
        val ui = ResultsUi.from(success, ResultsKit.baseline)
        assertTrue("${ui.problemCount} items", ui.problemCount > FIVE)
        show(ResultsKit.loaded(success, PoolSource.Pasted))
        compose.onNodeWithText("Pool at a glance").assertIsNotDisplayed()
        compose.onNodeWithText("Skip to numbers").assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pool at a glance").assertIsDisplayed()
    }

    @Test
    fun problemsComeBeforeTheFirstMetricRowInReadingOrder() {
        val file = "trk-text-misspelled-01.txt"
        show(ResultsKit.loaded(ResultsKit.success(file)))
        val order = ResultsKit.inOrder(compose.onRoot().fetchSemanticsNode())
        val firstMetric = order.indexOfFirst {
            it.config.getOrNull(SemanticsProperties.TestTag) == "/totalCards"
        }
        assertTrue("no metric row", firstMetric >= 0)
        fun indexOf(text: String): Int = order.indexOfFirst { text in ResultsKit.mergedTexts(it) }
        val unresolved = ResultsKit.goldenUnresolved(file)
        val problems =
            listOf("${unresolved.size} lines not read. The numbers below leave them out.") +
                unresolved.flatMap { (line, text, _) -> listOf("Line $line", text) } +
                ResultsKit.goldenWarnings(file).flatMap { listOf("Card count", it.message) }
        problems.forEach { text ->
            val at = indexOf(text)
            assertTrue("'$text' not found", at >= 0)
            assertTrue(
                "'$text' at $at comes after the first metric at $firstMetric",
                at < firstMetric,
            )
        }
    }

    private companion object {
        const val FIVE = 5
        const val SIX = 6
    }
}
