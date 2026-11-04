package spa.app.pool

import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import spa.app.PoolUiState
import spa.app.PoolViewModel
import spa.app.Snapshots
import spa.app.SpaApp
import spa.app.testing.A11yChecks
import spa.app.testing.TestImports
import spa.app.timing.SpaTiming
import spa.app.timing.TimingSink

/**
 * SPA-017 criterion 7: the Pool screen's snapshots from the spec's list, and each state checked for
 * A1 and A4 (touch targets and labels) and A5 (no text overflow at font scale 2.0, every action
 * reachable with `performScrollTo` and clickable). Captures are the 411 x 914 dp viewport, what the
 * screen shows first.
 *
 * A1 and A4 run on a surface [TALL] enough to lay the whole scroll out: the shared checks read each
 * node's laid-out bounds in the root, which are clipped to nothing for a node scrolled out of the
 * viewport. The Pool screen's content is in a vertical scroll, so its nodes are the same size on
 * either surface.
 */
@RunWith(AndroidJUnit4::class)
class PoolSnapshotTest {
    @get:Rule val compose = createComposeRule()

    private val app = PoolApp(compose)

    /** A ViewModel holding pasted `trk-collector-01.txt`, chooser on Auto. */
    private fun pasteDetected() =
        app.realViewModel().apply { onTextChange(TestImports.pool("trk-collector-01.txt")) }

    /** A ViewModel whose import of the ten prose lines has failed, through the app's import. */
    private fun errorNotAPool(): PoolViewModel {
        val vm = app.realViewModel().apply { onTextChange(PROSE_TEXT) }
        vm.analyze(PROSE_TEXT, null)
        val deadline = System.currentTimeMillis() + PoolApp.TIMEOUT
        while (vm.state.value is PoolUiState.Loading && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
        assertTrue("${vm.state.value}", vm.state.value is PoolUiState.ImportFailed)
        return vm
    }

    @Composable
    private fun Screen(vm: PoolViewModel) {
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry = app.picker
            }
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            SpaApp(vm, SpaTiming(TimingSink { _, _ -> }))
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(Snapshots.path(name), Snapshots.options)
    }

    private fun clickChecks(vm: PoolViewModel, fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { Screen(vm) }
        A11yChecks.assertTouchTargets(compose)
        A11yChecks.assertClickablesLabelled(compose)
    }

    /** A5 for [vm]'s state; [onShown] runs before anything scrolls. */
    private fun largeFontChecks(
        vm: PoolViewModel,
        actions: List<String>,
        onShown: () -> Unit = {},
    ) {
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { Screen(vm) }
        onShown()
        compose.onNodeWithContentDescription("About").assertHasClickAction()
        actions.forEach { label ->
            compose.onNode(hasText(label) and hasClickAction()).performScrollTo()
        }
        compose.onNode(hasSetTextAction()).performScrollTo()
    }

    // Snapshot 1, pool_empty.

    @Test
    fun poolEmpty() {
        app.launch()
        capture("pool_empty")
    }

    @Test @Config(qualifiers = TALL) fun poolEmptyClickChecks() = clickChecks(app.realViewModel())

    // Snapshot 2, pool_empty_fontScale200: also A5 for the Empty state.

    @Test
    fun poolEmptyFontScale200() {
        largeFontChecks(app.realViewModel(), listOf("Open file") + CHIPS) {
            capture("pool_empty_fontScale200")
        }
    }

    @Test
    @Config(qualifiers = TALL)
    fun poolEmptyFontScale200ClickChecks() =
        clickChecks(app.realViewModel(), A11yChecks.LARGE_FONT_SCALE)

    // Snapshot 3, pool_pasteDetected.

    @Test
    fun poolPasteDetected() {
        app.launch(pasteDetected())
        compose.onNodeWithText("Detected: Collector numbers").assertExists()
        compose.onNodeWithText("Analyze pool").assertHasClickAction()
        capture("pool_pasteDetected")
    }

    @Test
    @Config(qualifiers = TALL)
    fun poolPasteDetectedClickChecks() = clickChecks(pasteDetected())

    @Test
    fun poolPasteDetectedAtFontScale200() =
        largeFontChecks(pasteDetected(), listOf("Open file", "Analyze pool") + CHIPS)

    // Snapshot 4, pool_errorNotAPool.

    @Test
    fun poolErrorNotAPool() {
        app.launch(errorNotAPool())
        compose.onNodeWithText("This doesn't look like a pool").assertExists()
        capture("pool_errorNotAPool")
    }

    @Test
    @Config(qualifiers = TALL)
    fun poolErrorNotAPoolClickChecks() = clickChecks(errorNotAPool())

    @Test
    fun poolErrorNotAPoolAtFontScale200() =
        largeFontChecks(
            errorNotAPool(),
            listOf("Show all 10 lines", "Open file", "Analyze pool") + CHIPS,
        )

    private companion object {
        /** The Pixel 6 width, tall enough for every Pool state's whole scroll at font scale 2. */
        const val TALL = "w411dp-h4000dp-xxhdpi"
        const val POLL_MS = 10L
        val CHIPS = listOf("Auto", "ManaBox CSV", "List with counts", "Collector numbers")
    }
}
