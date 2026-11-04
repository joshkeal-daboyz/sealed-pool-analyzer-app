package spa.app.pair

import androidx.compose.ui.test.assertHasClickAction
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
import spa.app.Snapshots
import spa.app.results.ResultsKit
import spa.app.testing.A11yChecks

/**
 * Criterion 6: snapshots 13 and 14 from the spec's list, `trk-manabox-01.csv` WU, each state
 * checked for A1 and A4 (touch targets and labels) and A5 (no text overflow at font scale 2.0, Back
 * and About clickable, every section reachable with `performScrollTo`). Captures are the 411 x 914
 * dp viewport, what the screen shows first: Roborazzi captures a view's laid-out bounds and cannot
 * stitch a scroll.
 *
 * A1 and A4 run on a surface [TALL] enough to lay the whole scroll out, because the shared checks
 * read each node's laid-out bounds in the root, which are clipped to nothing below the fold.
 */
@RunWith(AndroidJUnit4::class)
class PairDetailSnapshotTest {
    @get:Rule val compose = createComposeRule()

    private fun wu() = ResultsKit.loaded(PairKit.FILE)

    private fun show(fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { PairKit.Screen(wu()) }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(Snapshots.path(name), Snapshots.options)
    }

    private fun clickChecks(fontScale: Float = 1f) {
        show(fontScale)
        A11yChecks.assertTouchTargets(compose)
        A11yChecks.assertClickablesLabelled(compose)
    }

    // Snapshot 13, pairDetail_WU.

    @Test
    fun pairDetailWU() {
        show()
        capture("pairDetail_WU")
    }

    @Test @Config(qualifiers = TALL) fun pairDetailWUClickChecks() = clickChecks()

    // Snapshot 14, pairDetail_WU_fontScale200: also A5.

    @Test
    fun pairDetailWUFontScale200() {
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { PairKit.Screen(wu()) }
        capture("pairDetail_WU_fontScale200")
        compose.onNodeWithContentDescription("Back").assertHasClickAction()
        compose.onNodeWithContentDescription("About").assertHasClickAction()
        listOf("Lands", "Cards", "Curve", "Splash candidates", "Mana value 7+").forEach {
            compose.onAllNodes(hasText(it))[0].performScrollTo()
        }
    }

    @Test
    @Config(qualifiers = TALL)
    fun pairDetailWUFontScale200ClickChecks() = clickChecks(A11yChecks.LARGE_FONT_SCALE)

    private companion object {
        /** The Pixel 6 width, tall enough for the whole scroll at font scale 2. */
        const val TALL = "w411dp-h9000dp-xxhdpi"
    }
}
