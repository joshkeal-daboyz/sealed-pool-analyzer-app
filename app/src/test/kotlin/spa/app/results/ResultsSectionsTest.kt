package spa.app.results

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.PoolViewModel
import spa.app.SpaApp
import spa.app.importing.PoolSource
import spa.app.nav.Routes
import spa.app.testing.A11yChecks
import spa.app.testing.TestImports
import spa.app.timing.SpaTiming
import spa.app.timing.TimingSink
import spa.core.metrics.ColorPair

/**
 * Criteria 6 (pairs, R3), 7 (colours, R4), 8 (fixing sources), and 9 (headings A6, and R6 at font
 * scale 2.0). Values are read from the goldens and the baseline.
 */
@RunWith(AndroidJUnit4::class)
class ResultsSectionsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(file: String = FILE, onPair: (ColorPair) -> Unit = {}) {
        compose.setContent { ResultsKit.Screen(ResultsKit.loaded(file), onPair) }
        compose.waitForIdle()
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private val hasState = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)

    private fun line(pointer: String, format: (Int) -> String): String {
        val value = ResultsKit.goldenValue(FILE, pointer).toInt()
        return "${format(value)}, ${ResultsKit.typicalText(pointer)}"
    }

    // Criterion 6.

    @Test
    fun tenPairRowsAreSortedByCardsUnderTheirNote() {
        show()
        compose.onNodeWithText("Colour pairs").assertExists()
        compose.onNodeWithText("Sorted by cards in the pair").assertExists()
        val rows =
            compose
                .onAllNodes(
                    isButton and
                        SemanticsMatcher("pair row") {
                            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("pair/") ==
                                true
                        }
                )
                .fetchSemanticsNodes()
        assertEquals(TEN, rows.size)
        val shown = rows.map { it.config[SemanticsProperties.TestTag].removePrefix("pair/") }
        assertEquals(ResultsKit.goldenPairOrder(FILE), shown)
        // In reading order too: the rows' tops increase.
        val tops = rows.map { it.boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun eachPairRowIsOneButtonWithItsNameAndTwoLines() {
        show()
        ColorPair.entries.forEach { pair ->
            val row = compose.onNode(hasTestTag("pair/$pair") and isButton and hasClickAction())
            val texts = ResultsKit.mergedTexts(row.fetchSemanticsNode())
            val cards = line("/pairs/$pair/cards") { "$it cards" }
            val creatures = line("/pairs/$pair/creatures") { "$it creatures" }
            assertTrue("$pair: $texts lacks '$cards'", cards in texts)
            assertTrue("$pair: $texts lacks '$creatures'", creatures in texts)
        }
        val wu = compose.onNode(hasTestTag("pair/WU")).fetchSemanticsNode()
        assertTrue("White–Blue (WU)" in ResultsKit.mergedTexts(wu))
    }

    @Test
    fun tappingAPairRowAsksForThatPair() {
        val asked = mutableListOf<ColorPair>()
        show(onPair = { asked += it })
        compose.onNode(hasTestTag("pair/UG")).performScrollTo().performClick()
        assertEquals(listOf(ColorPair.UG), asked)
    }

    @Test
    fun inTheAppTappingAPairRowOpensItsRoute() {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            SpaApp(PoolViewModel(TestImports.importer), SpaTiming(TimingSink { _, _ -> }), nav)
        }
        compose.onNode(hasSetTextAction()).performTextReplacement(TestImports.pool(FILE))
        compose.onNodeWithText("Analyze pool").performClick()
        // The bundled baseline loads from the APK's assets, then the pairs appear.
        compose.waitUntil(ResultsKit.TIMEOUT) {
            compose.onAllNodes(hasTestTag("pair/WR")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasTestTag("pair/WR")).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(Routes.PAIR, nav.currentDestination?.route)
        assertEquals("WR", nav.currentBackStackEntry?.arguments?.getString(Routes.PAIR_ARG))
    }

    // Criterion 7.

    @Test
    fun sevenColourRowsInOrderCollapsedWithCardsAndPlayables() {
        show()
        val rows = compose.onAllNodes(hasState and hasClickAction()).fetchSemanticsNodes()
        assertEquals(ResultsKit.COLOR_NAMES.size, rows.size)
        val codes = listOf("W", "U", "B", "R", "G", "MULTI", "COLORLESS")
        rows.forEachIndexed { at, row ->
            val texts = ResultsKit.mergedTexts(row)
            assertEquals(ResultsKit.COLOR_NAMES[at], texts.first())
            assertEquals("Collapsed", row.config[SemanticsProperties.StateDescription])
            assertEquals(Role.Button, row.config.getOrNull(SemanticsProperties.Role))
            val c = codes[at]
            assertTrue("$texts", line("/byColor/$c/cards/total") { "Cards $it" } in texts)
            assertTrue("$texts", line("/byColor/$c/playables/total") { "Playables $it" } in texts)
            assertFalse("$texts", texts.any { it.startsWith("Creatures") })
        }
    }

    @Test
    fun tappingAColourRowExpandsItWithEveryNumber() {
        show()
        val red = compose.onNode(hasText("Red") and hasState)
        red.performScrollTo().performClick()
        compose.waitForIdle()
        val node = red.fetchSemanticsNode()
        assertEquals("Expanded", node.config[SemanticsProperties.StateDescription])
        val texts = ResultsKit.mergedTexts(node)
        val expected =
            listOf(
                line("/byColor/R/cards/total") { "Cards $it" },
                line("/byColor/R/playables/total") { "Playables $it" },
                line("/byColor/R/creatures") { "Creatures $it" },
                line("/byColor/R/removal") { "Removal $it" },
                line("/byColor/R/interaction") { "Interaction $it" },
            ) +
                RARITIES.map { (key, word) ->
                    line("/byColor/R/cards/$key") { "$word cards $it" }
                } +
                RARITIES.map { (key, word) ->
                    line("/byColor/R/playables/$key") { "$word playables $it" }
                }
        expected.forEach { assertTrue("$texts lacks '$it'", it in texts) }
        red.performClick()
        compose.waitForIdle()
        assertEquals(
            "Collapsed",
            red.fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        )
    }

    // Criterion 8.

    @Test
    fun eachFixingSourceHasItsNameAndColoursAndNoCountAtOne() {
        val fixing = ResultsKit.goldenFixing(FILE)
        assertTrue(fixing.isNotEmpty())
        assertTrue(fixing.all { it.third == 1 })
        show()
        fixing.forEach { (name, colors, _) ->
            val names = colors.joinToString(", ") { COLOUR_NAMES.getValue(it) }
            compose.onNode(hasText(name) and hasText(names)).assertExists()
        }
        assertEquals(
            0,
            compose.onAllNodes(hasText("copies", substring = true)).fetchSemanticsNodes().size,
        )
        assertEquals(0, compose.onAllNodes(hasText("No fixing sources")).fetchSemanticsNodes().size)
    }

    @Test
    fun aSourceWithTwoCopiesShowsItsCount() {
        val success = TestImports.success("2 Bajor")
        val source = success.metrics.fixingSources.single()
        assertEquals(2, source.count)
        compose.setContent { ResultsKit.Screen(ResultsKit.loaded(success, PoolSource.Pasted)) }
        compose
            .onNode(hasText("Bajor") and hasText("White, Red") and hasText("2 copies"))
            .assertExists()
    }

    @Test
    fun aPoolWithNoFixingSaysSo() {
        val success = TestImports.success("TRK 1701d")
        assertTrue(success.metrics.fixingSources.isEmpty())
        compose.setContent { ResultsKit.Screen(ResultsKit.loaded(success, PoolSource.Pasted)) }
        compose.onNodeWithText("No fixing sources").assertExists()
    }

    // Criterion 9.

    @Test
    fun theSectionHeadingsAreHeadings() {
        show()
        listOf("Problems", "Pool at a glance", "Colour pairs", "Cards per colour", "Fixing sources")
            .forEach { compose.onNode(hasText(it) and isHeading()).assertExists() }
    }

    @Test
    fun atFontScale2WrongSetLinesAreWholeAndNothingOverflows() {
        val file = "trk-manabox-wrong-set-01.csv"
        val state = ResultsKit.loaded(file)
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { ResultsKit.Screen(state) }
        ResultsKit.goldenUnresolved(file).forEach { (_, text, _) ->
            compose.onNodeWithText(text, useUnmergedTree = true).assertExists()
        }
    }

    private companion object {
        const val FILE = "trk-manabox-01.csv"
        const val TEN = 10
        val RARITIES =
            listOf(
                "common" to "Common",
                "uncommon" to "Uncommon",
                "rare" to "Rare",
                "mythic" to "Mythic",
            )
        val COLOUR_NAMES =
            mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green")
    }
}
