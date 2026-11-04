package spa.app.about

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
import spa.app.testing.A11yChecks

/**
 * Criterion 6: the About snapshots from the spec's list (15 `about`, 16 `about_fontScale200`), each
 * state checked for A1 and A4 (touch targets and labels) and A5 (no text overflow at font scale
 * 2.0, the back arrow and the Scryfall sentence reachable with `performScrollTo` and clickable).
 * Captures are the 411 x 914 dp viewport, what the screen shows first: Roborazzi captures a view's
 * laid-out bounds and cannot stitch a scroll.
 *
 * A1 and A4 run on a surface [TALL] enough to lay the whole scroll out, because the shared checks
 * read each node's laid-out bounds in the root, which are clipped to nothing below the fold.
 */
@RunWith(AndroidJUnit4::class)
class AboutSnapshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(Snapshots.path(name), Snapshots.options)
    }

    private fun clickChecks(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { AboutKit.Screen() }
        compose.waitForIdle()
        A11yChecks.assertTouchTargets(compose)
        A11yChecks.assertClickablesLabelled(compose)
    }

    // Snapshot 15, about.

    @Test
    fun about() {
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent { AboutKit.Screen() }
        capture("about")
    }

    @Test @Config(qualifiers = TALL) fun aboutClickChecks() = clickChecks(1f)

    // Snapshot 16, about_fontScale200: also A5.

    @Test
    fun aboutFontScale200() {
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { AboutKit.Screen() }
        capture("about_fontScale200")
        compose.onNodeWithContentDescription("Back").assertHasClickAction()
        compose
            .onNode(hasText("Card data comes from", substring = true))
            .performScrollTo()
            .assertHasClickAction()
    }

    @Test
    @Config(qualifiers = TALL)
    fun aboutFontScale200ClickChecks() = clickChecks(A11yChecks.LARGE_FONT_SCALE)

    private companion object {
        /** The Pixel 6 width, tall enough for About's whole scroll at font scale 2. */
        const val TALL = "w411dp-h4000dp-xxhdpi"
    }
}
