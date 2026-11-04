package spa.app.about

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.nav.Routes
import spa.app.pool.PoolApp
import spa.app.pool.pasteBox
import spa.app.testing.TestImports

/**
 * Criterion 5, B3: About is one click from Pool with no pool and from Results after a pasted
 * `trk-manabox-01.csv`, through the app's real import, and back returns to where it came from.
 */
@RunWith(AndroidJUnit4::class)
class AboutNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val app = PoolApp(compose)

    private fun route(): String? = app.nav.currentDestination?.route

    private fun assertAboutShown() {
        assertEquals(Routes.ABOUT, route())
        compose.onNode(hasText("Card data") and isHeading()).assertIsDisplayed()
        compose.onNodeWithText("Card data snapshot: ${AboutKit.snapshotDate}").assertIsDisplayed()
    }

    private fun clickAbout() {
        compose.onNodeWithContentDescription("About").assertHasClickAction().performClick()
        compose.waitForIdle()
    }

    private fun clickBack() {
        compose.onNodeWithContentDescription("Back").assertHasClickAction().performClick()
        compose.waitForIdle()
    }

    @Test
    fun fromPoolWithNoPoolOneClickShowsAboutAndBackReturnsToPool() {
        app.launch()
        assertEquals(Routes.POOL, route())
        clickAbout()
        assertAboutShown()
        clickBack()
        assertEquals(Routes.POOL, route())
        assertNull(app.nav.previousBackStackEntry)
        compose.pasteBox().assertIsDisplayed()
    }

    @Test
    fun fromResultsAfterAPastedImportOneClickShowsAboutAndBackReturnsToResults() {
        app.launch()
        compose.pasteBox().performTextReplacement(TestImports.pool("trk-manabox-01.csv"))
        compose.onNodeWithText("Analyze pool").performClick()
        app.waitForText("New pool")
        compose.waitForIdle()
        assertEquals(Routes.RESULTS, route())

        clickAbout()
        assertAboutShown()
        clickBack()
        assertEquals(Routes.RESULTS, route())
        compose.onNodeWithText("85 cards in 71 entries").assertIsDisplayed()
    }
}
