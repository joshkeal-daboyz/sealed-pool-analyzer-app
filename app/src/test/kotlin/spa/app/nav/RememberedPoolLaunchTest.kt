package spa.app.nav

import android.content.Intent
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import spa.app.MainActivity
import spa.app.PoolUiState
import spa.app.PoolViewModel
import spa.app.results.ResultsKit
import spa.app.results.ResultsUi
import spa.app.testing.Goldens
import spa.app.testing.TestImports

/**
 * Criteria 2 and 4 on the real Activity over the app's real `filesDir`: a pool imported, then the
 * Activity and its ViewModel created again, opens Results with the same results; with no file
 * launch shows Pool's empty state; and Clear pool, confirmed, deletes the file and leaves Pool
 * (Empty) as the whole back stack with the snackbar "Pool cleared".
 */
@RunWith(AndroidJUnit4::class)
class RememberedPoolLaunchTest {
    @get:Rule val compose = createEmptyComposeRule()

    private var controller: ActivityController<MainActivity>? = null

    private val poolFile: File
        get() = RuntimeEnvironment.getApplication().filesDir.resolve("last-pool.json")

    @After
    fun tearDown() {
        close()
    }

    private fun launch(): MainActivity {
        val intent =
            Intent(RuntimeEnvironment.getApplication(), MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        val built = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        controller = built
        return built.get()
    }

    /** Finishes the Activity as leaving the app does, so its ViewModel goes with it. */
    private fun close() {
        controller?.let { if (!it.get().isDestroyed) it.pause().stop().destroy() }
        controller = null
    }

    private fun viewModelOf(activity: MainActivity): PoolViewModel =
        ViewModelProvider(activity)[PoolViewModel::class.java]

    private fun shows(text: String): Boolean =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) {
        compose.waitUntil(TIMEOUT) { shows(text) }
    }

    private fun backLeavesTheApp(activity: MainActivity): Boolean {
        activity.onBackPressedDispatcher.onBackPressed()
        return activity.isFinishing
    }

    /** Pastes `trk-manabox-01.csv` on Pool and waits for its Results. */
    private fun importManabox01(activity: MainActivity): PoolUiState.Loaded {
        compose
            .onNode(hasSetTextAction() and hasText("Paste your pool"))
            .performTextReplacement(TestImports.pool("trk-manabox-01.csv"))
        compose.onNode(hasText("Analyze pool") and hasClickAction()).performClick()
        waitFor("New pool")
        return viewModelOf(activity).currentPool.value!!
    }

    private fun counts(file: String): String {
        val golden = Goldens.header(file)
        return "${golden.totalCards} cards in ${golden.entries} entries"
    }

    @Test
    fun withNoFileLaunchShowsPoolsEmptyState() {
        assertFalse(poolFile.exists())
        val activity = launch()

        compose.onNodeWithText("Add your pool").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Paste your pool")).assertExists()
        assertFalse(viewModelOf(activity).startsWithSavedPool)
    }

    @Test
    fun aRememberedPoolOpensResultsWithTheSameResults() {
        val first = importManabox01(launch())
        assertTrue(poolFile.exists())
        close()

        val activity = launch()
        val vm = viewModelOf(activity)
        assertTrue("a new ViewModel re-read the file", vm.startsWithSavedPool)
        waitFor(counts("trk-manabox-01.csv"))

        val restored = vm.state.value as PoolUiState.Loaded
        assertEquals(
            ResultsUi.from(first.result, ResultsKit.baseline),
            ResultsUi.from(restored.result, ResultsKit.baseline),
        )
        compose.onNodeWithText("Pasted").assertExists()
        compose.onNodeWithText("Read as ManaBox CSV").assertExists()
        compose.onNodeWithText("Your pool").assertExists()
        assertTrue("Results is the whole back stack", backLeavesTheApp(activity))
    }

    @Test
    fun clearPoolConfirmedLeavesPoolEmptyWithTheSnackbar() {
        importManabox01(launch())
        close()
        val activity = launch()
        waitFor(counts("trk-manabox-01.csv"))

        compose
            .onNode(hasText("Clear pool") and hasClickAction() and !hasAnyAncestor(isDialog()))
            .performScrollTo()
            .performClick()
        compose
            .onNode(hasText("Clear pool") and hasClickAction() and hasAnyAncestor(isDialog()))
            .performClick()
        waitFor("Pool cleared")

        assertFalse(poolFile.exists())
        compose.onNodeWithText("Add your pool").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Paste your pool")).assertExists()
        assertFalse(shows("New pool"))
        assertFalse("the dialog has closed", shows("Clear this pool?"))
        assertEquals(PoolUiState.NoPool, viewModelOf(activity).state.value)
        assertTrue("Pool is the whole back stack", backLeavesTheApp(activity))
    }

    @Test
    fun afterClearPoolTheNextLaunchShowsPool() {
        importManabox01(launch())
        compose
            .onNode(hasText("Clear pool") and hasClickAction() and !hasAnyAncestor(isDialog()))
            .performScrollTo()
            .performClick()
        compose
            .onNode(hasText("Clear pool") and hasClickAction() and hasAnyAncestor(isDialog()))
            .performClick()
        waitFor("Pool cleared")
        close()

        val activity = launch()
        assertFalse(viewModelOf(activity).startsWithSavedPool)
        compose.onNodeWithText("Add your pool").assertExists()
    }

    @Test
    fun keepPoolLeavesTheFileByteForByte() {
        importManabox01(launch())
        val before = poolFile.readBytes()

        compose
            .onNode(hasText("Clear pool") and hasClickAction() and !hasAnyAncestor(isDialog()))
            .performScrollTo()
            .performClick()
        compose
            .onNode(hasText("Keep pool") and hasClickAction() and hasAnyAncestor(isDialog()))
            .performClick()
        compose.waitForIdle()

        assertArrayEquals(before, poolFile.readBytes())
        assertTrue(shows("New pool"))
        assertFalse(shows("Pool cleared"))
    }

    private companion object {
        /** Real imports read the bundled set data the first time, which can take a while. */
        const val TIMEOUT = 30_000L
    }
}
