package spa.app.pool

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.nav.Routes
import spa.app.testing.Goldens
import spa.app.testing.TestImports

/**
 * SPA-017 criterion 6: the picker result is imported through `PoolImporter.importStream` under the
 * document's display name, logs `import-confirmed`, and lands on Results.
 */
@RunWith(AndroidJUnit4::class)
class OpenFileTest {
    @get:Rule val compose = createComposeRule()

    private val app = PoolApp(compose)

    @Before fun setUp() = TestDocuments.install()

    @Test
    fun pickingTrkManabox01LandsOnItsResults() {
        val golden = Goldens.header("trk-manabox-01.csv")
        app.launch()
        app.pick(TestDocuments.pool("trk-manabox-01.csv"))
        app.waitForText("Read as ManaBox CSV")

        assertEquals(Routes.RESULTS, app.nav.currentDestination?.route)
        assertNull(app.nav.previousBackStackEntry)
        compose.onNodeWithText("trk-manabox-01.csv").assertIsDisplayed()
        compose.onNodeWithText("Read as ManaBox CSV").assertIsDisplayed()
        compose
            .onNodeWithText("${golden.totalCards} cards in ${golden.entries} entries")
            .assertIsDisplayed()
        assertEquals(1, app.sink.events.count { it == "import-confirmed" })
    }

    @Test
    fun thePickerOffersCsvAndPlainText() {
        app.launch()
        app.pick(null)
        val types = app.picker.launches.single() as Array<*>
        assertEquals(
            listOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain"),
            types.toList(),
        )
    }

    @Test
    fun theContentImporterReadsTheDisplayNameAndTheStream() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val files = ContentFileImporter(resolver, TestImports.poolImporter)
        val uri = TestDocuments.pool("trk-manabox-01.csv")
        assertEquals("trk-manabox-01.csv", files.displayName(uri))
        val source = PoolSource.File("trk-manabox-01.csv")
        val outcome = runBlocking { files.import(uri, source) } as ImportOutcome.Success
        assertEquals(source, outcome.source)
        assertEquals(TestImports.manabox01.result.pool, outcome.result.pool)
    }

    @Test
    fun aRefusedNameQueryFallsBackToTheUrisLastSegment() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val files = ContentFileImporter(resolver, TestImports.poolImporter)
        val uri = TestDocuments.refusingName("pool.csv", ByteArray(0))
        assertEquals("forbidden-pool.csv", files.displayName(uri))
    }
}
