package spa.app.pair

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.PoolUiState
import spa.app.importing.PoolSource
import spa.app.results.ResultsKit
import spa.app.testing.TestImports
import spa.core.metrics.ColorPair

/**
 * Criteria 2 to 5: the layout (A2, A6, the reminder line), the lands (D1), the curve (D2), the
 * splash list, and the no-splash and empty-pair states (A7). Values are read from the golden and
 * the baseline.
 */
@RunWith(AndroidJUnit4::class)
class PairDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        pool: PoolUiState.Loaded? = ResultsKit.loaded(PairKit.FILE),
        pair: String = "WU",
        onBack: () -> Unit = {},
        onAbout: () -> Unit = {},
    ) {
        compose.setContent { PairKit.Screen(pool, pair, onBack, onAbout) }
        compose.waitForIdle()
    }

    private fun built(text: String) =
        ResultsKit.loaded(TestImports.success(text), PoolSource.Pasted)

    private fun golden(pointer: String): Int = ResultsKit.goldenValue(PairKit.FILE, pointer).toInt()

    /** The texts of the node tagged [pointer] and below it, scrolled into view. */
    private fun rowTexts(pointer: String): List<String> {
        val row = compose.onNode(hasTestTag(pointer), useUnmergedTree = true)
        row.performScrollTo()
        return ResultsKit.texts(row)
    }

    /** The one merged node holding [text], its texts as the screen reader gets them. */
    private fun mergedNode(text: String): SemanticsNode {
        val nodes = compose.onAllNodes(hasText(text)).fetchSemanticsNodes()
        assertEquals("nodes with '$text'", 1, nodes.size)
        return nodes.single()
    }

    private fun count(text: String, substring: Boolean = false): Int =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().size

    private fun order(interaction: SemanticsNodeInteraction): List<SemanticsNode> =
        ResultsKit.inOrder(interaction.fetchSemanticsNode())

    // Criterion 2: the top bar, the reminder line, the section headings.

    @Test
    fun theTopBarHasBackThePairNameAndAbout() {
        var backs = 0
        var abouts = 0
        show(onBack = { backs++ }, onAbout = { abouts++ })
        compose.onNodeWithText("White–Blue (WU)").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("About").assertHasClickAction().performClick()
        assertEquals(1, backs)
        assertEquals(1, abouts)
    }

    @Test
    fun everyPairCodeGivesItsName() {
        show(pair = "BG")
        compose.onNodeWithText("Black–Green (BG)").assertIsDisplayed()
    }

    @Test
    fun theFourSectionsAreHeadingsInTheSpecsOrder() {
        show()
        val headings = listOf("Lands", "Cards", "Curve", "Splash candidates")
        headings.forEach { compose.onNode(hasText(it) and isHeading()).assertExists() }
        val tree = order(compose.onRoot(useUnmergedTree = true))
        val positions = headings.map { heading ->
            tree.indexOfFirst {
                SemanticsProperties.Heading in it.config &&
                    it.config
                        .getOrElse(SemanticsProperties.Text) { emptyList() }
                        .any { t -> t.text == heading }
            }
        }
        assertTrue("$positions", positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun aCleanPoolHasNoReminderLine() {
        assertEquals(0, PairKit.goldenUnresolved(PairKit.FILE))
        show()
        assertEquals(0, count("not read", substring = true))
    }

    @Test
    fun aPoolWithUnresolvedLinesShowsTheReminderFirst() {
        val file = "trk-text-misspelled-01.txt"
        val n = PairKit.goldenUnresolved(file)
        assertTrue("expected several unresolved lines, got $n", n > 1)
        show(ResultsKit.loaded(ResultsKit.success(file), PoolSource.Pasted))
        val reminder = "This pool has $n lines not read; these numbers leave them out."
        compose.onNodeWithText(reminder).assertIsDisplayed()
        val tree = order(compose.onRoot(useUnmergedTree = true))
        fun at(text: String) = tree.indexOfFirst {
            it.config
                .getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { t -> t.text == text }
        }
        assertTrue(at(reminder) in 0 until at("Lands"))
    }

    @Test
    fun oneUnresolvedLineIsSingular() {
        val text = TestImports.pool("trk-text-01.txt") + "\n1 Not A Card At All"
        val pool = built(text)
        assertEquals(1, pool.result.result.unresolved.size)
        show(pool)
        compose
            .onNodeWithText("This pool has 1 line not read; these numbers leave it out.")
            .assertExists()
    }

    // Criterion 3, D1: the lands.

    @Test
    fun theLandsReadWithBasicLandNamesAndTypicalValues() {
        show()
        PairKit.colours("WU").forEach { colour ->
            val pointer = "/pairs/WU/landRecommendation/$colour"
            val line = "${golden(pointer)} ${PairKit.LANDS.getValue(colour)}"
            compose.onNodeWithText(line, useUnmergedTree = true).assertIsDisplayed()
            val texts = ResultsKit.mergedTexts(mergedNode(line))
            assertTrue("$texts", ResultsKit.typicalText(pointer) in texts)
        }
    }

    @Test
    fun theOtherBasicLandNamesAreUsedForTheirColours() {
        show(pair = "BR")
        compose.onNodeWithText("Black–Red (BR)").assertIsDisplayed()
        listOf("B", "R").forEach { colour ->
            val pointer = "/pairs/BR/landRecommendation/$colour"
            compose
                .onNodeWithText("${golden(pointer)} ${PairKit.LANDS.getValue(colour)}")
                .assertExists()
        }
    }

    // The Cards section.

    @Test
    fun theCardsRowsHaveTheirLabelValueAndTypical() {
        show()
        PairKit.COUNTS.zip(PairKit.COUNT_LABELS).forEach { (segment, label) ->
            val pointer = "/pairs/WU/$segment"
            val row = compose.onNode(hasTestTag(pointer))
            row.performScrollTo()
            val texts = ResultsKit.mergedTexts(row.fetchSemanticsNode())
            assertTrue("$pointer: $texts", label in texts)
            assertTrue("$pointer: $texts", "${golden(pointer)}" in texts)
            assertTrue("$pointer: $texts", ResultsKit.typicalText(pointer) in texts)
        }
    }

    // Criterion 4, D2: the curve.

    @Test
    fun theCurveIsEightMergedRowsEndingInSevenPlus() {
        show()
        val labels = (0..6).map { "Mana value $it" } + "Mana value 7+"
        assertEquals(0, count("Mana value 7"))
        labels.forEachIndexed { i, label ->
            compose.onNode(hasText(label)).performScrollTo()
            val texts = ResultsKit.mergedTexts(mergedNode(label))
            val at = "/pairs/WU/curve/buckets/$i"
            val creatures = golden("$at/creatures")
            val noun = if (creatures == 1) "creature" else "creatures"
            val creaturesLine = "$creatures $noun, ${ResultsKit.typicalText("$at/creatures")}"
            val otherLine =
                "${golden("$at/noncreatures")} other, ${ResultsKit.typicalText("$at/noncreatures")}"
            assertTrue("$label: $texts lacks '$creaturesLine'", creaturesLine in texts)
            assertTrue("$label: $texts lacks '$otherLine'", otherLine in texts)
        }
    }

    // Criterion 5: the splash candidates.

    @Test
    fun theSplashListHasItsCountThenEachCandidateInOrder() {
        show()
        val pointer = "/pairs/WU/splashCandidates/count"
        val n = golden(pointer)
        compose
            .onNodeWithText("$n splash candidates, ${ResultsKit.typicalText(pointer)}")
            .performScrollTo()
            .assertIsDisplayed()
        val candidates = PairKit.goldenSplash(PairKit.FILE, "WU")
        assertEquals(n, candidates.size)
        val nodes = candidates.map { candidate ->
            compose.onNode(hasText(candidate.name)).performScrollTo()
            val node = mergedNode(candidate.name)
            val joined = ResultsKit.mergedTexts(node).joinToString(" ")
            assertTrue(joined, PairKit.COLOURS.getValue(candidate.color) in joined)
            assertTrue(joined, PairKit.reasonWords(candidate.reason) in joined)
            node
        }
        val tops = nodes.map { it.boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        assertEquals(0, count("No splash candidates"))
    }

    @Test
    fun aPairWithNoSplashCandidatesSaysSo() {
        val pool = built(PairKit.NO_SPLASH_WU_POOL)
        show(pool)
        val pointer = "/pairs/WU/splashCandidates/count"
        compose
            .onNodeWithText("0 splash candidates, ${ResultsKit.typicalText(pointer)}")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("No splash candidates").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anEmptyPairShowsZeroEverywhereWithItsTypicalAndBelowTypical() {
        val pool = built(PairKit.EMPTY_WU_POOL)
        assertEquals(0, pool.result.metrics.pairs.getValue(ColorPair.WU).cards)
        show(pool)
        var below = 0
        PairKit.pointers("WU")
            .filterNot { it.endsWith("splashCandidates/count") }
            .forEach { pointer ->
                val joined = rowTexts(pointer).joinToString(" | ")
                val typical = ResultsKit.typicalText(pointer)
                assertTrue("$pointer: '$joined' lacks '$typical'", typical in joined)
                assertTrue(
                    "$pointer: '$joined' lacks the value 0",
                    Regex("\\b0\\b").containsMatchIn(joined.replace(typical, "")),
                )
                val words = ResultsKit.outsideWords(pointer, 0.0)
                if (words != null) {
                    below++
                    assertEquals("below typical", words)
                    assertTrue("$pointer: '$joined' lacks '$words'", words in joined)
                } else {
                    assertFalse("$pointer: '$joined'", "below typical" in joined)
                }
            }
        assertTrue("expected some values below typical", below > 0)
        // A7: the words are in the merged node a screen reader reads, such as the Cards row.
        val cards = compose.onNode(hasTestTag("/pairs/WU/cards")).fetchSemanticsNode()
        assertTrue("below typical" in ResultsKit.mergedTexts(cards))
        compose.onNodeWithText("0 Plains").assertExists()
        compose.onNodeWithText("0 Island").assertExists()
    }
}
