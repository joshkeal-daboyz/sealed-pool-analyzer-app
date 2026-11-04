package spa.app.pool

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.Importer
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.testing.TestImports
import spa.core.intake.PoolFormat

/** SPA-017 criterion 3 and P2: the manual override, and the format it passes to the import. */
@RunWith(AndroidJUnit4::class)
class FormatChooserTest {
    @get:Rule val compose = createComposeRule()

    private val app = PoolApp(compose)
    private val chips = listOf("Auto", "ManaBox CSV", "List with counts", "Collector numbers")
    private val calls = mutableListOf<Triple<String, PoolFormat?, PoolSource>>()

    private val recording = Importer { text, format, source ->
        calls += Triple(text, format, source)
        CompletableDeferred<ImportOutcome>().await()
    }

    private fun count(text: String) =
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size

    private fun pasteCollector01() {
        compose.pasteBox().performTextReplacement(TestImports.pool("trk-collector-01.txt"))
        compose.mainClock.advanceTimeBy(DETECT_PAUSE_MS + SETTLE_MS)
        compose.waitForIdle()
    }

    @Test
    fun theChipsAreTheFourChoicesInOrderEachWithARoleAndSelectedState() {
        app.launch(app.realViewModel(recording))
        val lefts = chips.map { label ->
            compose
                .onNodeWithText(label)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
                .fetchSemanticsNode()
                .boundsInRoot
        }
        // One row at font scale 1 on a Pixel 6, or wrapped: either way, reading order is the list.
        val order = lefts.map { it.top to it.left }
        assertEquals(order.sortedWith(compareBy({ it.first }, { it.second })), order)
    }

    @Test
    fun choosingCollectorNumbersSelectsItAloneP2() {
        app.launch(app.realViewModel(recording))
        compose.onNodeWithText("Collector numbers").performClick()
        compose.onNodeWithText("Collector numbers").assertIsSelected()
        compose.onNodeWithText("Auto").assertIsNotSelected()
        compose.onNodeWithText("ManaBox CSV").assertIsNotSelected()
        compose.onNodeWithText("List with counts").assertIsNotSelected()
    }

    @Test
    fun choosingListWithCountsShowsTheChosenLineAndHidesDetection() {
        app.launch(app.realViewModel(recording))
        pasteCollector01()
        compose.onNodeWithText("Detected: Collector numbers").assertIsDisplayed()

        compose.onNodeWithText("List with counts").performClick()
        compose.mainClock.advanceTimeBy(DETECT_PAUSE_MS + SETTLE_MS)
        compose.onNodeWithText("Reading as List with counts (chosen)").assertIsDisplayed()
        assertEquals(0, count("Detected:"))
    }

    @Test
    fun analyzingUsesTheChosenFormat() {
        app.launch(app.realViewModel(recording))
        pasteCollector01()
        compose.onNodeWithText("List with counts").performClick()
        compose.onNodeWithText("Analyze pool").performClick()
        compose.waitForIdle()
        val text = TestImports.pool("trk-collector-01.txt")
        assertEquals(listOf(Triple(text, PoolFormat.TEXT, PoolSource.Pasted)), calls)
    }

    @Test
    fun tappingAutoRestoresDetection() {
        app.launch(app.realViewModel(recording))
        pasteCollector01()
        compose.onNodeWithText("List with counts").performClick()
        compose.onNodeWithText("Auto").performClick()
        compose.onNodeWithText("Auto").assertIsSelected()
        compose.onNodeWithText("List with counts").assertIsNotSelected()
        compose.onNodeWithText("Detected: Collector numbers").assertIsDisplayed()
        assertEquals(0, count("Reading as"))

        compose.onNodeWithText("Analyze pool").performClick()
        compose.waitForIdle()
        assertEquals(listOf<PoolFormat?>(null), calls.map { it.second })
    }

    @Test
    fun theChosenFormatIsTheViewModels() {
        app.launch(app.realViewModel(recording))
        compose.onNodeWithText("Collector numbers").performClick()
        compose.waitForIdle()
        assertEquals(PoolFormat.COLLECTOR, app.viewModel.chosenFormat.value)
        compose.onNodeWithText("Auto").performClick()
        compose.waitForIdle()
        assertEquals(null, app.viewModel.chosenFormat.value)
    }

    private companion object {
        /** Time for the frame before the effect starts and the one after it sets the line. */
        const val SETTLE_MS = 100L
    }
}
