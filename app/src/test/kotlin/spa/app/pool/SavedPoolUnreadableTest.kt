package spa.app.pool

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import spa.app.PoolViewModel
import spa.app.importing.PoolSource
import spa.app.nav.Routes
import spa.app.store.PoolStore
import spa.app.store.SavedPool
import spa.app.testing.TestImports
import spa.core.intake.PoolFormat

/**
 * Criterion 3: a remembered pool that cannot be re-read (a corrupt file, or text that now resolves
 * no entries) opens Pool with the card "Your last pool couldn't be read", its body, and a "Clear
 * pool" button that deletes the file with no further confirmation.
 */
@RunWith(AndroidJUnit4::class)
class SavedPoolUnreadableTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val folder = TemporaryFolder()

    private val app = PoolApp(compose)

    private val store by lazy { PoolStore(folder.root) }

    private fun launch() {
        app.launch(PoolViewModel(TestImports.importer, store = store))
        app.waitForText(TITLE)
    }

    private fun shows(text: String): Boolean =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun assertPoolOnly() {
        assertEquals(Routes.POOL, app.nav.currentDestination?.route)
        assertNull(app.nav.previousBackStackEntry)
    }

    @Test
    fun aCorruptFileOpensPoolWithTheCardForAPoolWithNoName() {
        store.file.writeText("{ not json")
        launch()

        compose.onNodeWithText(TITLE).assertExists()
        compose.onNodeWithText("Your pool was saved, but the app can't read it now.").assertExists()
        compose.onNode(hasText("Clear pool") and hasClickAction()).assertHasClickAction()
        compose.pasteBox().assertExists()
        assertPoolOnly()
    }

    @Test
    fun aSavedFileThatNowResolvesNoEntriesNamesItsSource() {
        store.save(PROSE_TEXT, PoolFormat.TEXT, PoolSource.File("old-pool.txt"))
        launch()

        compose
            .onNodeWithText("old-pool.txt was saved, but the app can't read it now.")
            .assertExists()
        assertPoolOnly()
        assertFalse("the card lists no lines", shows("Line 1"))
    }

    @Test
    fun aPastedPoolIsCalledThePastedText() {
        store.save(PROSE_TEXT, PoolFormat.TEXT, PoolSource.Pasted)
        launch()

        compose
            .onNodeWithText("The pasted text was saved, but the app can't read it now.")
            .assertExists()
    }

    @Test
    fun clearPoolDeletesTheFileWithNoFurtherConfirmation() {
        store.file.writeText("{ not json")
        launch()

        compose.onNode(hasText("Clear pool") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()

        assertFalse(store.file.exists())
        assertTrue("no dialog", compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty())
        assertFalse(shows("Clear this pool?"))
        assertFalse(shows(TITLE))
        assertFalse(shows("Clear pool"))
        compose.onNodeWithText("Add your pool").assertExists()
        assertPoolOnly()
    }

    @Test
    fun otherErrorCardsHaveNoClearPool() {
        app.launch(PoolViewModel(TestImports.importer, store = store))
        compose.pasteBox().performTextReplacement(PROSE_TEXT)
        compose.onNode(hasText("Analyze pool") and hasClickAction()).performClick()
        app.waitForText("This doesn't look like a pool")

        assertFalse(shows("Clear pool"))
        assertFalse(store.file.exists())
    }

    @Test
    fun anImportAfterTheErrorReplacesTheUnreadableFile() {
        store.file.writeText("{ not json")
        launch()

        compose.pasteBox().performTextReplacement(TestImports.pool("trk-text-01.txt"))
        compose.onNode(hasText("Analyze pool") and hasClickAction()).performClick()
        app.waitForText("New pool")

        val saved = store.read() as SavedPool.Pool
        assertEquals(TestImports.pool("trk-text-01.txt"), saved.text)
    }

    private companion object {
        const val TITLE = "Your last pool couldn't be read"
    }
}
