package spa.app.results

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import spa.app.Snapshots
import spa.app.importing.PoolSource
import spa.app.testing.A11yChecks
import spa.app.ui.theme.SpaTheme

/**
 * Criterion 6: snapshot 12, `results_clearPoolDialog` (`trk-manabox-01.csv`, dialog open), with A1
 * and A4 (touch targets and labels) and A5 (no text overflow at font scale 2.0, both buttons
 * reachable) run on the same state. The capture is the whole screen, the dialog's window over
 * Results, since a dialog is a window of its own.
 */
@RunWith(AndroidJUnit4::class)
class ClearPoolDialogSnapshotTest {
    @get:Rule val compose = createComposeRule()

    private val clean = ResultsKit.loaded("trk-manabox-01.csv")

    /** Results for [clean], with the header's "Clear pool" clicked. */
    private fun showDialog(fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { ResultsKit.Screen(clean) }
        compose.waitForIdle()
        compose
            .onNode(hasText("Clear pool") and hasClickAction() and !hasAnyAncestor(isDialog()))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Clear this pool?") and hasAnyAncestor(isDialog())).assertExists()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun resultsClearPoolDialog() {
        showDialog()
        captureScreenRoboImage(Snapshots.path("results_clearPoolDialog"), Snapshots.options)
    }

    @Test
    @Config(qualifiers = TALL)
    fun resultsClearPoolDialogClickChecks() {
        showDialog()
        A11yChecks.assertTouchTargets(compose)
        A11yChecks.assertClickablesLabelled(compose)
    }

    @Test
    @Config(qualifiers = TALL)
    fun resultsClearPoolDialogAtFontScale200ClickChecks() {
        showDialog(A11yChecks.LARGE_FONT_SCALE)
        A11yChecks.assertTouchTargets(compose)
        A11yChecks.assertClickablesLabelled(compose)
    }

    /** The dialog open from the first frame, over Results, for A5's one `setContent`. */
    @Composable
    private fun OpenDialog() {
        SpaTheme {
            CompositionLocalProvider(LocalBaseline provides ResultsKit.baseline) {
                ResultsScreen(clean, ResultsActions(onNewPool = {}, onAbout = {}))
                ClearPoolDialog(PoolSource.File("trk-manabox-01.csv"), onConfirm = {}) {}
            }
        }
    }

    @Test
    fun resultsClearPoolDialogAtFontScale200() {
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { OpenDialog() }
        compose.onNode(hasText("Clear this pool?") and hasAnyAncestor(isDialog())).assertExists()
        listOf("Keep pool", "Clear pool").forEach {
            compose
                .onNode(hasText(it) and hasClickAction() and hasAnyAncestor(isDialog()))
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }

    /** A5 for the header's new action: at font scale 2.0, Clear pool is reachable and opens. */
    @Test
    fun theHeadersClearPoolIsReachableAtFontScale200() {
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { ResultsKit.Screen(clean) }
        compose
            .onNode(hasText("Clear pool") and hasClickAction() and !hasAnyAncestor(isDialog()))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Clear this pool?") and hasAnyAncestor(isDialog())).assertExists()
    }

    private companion object {
        /** The Pixel 6 width, tall enough for Results' whole scroll at font scale 2. */
        const val TALL = "w411dp-h9000dp-xxhdpi"
    }
}
