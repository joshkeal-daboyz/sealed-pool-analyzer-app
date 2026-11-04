package spa.app.share

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.testing.Goldens

/**
 * Criteria 2 and 4: the real Activity, launched by a share or an "Open with", lands on Results with
 * the shared pool and nothing under it; a file that fails lands on Pool with its error card and
 * nothing under it; an intent with no file, or one that cannot be opened, says so instead of
 * crashing.
 */
@RunWith(AndroidJUnit4::class)
class ShareLaunchTest {
    @get:Rule val compose = createEmptyComposeRule()

    private var scenario: SharedApp? = null

    @Before fun setUp() = SharedDocuments.install()

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(intent: Intent): SharedApp = SharedApp.launch(intent).also { scenario = it }

    private fun assertResultsFor(file: String, app: SharedApp) {
        val golden = Goldens.header(file)
        val counts = "${golden.totalCards} cards in ${golden.entries} entries"
        compose.waitForText(counts)
        compose.onNodeWithText(file).assertIsDisplayed()
        compose.onNodeWithText(counts).assertIsDisplayed()
        compose.onNodeWithText("New pool").assertIsDisplayed()
        assertTrue("Results is the whole back stack", app.backLeavesTheApp())
    }

    private fun assertPoolCard(title: String, body: String, app: SharedApp) {
        compose.waitForText(title)
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(body, substring = true).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Paste your pool")).assertExists()
        assertFalse(compose.shows("New pool"))
        assertTrue("Pool is the whole back stack", app.backLeavesTheApp())
    }

    @Test
    fun aShareOfTrkManabox01LandsOnItsResults() {
        val app = launch(Shares.send(SharedDocuments.pool("trk-manabox-01.csv")))
        assertResultsFor("trk-manabox-01.csv", app)
    }

    @Test
    fun anOpenWithOfTrkManabox01LandsOnItsResults() {
        val app = launch(Shares.view(SharedDocuments.pool("trk-manabox-01.csv")))
        assertResultsFor("trk-manabox-01.csv", app)
    }

    @Test
    fun aShareUnderAnotherCsvTypeLandsOnResultsToo() {
        val uri = SharedDocuments.pool("trk-manabox-02.csv")
        val app = launch(Shares.send(uri, type = "text/comma-separated-values"))
        assertResultsFor("trk-manabox-02.csv", app)
    }

    @Test
    fun anEmptySharedFileLandsOnPoolWithTheEmptyCard() {
        val app = launch(Shares.send(SharedDocuments.put("empty.csv", ByteArray(0))))
        assertPoolCard("This file is empty", "empty.csv has no cards in it.", app)
    }

    @Test
    fun anEmptyOpenWithFileLandsOnPoolWithTheEmptyCard() {
        val app = launch(Shares.view(SharedDocuments.put("empty.csv", ByteArray(0))))
        assertPoolCard("This file is empty", "empty.csv has no cards in it.", app)
    }

    @Test
    fun aShareWithNoFileSaysItCouldNotReadIt() {
        val app = launch(Shares.send(null))
        assertPoolCard("Couldn't read this file", "couldn't be opened as text.", app)
    }

    @Test
    fun anOpenWithWithNoFileSaysItCouldNotReadIt() {
        val app = launch(Shares.view(null))
        assertPoolCard("Couldn't read this file", "couldn't be opened as text.", app)
    }

    @Test
    fun aSharedUriThatCannotBeOpenedSaysItCouldNotReadIt() {
        val app = launch(Shares.send(SharedDocuments.missing("gone.csv")))
        assertPoolCard("Couldn't read this file", "gone.csv couldn't be opened as text.", app)
    }

    @Test
    fun theLauncherStillOpensPool() {
        val app = launch(Shares.launcher())
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Paste your pool")).assertIsDisplayed()
        assertFalse(compose.shows("Couldn't read this file"))
        assertTrue(app.backLeavesTheApp())
    }

    @Test
    fun rotationKeepsResultsAndDoesNotImportAgain() {
        val app = launch(Shares.send(SharedDocuments.pool("trk-manabox-01.csv")))
        val golden = Goldens.header("trk-manabox-01.csv")
        compose.waitForText("${golden.totalCards} cards in ${golden.entries} entries")
        // A rotation re-creates the Activity with the same share intent.
        SharedDocuments.missing("trk-manabox-01.csv")
        app.recreate()
        compose.waitForIdle()
        assertFalse(compose.shows("Couldn't read this file"))
        assertResultsFor("trk-manabox-01.csv", app)
    }
}
