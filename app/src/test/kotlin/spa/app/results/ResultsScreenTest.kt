package spa.app.results

import androidx.compose.runtime.State
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.PoolUiState
import spa.app.PoolViewModel
import spa.app.SpaApp
import spa.app.importing.PoolSource
import spa.app.testing.Goldens
import spa.app.testing.TestImports
import spa.app.timing.SpaTiming
import spa.app.timing.TimingSink
import spa.app.ui.theme.SpaTheme

/** Criterion 6 and R5: the Results top bar, the pool header card, and the Loading state. */
@RunWith(AndroidJUnit4::class)
class ResultsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: PoolUiState) {
        compose.setContent { SpaTheme { ResultsScreen(state, onNewPool = {}, onAbout = {}) } }
        compose.waitForIdle()
    }

    private fun count(text: String): Int =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private fun indicators(): Int =
        compose.onAllNodes(hasContentDescription("Reading your pool")).fetchSemanticsNodes().size

    @Test
    fun pastingManabox01ShowsItsHeaderReadFromTheGolden() {
        val golden = Goldens.header("trk-manabox-01.csv")
        compose.setContent {
            SpaApp(PoolViewModel(TestImports.importer), SpaTiming(TimingSink { _, _ -> }))
        }
        compose.waitForIdle()
        compose
            .onNode(hasSetTextAction())
            .performTextReplacement(TestImports.pool("trk-manabox-01.csv"))
        compose.onNodeWithText("Analyze pool").performClick()
        compose.waitUntil(TIMEOUT) { count("New pool") > 0 }

        compose.onNodeWithText("Your pool").assertIsDisplayed()
        compose.onNodeWithText("Pasted").assertIsDisplayed()
        compose
            .onNodeWithText("Read as ${Goldens.FORMAT_NAMES.getValue(golden.format)}")
            .assertIsDisplayed()
        compose
            .onNodeWithText("${golden.totalCards} cards in ${golden.entries} entries")
            .assertIsDisplayed()
        assertEquals(0, golden.skipped)
        assertEquals(
            0,
            compose.onAllNodes(hasText("skipped", substring = true)).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun theTopBarHasYourPoolAndTheAboutIcon() {
        show(
            PoolUiState.Loaded(
                TestImports.manabox01,
                PoolSource.Pasted,
                TestImports.manabox01.format,
            )
        )
        compose.onNodeWithText("Your pool").assertIsDisplayed()
        compose.onNodeWithContentDescription("About").assertHasClickAction()
        compose.onNodeWithText("New pool").assertHasClickAction()
    }

    @Test
    fun aFileSourceShowsTheFileName() {
        val success = TestImports.manabox01
        show(PoolUiState.Loaded(success, PoolSource.File("my pool.csv"), success.format))
        compose.onNodeWithText("my pool.csv").assertIsDisplayed()
        assertEquals(0, count("Pasted"))
    }

    @Test
    fun oneCardInOneEntryIsSingular() {
        val success = TestImports.success("TRK 1701d")
        show(PoolUiState.Loaded(success, PoolSource.Pasted, success.format))
        compose.onNodeWithText("Read as Collector numbers").assertIsDisplayed()
        compose.onNodeWithText("1 card in 1 entry").assertIsDisplayed()
    }

    @Test
    fun skippedLinesAreShownWhenAboveZero() {
        val text = "\n\n\n" + TestImports.pool("trk-text-01.txt")
        val success = TestImports.success(text)
        val skipped = success.result.skippedLines
        assertTrue("expected skipped lines, got $skipped", skipped > 1)
        show(PoolUiState.Loaded(success, PoolSource.Pasted, success.format))
        compose.onNodeWithText("$skipped blank or header lines skipped").assertIsDisplayed()
    }

    @Test
    fun oneSkippedLineIsSingular() {
        val success = TestImports.success("\nTRK 1701d")
        assertEquals(1, success.result.skippedLines)
        show(PoolUiState.Loaded(success, PoolSource.Pasted, success.format))
        compose.onNodeWithText("1 blank or header line skipped").assertIsDisplayed()
    }

    @Test
    fun loadingShowsTheSourceAndReadingYourPoolAndNoButtons() {
        show(PoolUiState.Loading(PoolSource.Pasted))
        compose.onNodeWithText("Your pool").assertIsDisplayed()
        compose.onNodeWithText("Pasted").assertIsDisplayed()
        compose.onNodeWithText("Reading your pool…").assertIsDisplayed()
        compose.onNodeWithContentDescription("About").assertHasClickAction()
        assertEquals(0, count("New pool"))
        assertEquals(
            0,
            compose.onAllNodes(hasText("Read as", substring = true)).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun theLoadingIndicatorAppearsOnlyOnceLoadingHasLasted300Ms() {
        compose.mainClock.autoAdvance = false
        // The same timer the screen uses, started in the same composition, read without a frame.
        lateinit var due: State<Boolean>
        compose.setContent {
            due = rememberIndicatorDue()
            SpaTheme { ResultsScreen(PoolUiState.Loading(PoolSource.Pasted), {}, {}) }
        }
        compose.mainClock.advanceTimeBy(299, ignoreFrameDuration = true)
        assertFalse(due.value)
        assertEquals(0, indicators())
        compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        assertTrue(due.value)
        // The indicator is drawn on the frame after the 300 ms are up.
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, indicators())
    }

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
