package spa.app.pool

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.Importer
import spa.app.importing.ImportOutcome
import spa.app.testing.Goldens
import spa.app.testing.TestImports

/**
 * SPA-017 criterion 2: with "Auto", pasted text shows "Detected: <format>" for the format its
 * golden's `/pool/format` gives, once typing has paused for 300 ms (on the test clock).
 */
@RunWith(AndroidJUnit4::class)
class DetectionTest {
    @get:Rule val compose = createComposeRule()

    private val app = PoolApp(compose)

    private val neverImports = Importer { _, _, _ -> CompletableDeferred<ImportOutcome>().await() }

    private fun detectedLines() =
        compose.onAllNodes(hasText("Detected:", substring = true)).fetchSemanticsNodes().map { node
            ->
            node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        }

    @Test
    fun everyFixtureIsDetectedAsItsGoldensFormat() {
        app.launch(app.realViewModel(neverImports))
        compose.mainClock.autoAdvance = false
        val fixtures =
            (1..5).map { "trk-text-0$it.txt" } +
                "trk-text-misspelled-01.txt" +
                (1..5).map { "trk-collector-0$it.txt" } +
                "trk-collector-bad-number-01.txt" +
                listOf("trk-manabox-01.csv", "trk-manabox-06.csv", "trk-manabox-wrong-set-01.csv")
        fixtures.forEach { file ->
            val expected = Goldens.FORMAT_NAMES.getValue(Goldens.header(file).format)
            compose.pasteBox().performTextReplacement(TestImports.pool(file))
            compose.mainClock.advanceTimeBy(DETECT_PAUSE_MS + SETTLE_MS)
            assertEquals(file, listOf("Detected: $expected"), detectedLines())
        }
    }

    @Test
    fun theDetectedLineWaitsForTypingToPause() {
        app.launch(app.realViewModel(neverImports))
        compose.mainClock.autoAdvance = false
        compose.pasteBox().performTextReplacement(TestImports.pool("trk-collector-01.txt"))
        compose.mainClock.advanceTimeBy(DETECT_PAUSE_MS - SETTLE_MS)
        assertEquals(emptyList<String>(), detectedLines())
        compose.mainClock.advanceTimeBy(SETTLE_MS * 2)
        compose.onNodeWithText("Detected: Collector numbers").assertIsDisplayed()

        // More typing: the old line stays until the next pause, then updates.
        compose.pasteBox().performTextReplacement(TestImports.pool("trk-text-01.txt"))
        compose.mainClock.advanceTimeBy(DETECT_PAUSE_MS - SETTLE_MS)
        assertEquals(listOf("Detected: Collector numbers"), detectedLines())
        compose.mainClock.advanceTimeBy(SETTLE_MS * 2)
        assertEquals(listOf("Detected: List with counts"), detectedLines())
    }

    @Test
    fun clearingTheBoxRemovesTheDetectedLine() {
        app.launch(app.realViewModel(neverImports))
        compose.pasteBox().performTextReplacement(TestImports.pool("trk-text-01.txt"))
        compose.mainClock.advanceTimeBy(DETECT_PAUSE_MS + SETTLE_MS)
        compose.onNodeWithText("Detected: List with counts").assertIsDisplayed()
        compose.pasteBox().performTextReplacement("  \n ")
        compose.waitForIdle()
        assertEquals(emptyList<String>(), detectedLines())
    }

    private companion object {
        /** Time for the frame before the effect starts and the one after it sets the line. */
        const val SETTLE_MS = 100L
    }
}
