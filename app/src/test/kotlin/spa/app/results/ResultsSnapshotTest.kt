package spa.app.results

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import spa.app.PoolUiState
import spa.app.Snapshots
import spa.app.importing.PoolSource
import spa.app.testing.A11yChecks
import spa.core.metrics.ColorPair

/**
 * Criterion 10: the Results snapshots from the spec's list, each state checked for A1 and A4 (touch
 * targets and labels) and A5 (no text overflow at font scale 2.0, every action reachable with
 * `performScrollTo` and clickable). Captures are the 411 x 914 dp viewport, what the screen shows
 * first: Roborazzi captures a view's laid-out bounds and cannot stitch a scroll.
 *
 * A1 and A4 run on a surface [TALL] enough to lay the whole scroll out, because the shared checks
 * read each node's laid-out bounds in the root, which are clipped to nothing below the fold.
 */
@RunWith(AndroidJUnit4::class)
class ResultsSnapshotTest {
    @get:Rule val compose = createComposeRule()

    private fun clean() = ResultsKit.loaded("trk-manabox-01.csv")

    private fun problems() =
        ResultsKit.loaded(ResultsKit.success("trk-text-misspelled-01.txt"), PoolSource.Pasted)

    private fun wrongSet() = ResultsKit.loaded("trk-manabox-wrong-set-01.csv")

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(Snapshots.path(name), Snapshots.options)
    }

    private fun show(state: PoolUiState, fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { ResultsKit.Screen(state) }
        compose.waitForIdle()
    }

    private fun clickChecks(state: PoolUiState, fontScale: Float = 1f) {
        show(state, fontScale)
        A11yChecks.assertTouchTargets(compose)
        A11yChecks.assertClickablesLabelled(compose)
    }

    /** A5 for [state]; [onShown] runs before anything scrolls. */
    private fun largeFontChecks(state: PoolUiState, onShown: () -> Unit = {}) {
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { ResultsKit.Screen(state) }
        onShown()
        compose.onNodeWithContentDescription("About").assertHasClickAction()
        compose.onNode(hasText("New pool") and hasClickAction()).performScrollTo()
        ColorPair.entries.forEach {
            compose.onNode(hasTestTag("pair/$it") and hasClickAction()).performScrollTo()
        }
        ResultsKit.COLOR_NAMES.forEach {
            compose.onNode(hasText(it) and hasClickAction()).performScrollTo()
        }
    }

    // Snapshot 7, results_clean.

    @Test
    fun resultsClean() {
        show(clean())
        capture("results_clean")
    }

    @Test @Config(qualifiers = TALL) fun resultsCleanClickChecks() = clickChecks(clean())

    // Snapshot 8, results_clean_fontScale200: also A5 for the clean state.

    @Test
    fun resultsCleanFontScale200() {
        largeFontChecks(clean()) { capture("results_clean_fontScale200") }
    }

    @Test
    @Config(qualifiers = TALL)
    fun resultsCleanFontScale200ClickChecks() = clickChecks(clean(), A11yChecks.LARGE_FONT_SCALE)

    // Snapshot 9, results_problems.

    @Test
    fun resultsProblems() {
        show(problems())
        capture("results_problems")
    }

    @Test @Config(qualifiers = TALL) fun resultsProblemsClickChecks() = clickChecks(problems())

    // Snapshot 10, results_problems_fontScale200: also A5 for the problems state.

    @Test
    fun resultsProblemsFontScale200() {
        largeFontChecks(problems()) { capture("results_problems_fontScale200") }
    }

    @Test
    @Config(qualifiers = TALL)
    fun resultsProblemsFontScale200ClickChecks() =
        clickChecks(problems(), A11yChecks.LARGE_FONT_SCALE)

    // Snapshot 11, results_wrongSetLines.

    @Test
    fun resultsWrongSetLines() {
        show(wrongSet())
        capture("results_wrongSetLines")
    }

    @Test @Config(qualifiers = TALL) fun resultsWrongSetLinesClickChecks() = clickChecks(wrongSet())

    @Test fun resultsWrongSetLinesAtFontScale200() = largeFontChecks(wrongSet())

    private companion object {
        /**
         * The Pixel 6 width, tall enough for every Results state's whole scroll at font scale 2.
         */
        const val TALL = "w411dp-h9000dp-xxhdpi"
    }
}
