package spa.app.pool

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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import spa.app.PoolViewModel
import spa.app.Snapshots
import spa.app.SpaApp
import spa.app.store.PoolStore
import spa.app.testing.A11yChecks
import spa.app.testing.TestImports
import spa.app.timing.SpaTiming
import spa.app.timing.TimingSink

/**
 * Criterion 6: snapshot 5, `pool_errorSavedPoolUnreadable` (a remembered pool file that fails to
 * read), with A1 and A4 (touch targets and labels) and A5 (no text overflow at font scale 2.0,
 * every action reachable) run on the same state. The saved file names `trk-manabox-01.csv` but its
 * format is one the app does not know, so it fails to read at once, with nothing to wait for.
 */
@RunWith(AndroidJUnit4::class)
class SavedPoolUnreadableSnapshotTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val folder = TemporaryFolder()

    private val picker = FakePicker()

    private fun viewModel(): PoolViewModel {
        val store = PoolStore(folder.root)
        store.file.writeText("""{"text":"","format":"DECKBOX","source":"trk-manabox-01.csv"}""")
        return PoolViewModel(TestImports.importer, store = store)
    }

    @Composable
    private fun Screen(vm: PoolViewModel) {
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry = picker
            }
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            SpaApp(vm, SpaTiming(TimingSink { _, _ -> }))
        }
    }

    private fun show(fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        val vm = viewModel()
        compose.setContent { Screen(vm) }
        compose.waitForIdle()
        compose.onNodeWithText(TITLE).assertExists()
    }

    @Test
    fun poolErrorSavedPoolUnreadable() {
        show()
        compose
            .onNodeWithText("trk-manabox-01.csv was saved, but the app can't read it now.")
            .assertExists()
        compose.onRoot().captureRoboImage(Snapshots.path(NAME), Snapshots.options)
    }

    @Test
    @Config(qualifiers = TALL)
    fun poolErrorSavedPoolUnreadableClickChecks() {
        show()
        A11yChecks.assertTouchTargets(compose)
        A11yChecks.assertClickablesLabelled(compose)
    }

    @Test
    @Config(qualifiers = TALL)
    fun poolErrorSavedPoolUnreadableFontScale200ClickChecks() {
        show(A11yChecks.LARGE_FONT_SCALE)
        A11yChecks.assertTouchTargets(compose)
        A11yChecks.assertClickablesLabelled(compose)
    }

    @Test
    fun poolErrorSavedPoolUnreadableAtFontScale200() {
        val vm = viewModel()
        A11yChecks.assertNoTextOverflowAtFontScale2(compose) { Screen(vm) }
        compose.onNodeWithText(TITLE).assertExists()
        compose.onNodeWithContentDescription("About").assertHasClickAction()
        ACTIONS.forEach { compose.onNode(hasText(it) and hasClickAction()).performScrollTo() }
        compose.onNode(hasSetTextAction()).performScrollTo()
    }

    private companion object {
        const val NAME = "pool_errorSavedPoolUnreadable"
        const val TITLE = "Your last pool couldn't be read"

        /** The state's actions: clear it, open a file, choose a format (and paste). */
        val ACTIONS =
            listOf(
                "Clear pool",
                "Open file",
                "Auto",
                "ManaBox CSV",
                "List with counts",
                "Collector numbers",
            )

        /** The Pixel 6 width, tall enough for the Pool screen's whole scroll at font scale 2. */
        const val TALL = "w411dp-h4000dp-xxhdpi"
    }
}
