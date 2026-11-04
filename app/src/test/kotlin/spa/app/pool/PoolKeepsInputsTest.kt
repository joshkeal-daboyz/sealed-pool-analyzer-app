package spa.app.pool

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.PoolUiState
import spa.app.nav.Routes
import spa.app.testing.TestImports
import spa.core.intake.PoolFormat

/**
 * SPA-017 criterion 5: a failed paste import keeps the text and the chosen format; a failed file
 * import leaves the current pool untouched, and back from "New pool" returns to it; a cancelled
 * picker leaves the screen as it was.
 */
@RunWith(AndroidJUnit4::class)
class PoolKeepsInputsTest {
    @get:Rule val compose = createComposeRule()

    private val app = PoolApp(compose)

    @Before fun setUp() = TestDocuments.install()

    private fun route() = app.nav.currentDestination?.route

    @Test
    fun aFailedPasteImportKeepsTheTextAndTheChosenFormat() {
        app.launch()
        compose.pasteBox().performTextReplacement(PROSE_TEXT)
        compose.onNodeWithText("List with counts").performClick()
        compose.onNodeWithText("Analyze pool").performClick()
        app.waitForText("This doesn't look like a pool")

        assertEquals(Routes.POOL, route())
        compose.pasteBox().assert(hasText(PROSE_TEXT))
        compose.onNodeWithText("List with counts").assertIsSelected()
        compose
            .onNodeWithText("Reading as List with counts (chosen)")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(PROSE_TEXT, app.viewModel.pastedText.value)
        assertEquals(PoolFormat.TEXT, app.viewModel.chosenFormat.value)
    }

    @Test
    fun aFailedFileImportLeavesTheCurrentPoolAndBackReturnsToIt() {
        app.launch()
        compose.pasteBox().performTextReplacement(TestImports.pool("trk-manabox-01.csv"))
        compose.onNodeWithText("Analyze pool").performClick()
        app.waitForText("85 cards in 71 entries")
        val pool = app.viewModel.currentPool.value

        compose.onNodeWithText("New pool").performClick()
        compose.waitForIdle()
        app.pick(TestDocuments.put("empty.csv", ByteArray(0)))
        app.waitForText("This file is empty")

        assertEquals(Routes.POOL, route())
        assertEquals(Routes.RESULTS, app.nav.previousBackStackEntry?.destination?.route)
        assertSame(pool, app.viewModel.currentPool.value)

        compose.onNodeWithContentDescription("Back").assertHasClickAction().performClick()
        compose.waitForIdle()
        assertEquals(Routes.RESULTS, route())
        assertNull(app.nav.previousBackStackEntry)
        compose.onNodeWithText("85 cards in 71 entries").assertIsDisplayed()
        compose.onNodeWithText("Read as ManaBox CSV").assertIsDisplayed()
        assertEquals(0, count("This file is empty"))

        // New pool again: Pool is as Empty, with no stale error card.
        compose.onNodeWithText("New pool").performClick()
        compose.waitForIdle()
        assertEquals(Routes.POOL, route())
        assertEquals(0, count("This file is empty"))
    }

    @Test
    fun aFailedFileImportWithNoPoolLeavesPoolOnly() {
        app.launch()
        app.pick(TestDocuments.put("empty.csv", ByteArray(0)))
        app.waitForText("This file is empty")
        assertEquals(Routes.POOL, route())
        assertNull(app.nav.previousBackStackEntry)
        assertNull(app.viewModel.currentPool.value)
    }

    @Test
    fun aCancelledPickerLeavesTheScreenAsItWas() {
        app.launch()
        val text = TestImports.pool("trk-collector-01.txt")
        compose.pasteBox().performTextReplacement(text)
        compose.onNodeWithText("Collector numbers").performClick()
        compose.waitForIdle()

        app.pick(null)

        assertEquals(1, app.picker.launches.size)
        assertEquals(Routes.POOL, route())
        assertNull(app.nav.previousBackStackEntry)
        compose.pasteBox().assert(hasText(text))
        compose.onNodeWithText("Collector numbers").assertIsSelected()
        compose.onNodeWithText("Reading as Collector numbers (chosen)").assertIsDisplayed()
        assertEquals(PoolUiState.NoPool, app.viewModel.state.value)
        assertEquals(emptyList<String>(), app.sink.events.filter { it == "import-confirmed" })
    }

    private fun count(text: String) =
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size
}
