package spa.app.nav

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.Importer
import spa.app.PoolViewModel
import spa.app.SpaApp
import spa.app.importing.ImportFailureKind
import spa.app.importing.ImportOutcome
import spa.app.testing.TestImports
import spa.app.timing.SpaTiming
import spa.app.timing.TimingSink

/** Criterion 3 and A2: the routes, the back stack after an import, New pool, and About. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    private val succeeds = Importer { _, _, _ -> TestImports.manabox01 }

    private fun launch(importer: Importer = succeeds) {
        val timing = SpaTiming(TimingSink { _, _ -> })
        val vm = PoolViewModel(importer)
        compose.setContent {
            nav = rememberNavController()
            SpaApp(vm, timing, nav)
        }
        compose.waitForIdle()
    }

    private fun route(): String? = nav.currentDestination?.route

    private fun analyze(text: String = "pasted pool") {
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        compose.onNodeWithText("Analyze pool").performClick()
        compose.waitForIdle()
    }

    private fun waitForResults() {
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithTextCount("New pool") > 0 }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(
        text: String
    ): Int = onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    @Test
    fun withNoPoolLaunchShowsPool() {
        launch()
        assertEquals(Routes.POOL, route())
        assertNull(nav.previousBackStackEntry)
        compose.onNodeWithText("Sealed Pool Analyzer").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTextCount("Back"))
        assertTrue(
            compose.onAllNodes(hasContentDescriptionExactly("Back")).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun theFourRoutesAreRegistered() {
        launch()
        listOf("pool", "results", "pair/{pair}", "about").forEach { r ->
            assertNotNull(r, nav.graph.findNode(r))
        }
        assertEquals(listOf("pool", "results", "pair/{pair}", "about"), Routes.ALL)
    }

    @Test
    fun thePairRouteOpensPairDetail() {
        launch()
        compose.runOnUiThread { nav.navigate(Routes.pair("WU")) }
        compose.waitForIdle()
        assertEquals(Routes.PAIR, route())
        compose.onNodeWithText("White–Blue (WU)").assertIsDisplayed()
        compose.onNodeWithContentDescription("About").assertHasClickAction()
        compose.onNodeWithContentDescription("Back").assertHasClickAction()
    }

    @Test
    fun afterASuccessfulImportTheBackStackIsResultsOnly() {
        launch()
        analyze()
        waitForResults()
        assertEquals(Routes.RESULTS, route())
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun systemBackFromResultsFinishesTheActivity() {
        launch()
        analyze()
        waitForResults()
        assertFalse(compose.activity.isFinishing)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertTrue(compose.activity.isFinishing)
    }

    @Test
    fun newPoolOpensPoolWithABackArrowAndBackReturnsToTheSameResults() {
        launch()
        analyze()
        waitForResults()
        compose.onNodeWithText("85 cards in 71 entries").assertIsDisplayed()

        compose.onNodeWithText("New pool").performClick()
        compose.waitForIdle()
        assertEquals(Routes.POOL, route())
        assertEquals(Routes.RESULTS, nav.previousBackStackEntry?.destination?.route)
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertHasClickAction()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(Routes.RESULTS, route())
        assertNull(nav.previousBackStackEntry)
        compose.onNodeWithText("85 cards in 71 entries").assertIsDisplayed()
    }

    @Test
    fun systemBackFromNewPoolReturnsToTheSameResults() {
        launch()
        analyze()
        waitForResults()
        compose.onNodeWithText("New pool").performClick()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(Routes.RESULTS, route())
        assertFalse(compose.activity.isFinishing)
        compose.onNodeWithText("85 cards in 71 entries").assertIsDisplayed()
    }

    @Test
    fun theAboutIconOnPoolOpensAbout() {
        launch()
        compose.onNodeWithContentDescription("About").assertHasClickAction().performClick()
        compose.waitForIdle()
        assertEquals(Routes.ABOUT, route())
        compose.onNodeWithContentDescription("Back").assertHasClickAction().performClick()
        compose.waitForIdle()
        assertEquals(Routes.POOL, route())
    }

    @Test
    fun theAboutIconOnResultsOpensAbout() {
        launch()
        analyze()
        waitForResults()
        compose.onNodeWithContentDescription("About").assertHasClickAction().performClick()
        compose.waitForIdle()
        assertEquals(Routes.ABOUT, route())
        compose.onNodeWithText("About").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(Routes.RESULTS, route())
    }

    @Test
    fun aFailedImportLeavesPoolOnlyWithThePastedTextKept() {
        launch(
            Importer { _, _, source ->
                ImportOutcome.Failure(ImportFailureKind.NOT_A_POOL, source, emptyList())
            }
        )
        analyze("not a pool")
        compose.waitUntil(TIMEOUT) { route() == Routes.POOL }
        compose.waitForIdle()
        assertNull(nav.previousBackStackEntry)
        compose.onNodeWithText("not a pool").assertIsDisplayed()
    }

    private fun hasContentDescriptionExactly(text: String) =
        androidx.compose.ui.test.hasContentDescription(text)

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
