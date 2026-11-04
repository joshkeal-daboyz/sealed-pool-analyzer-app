package spa.app.pool

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
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
import spa.app.PoolViewModel
import spa.app.SpaApp
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.timing.SpaTiming
import spa.app.timing.TimingSink
import spa.core.intake.PoolFormat

/** Criterion 5, P1 and P3: the minimal Pool screen. */
@RunWith(AndroidJUnit4::class)
class PoolEntryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<Triple<String, PoolFormat?, PoolSource>>()

    private val recording = Importer { text, format, source ->
        calls += Triple(text, format, source)
        CompletableDeferred<ImportOutcome>().await()
    }

    private fun launch() {
        compose.setContent { SpaApp(PoolViewModel(recording), SpaTiming(TimingSink { _, _ -> })) }
        compose.waitForIdle()
    }

    private fun analyzeButton() = compose.onNodeWithText("Analyze pool")

    private fun pasteBox() = compose.onNode(hasSetTextAction() and hasText("Paste your pool"))

    @Test
    fun thePasteBoxIsLabelledPasteYourPool() {
        launch()
        pasteBox().assertIsDisplayed()
        // A label, not a placeholder: it is still there once the box has text.
        pasteBox().performTextReplacement("TRK 1701d")
        pasteBox().assertIsDisplayed()
    }

    @Test
    fun analyzeIsNotEnabledWhenTheBoxIsEmpty() {
        launch()
        analyzeButton().assertIsNotEnabled()
    }

    @Test
    fun analyzeIsNotEnabledForSpacesAndBlankLinesOnly() {
        launch()
        pasteBox().performTextReplacement("   \n\n \t \n")
        analyzeButton().assertIsNotEnabled()
    }

    @Test
    fun analyzeIsEnabledWithAnyOtherText() {
        launch()
        pasteBox().performTextReplacement("x")
        analyzeButton().assertIsEnabled()
        pasteBox().performTextReplacement("")
        analyzeButton().assertIsNotEnabled()
    }

    @Test
    fun tappingAnalyzeImportsTheTextAutoDetectedAsPasted() {
        launch()
        pasteBox().performTextReplacement("TRK 1701d")
        analyzeButton().performClick()
        compose.waitForIdle()
        assertEquals(listOf(Triple("TRK 1701d", null, PoolSource.Pasted)), calls)
    }
}
