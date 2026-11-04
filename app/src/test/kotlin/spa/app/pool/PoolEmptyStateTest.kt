package spa.app.pool

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** SPA-017 criterion 1: the Empty state, as the spec gives it, with A6 for "Add your pool". */
@RunWith(AndroidJUnit4::class)
class PoolEmptyStateTest {
    @get:Rule val compose = createComposeRule()

    private val app = PoolApp(compose)

    private fun count(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().size

    @Test
    fun addYourPoolIsAHeading() {
        app.launch()
        compose.onNode(hasText("Add your pool") and isHeading).assertIsDisplayed()
    }

    @Test
    fun theEmptyStateShowsTheSpecsCopyAndControls() {
        app.launch()
        compose.onNodeWithText("Open a ManaBox CSV, or paste a list.").assertIsDisplayed()
        compose.onNodeWithText("Open file").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Open file").assertIsEnabled()
        compose.onNodeWithText("or").assertIsDisplayed()
        compose.pasteBox().assertIsDisplayed()
        compose
            .onNodeWithText("ManaBox CSV, a list with counts, or collector numbers")
            .assertIsDisplayed()
        compose.onNodeWithText("Analyze pool").assertIsNotEnabled()
    }

    @Test
    fun theChooserIsOnAutoAndThereIsNoDetectedLine() {
        app.launch()
        compose.onNodeWithText("Auto").assertIsSelected()
        listOf("ManaBox CSV", "List with counts", "Collector numbers").forEach {
            compose.onNodeWithText(it).assertIsNotSelected()
        }
        assertEquals(0, count("Detected:", substring = true))
        assertEquals(0, count("Reading as", substring = true))
    }

    @Test
    fun thereIsNoErrorCard() {
        app.launch()
        listOf(
                "This file is empty",
                "This doesn't look like a pool",
                "This pool is from another set",
                "Couldn't read this file",
                "Card data is missing",
            )
            .forEach { assertEquals(it, 0, count(it)) }
    }
}
