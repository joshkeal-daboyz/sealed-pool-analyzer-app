package spa.app.results

import android.view.MotionEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import spa.app.PoolUiState
import spa.app.importing.PoolSource
import spa.app.ui.theme.SpaTheme

/**
 * Criterion 4 and C1 on Results: the header's "Clear pool" opens the dialog with the spec's copy
 * and the buttons "Keep pool" and "Clear pool", no "OK" or "Cancel"; "Keep pool", back, and a tap
 * outside close it without clearing; "Clear pool" in it clears.
 */
@RunWith(AndroidJUnit4::class)
class ClearPoolDialogTest {
    @get:Rule val compose = createComposeRule()

    private var cleared = 0

    private fun show(state: PoolUiState = ResultsKit.loaded("trk-manabox-01.csv")) {
        compose.setContent {
            SpaTheme {
                CompositionLocalProvider(LocalBaseline provides ResultsKit.baseline) {
                    ResultsScreen(
                        state = state,
                        actions =
                            ResultsActions(
                                onNewPool = {},
                                onAbout = {},
                                onClearPool = { cleared++ },
                            ),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun inDialog(text: String): SemanticsNodeInteraction =
        compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun headerClearPool(): SemanticsNodeInteraction =
        compose.onNode(hasText("Clear pool") and hasClickAction() and !hasAnyAncestor(isDialog()))

    private fun openDialog() {
        headerClearPool().performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun dialogShown(): Boolean =
        compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theHeaderHasClearPoolBesideNewPool() {
        show()
        compose.onNode(hasText("New pool") and hasClickAction()).assertExists()
        headerClearPool().assertHasClickAction()
        assertTrue("no dialog until it is asked for", !dialogShown())
    }

    @Test
    fun loadingHasNoClearPool() {
        show(PoolUiState.Loading(PoolSource.File("trk-manabox-01.csv")))
        assertEquals(
            0,
            compose.onAllNodes(hasText("Clear pool")).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun clearPoolOpensTheDialogWithTheSpecsCopy() {
        show()
        openDialog()

        inDialog("Clear this pool?").assertExists()
        inDialog(
                "The app will forget trk-manabox-01.csv. To see it again you'll need to " +
                    "import it again."
            )
            .assertExists()
        compose
            .onNode(hasText("Keep pool") and hasClickAction() and hasAnyAncestor(isDialog()))
            .assertExists()
        compose
            .onNode(hasText("Clear pool") and hasClickAction() and hasAnyAncestor(isDialog()))
            .assertExists()
        assertEquals(0, cleared)
    }

    @Test
    fun theDialogSaysNoOkAndNoCancel() {
        show()
        openDialog()

        listOf("OK", "Ok", "Cancel").forEach {
            assertEquals(
                it,
                0,
                compose.onAllNodes(hasText(it, ignoreCase = true)).fetchSemanticsNodes().size,
            )
        }
        val buttons =
            compose
                .onAllNodes(hasClickAction() and hasAnyAncestor(isDialog()))
                .fetchSemanticsNodes()
                .map { ResultsKit.texts(it).joinToString(" ") }
        assertEquals(listOf("Keep pool", "Clear pool"), buttons)
    }

    @Test
    fun aPastedPoolIsCalledThePastedText() {
        show(ResultsKit.loaded(ResultsKit.success("trk-text-01.txt"), PoolSource.Pasted))
        openDialog()

        inDialog(
                "The app will forget the pasted text. To see it again you'll need to import it " +
                    "again."
            )
            .assertExists()
    }

    @Test
    fun keepPoolClosesTheDialogWithoutClearing() {
        show()
        openDialog()

        inDialog("Keep pool").performClick()
        compose.waitForIdle()

        assertTrue(!dialogShown())
        assertEquals(0, cleared)
        compose.onNodeWithText("Your pool").assertExists()
    }

    @Test
    fun backClosesTheDialogWithoutClearing() {
        show()
        openDialog()

        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()

        assertTrue(!dialogShown())
        assertEquals(0, cleared)
    }

    @Test
    fun aTapOutsideClosesTheDialogWithoutClearing() {
        show()
        openDialog()

        compose.runOnUiThread {
            val dialog = ShadowDialog.getLatestDialog()
            val outside = -OUTSIDE_PX
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                val event = MotionEvent.obtain(0L, 0L, action, outside, outside, 0)
                dialog.onTouchEvent(event)
                event.recycle()
            }
        }
        compose.waitForIdle()

        assertTrue(!dialogShown())
        assertEquals(0, cleared)
    }

    @Test
    fun clearPoolInTheDialogClears() {
        show()
        openDialog()

        compose
            .onNode(hasText("Clear pool") and hasClickAction() and hasAnyAncestor(isDialog()))
            .performClick()
        compose.waitForIdle()

        assertEquals(1, cleared)
        assertTrue(!dialogShown())
    }

    private companion object {
        const val OUTSIDE_PX = 200f
    }
}
