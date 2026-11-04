package spa.app.pair

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.PoolViewModel
import spa.app.SpaApp
import spa.app.nav.Routes
import spa.app.results.LocalBaseline
import spa.app.results.ResultsKit
import spa.app.testing.TestImports
import spa.app.timing.SpaTiming
import spa.app.timing.TimingSink

/**
 * Criterion 2 through the app: a pair row opens Pair detail with the loaded pool's numbers, and
 * Back returns to Results at the same scroll position.
 */
@RunWith(AndroidJUnit4::class)
class PairDetailNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    private fun launchWithManabox01() {
        val vm = PoolViewModel(TestImports.importer)
        val timing = SpaTiming(TimingSink { _, _ -> })
        compose.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalBaseline provides ResultsKit.baseline) {
                SpaApp(vm, timing, nav)
            }
        }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextReplacement(TestImports.pool(PairKit.FILE))
        compose.onNodeWithText("Analyze pool").performClick()
        compose.waitUntil(ResultsKit.TIMEOUT) {
            compose.onAllNodes(hasTestTag("pair/WU")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Where the Results scroll stands, in px. */
    private fun resultsScroll(): Float =
        compose
            .onNode(hasScrollAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()

    @Test
    fun aPairRowOpensPairDetailWithThePoolsNumbers() {
        launchWithManabox01()
        compose.onNode(hasTestTag("pair/WU")).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(Routes.PAIR, nav.currentDestination?.route)
        compose.onNodeWithText("White–Blue (WU)").assertIsDisplayed()
        PairKit.colours("WU").forEach { colour ->
            val pointer = "/pairs/WU/landRecommendation/$colour"
            val n = ResultsKit.goldenValue(PairKit.FILE, pointer).toInt()
            compose.onNodeWithText("$n ${PairKit.LANDS.getValue(colour)}").assertExists()
        }
        val creatures = ResultsKit.goldenValue(PairKit.FILE, "/pairs/WU/creatures").toInt()
        val row = compose.onNode(hasTestTag("/pairs/WU/creatures")).fetchSemanticsNode()
        assertTrue("$creatures" in ResultsKit.mergedTexts(row))
    }

    @Test
    fun backReturnsToResultsAtTheSameScrollPosition() {
        launchWithManabox01()
        compose.onNode(hasTestTag("pair/RG")).performScrollTo()
        compose.waitForIdle()
        val before = resultsScroll()
        assertTrue("Results should have scrolled, at $before", before > 0f)

        compose.onNode(hasTestTag("pair/RG")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Red–Green (RG)").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(Routes.RESULTS, nav.currentDestination?.route)
        compose.onNode(hasText("Your pool")).assertIsDisplayed()
        assertEquals(before, resultsScroll(), 0f)
    }
}
