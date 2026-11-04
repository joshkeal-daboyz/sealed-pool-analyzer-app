package spa.app.testing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.PoolUiState
import spa.app.importing.PoolSource
import spa.app.pool.PoolEntryScreen
import spa.app.results.ResultsScreen
import spa.app.ui.theme.SpaTheme

/** Criterion 7: each shared check fails on a bad composable and passes on this ticket's screens. */
@RunWith(AndroidJUnit4::class)
class A11yChecksTest {
    @get:Rule val compose = createComposeRule()

    // The deliberately bad composables.

    @Test
    fun touchTargetsFailOnA40DpClickable() {
        compose.setContent {
            Box(Modifier.size(40.dp).testTag("small").clickable {}) { Text("Go") }
        }
        val failure =
            assertThrows(AssertionError::class.java) { A11yChecks.assertTouchTargets(compose) }
        val message = failure.message.orEmpty()
        assertTrue(message, message.contains("tag 'small'"))
        assertTrue(message, message.contains("40.0 x 40.0 dp"))
    }

    @Test
    fun touchTargetsIgnoreLabelsAndPassA48DpUnlabelledClickable() {
        compose.setContent { Box(Modifier.size(48.dp).clickable {}) }
        A11yChecks.assertTouchTargets(compose)
    }

    @Test
    fun labelsFailOnAnUnlabelledClickable() {
        compose.setContent { Box(Modifier.size(48.dp).testTag("bare").clickable {}) }
        val failure =
            assertThrows(AssertionError::class.java) {
                A11yChecks.assertClickablesLabelled(compose)
            }
        val message = failure.message.orEmpty()
        assertTrue(message, message.contains("tag 'bare'"))
        assertTrue(message, message.contains("has no label"))
    }

    @Test
    fun labelsIgnoreSizeAndPassASmallLabelledClickable() {
        compose.setContent { Box(Modifier.size(20.dp).clickable {}) { Text("Go") } }
        A11yChecks.assertClickablesLabelled(compose)
    }

    @Test
    fun textOverflowFailsOnAFixedWidthTextThatOverflows() {
        val failure =
            assertThrows(AssertionError::class.java) {
                A11yChecks.assertNoTextOverflowAtFontScale2(compose) {
                    Text(
                        "A line far too long for its box",
                        modifier = Modifier.width(80.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        val message = failure.message.orEmpty()
        assertTrue(message, message.contains("A line far too long for its box"))
    }

    @Test
    fun textOverflowPassesTheSameTextWhenItCanWrap() {
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) {
            Text("A line far too long for its box", modifier = Modifier.width(80.dp))
        }
    }

    @Test
    fun textOverflowChecksAtFontScale2() {
        var scale = 0f
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) {
            scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            Text("Go")
        }
        assertTrue("font scale was $scale", scale == 2f)
    }

    // This ticket's screens.

    @Composable
    private fun Pool() = SpaTheme {
        PoolEntryScreen(
            text = "TRK 1701d",
            onTextChange = {},
            onAnalyze = {},
            onAbout = {},
            onBack = null,
        )
    }

    @Composable
    private fun Loading() = SpaTheme {
        ResultsScreen(PoolUiState.Loading(PoolSource.Pasted), onNewPool = {}, onAbout = {})
    }

    @Composable
    private fun Loaded() = SpaTheme {
        val success = TestImports.manabox01
        ResultsScreen(
            PoolUiState.Loaded(success, PoolSource.Pasted, success.format),
            onNewPool = {},
            onAbout = {},
        )
    }

    private fun clickChecks(content: @Composable () -> Unit) {
        compose.setContent(content)
        A11yChecks.assertTouchTargets(compose)
        A11yChecks.assertClickablesLabelled(compose)
    }

    @Test fun poolPassesTouchTargetsAndLabels() = clickChecks { Pool() }

    @Test
    fun poolBackArrowPassesTouchTargetsAndLabels() = clickChecks {
        SpaTheme { PoolEntryScreen("", {}, {}, {}, onBack = {}) }
    }

    @Test fun resultsLoadingPassesTouchTargetsAndLabels() = clickChecks { Loading() }

    @Test fun resultsLoadedPassesTouchTargetsAndLabels() = clickChecks { Loaded() }

    @Test
    fun poolHasNoTextOverflowAtFontScale2() =
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { Pool() }

    @Test
    fun resultsLoadingHasNoTextOverflowAtFontScale2() =
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { Loading() }

    @Test
    fun resultsLoadedHasNoTextOverflowAtFontScale2() =
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { Loaded() }
}
