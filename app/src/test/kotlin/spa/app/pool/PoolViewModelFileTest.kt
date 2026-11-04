package spa.app.pool

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.FileImporter
import spa.app.Importer
import spa.app.PoolUiState
import spa.app.PoolViewModel
import spa.app.importing.ImportFailureKind
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.testing.TestImports
import spa.core.intake.PoolFormat

/**
 * SPA-017 criteria 3, 5, and 6 at the ViewModel: the chosen format is the one passed, a picked file
 * is imported under its display name, and a failed import touches neither the current pool nor the
 * pasted text and chosen format.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PoolViewModelFileTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val pasted = mutableListOf<Pair<String, PoolFormat?>>()
    private var pasteOutcome: ImportOutcome = TestImports.manabox01
    private val importer = Importer { text, format, _ ->
        pasted += text to format
        pasteOutcome
    }

    /** Files by name: [outcomes] gives each one's outcome, as its source says. */
    private class Files : FileImporter {
        val imported = mutableListOf<Pair<Uri, PoolSource>>()
        val held = CompletableDeferred<Unit>()
        var outcome: (PoolSource) -> ImportOutcome = {
            ImportOutcome.Failure(ImportFailureKind.EMPTY, it, emptyList())
        }

        override fun displayName(uri: Uri): String = "name-of-" + uri.lastPathSegment

        override suspend fun import(uri: Uri, source: PoolSource): ImportOutcome {
            imported += uri to source
            held.await()
            return outcome(source)
        }
    }

    private val uri = Uri.parse("content://docs/doc/7")

    @Test
    fun theChosenFormatIsPassedToTheImport() =
        runTest(dispatcher) {
            val vm = PoolViewModel(importer)
            vm.onTextChange("TRK 1701d")
            vm.onFormatChoose(PoolFormat.TEXT)
            vm.analyze(vm.pastedText.value, vm.chosenFormat.value)
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("TRK 1701d" to PoolFormat.TEXT), pasted)
        }

    @Test
    fun aPickedFileLoadsUnderItsDisplayName() =
        runTest(dispatcher) {
            val files = Files()
            val vm = PoolViewModel(importer, files)
            vm.analyzeFile(uri)
            val source = PoolSource.File("name-of-7")
            assertEquals(PoolUiState.Loading(source), vm.state.value)
            testScheduler.advanceUntilIdle()
            assertEquals(listOf(uri to source), files.imported)

            val success = TestImports.manabox01.copy(source = source)
            files.outcome = { success }
            files.held.complete(Unit)
            testScheduler.advanceUntilIdle()
            assertEquals(PoolUiState.Loaded(success, source, PoolFormat.MANABOX), vm.state.value)
            assertEquals(vm.state.value, vm.currentPool.value)
        }

    @Test
    fun aFailedFileImportLeavesTheCurrentPool() =
        runTest(dispatcher) {
            val files = Files()
            val vm = PoolViewModel(importer, files)
            vm.analyze("pool", null)
            testScheduler.advanceUntilIdle()
            val pool = vm.currentPool.value
            assertEquals(TestImports.manabox01, pool?.result)

            vm.analyzeFile(uri)
            files.held.complete(Unit)
            testScheduler.advanceUntilIdle()
            assertEquals(
                PoolUiState.ImportFailed(
                    ImportFailureKind.EMPTY,
                    PoolSource.File("name-of-7"),
                    emptyList(),
                ),
                vm.state.value,
            )
            assertSame(pool, vm.currentPool.value)

            // "New pool" clears the error card and goes back to the pool's own state.
            vm.startNewPool()
            assertSame(pool, vm.state.value)
        }

    @Test
    fun aFailedPasteImportKeepsTheTextAndFormat() =
        runTest(dispatcher) {
            pasteOutcome =
                ImportOutcome.Failure(ImportFailureKind.NOT_A_POOL, PoolSource.Pasted, emptyList())
            val vm = PoolViewModel(importer)
            vm.onTextChange("prose")
            vm.onFormatChoose(PoolFormat.COLLECTOR)
            vm.analyze("prose", PoolFormat.COLLECTOR)
            testScheduler.advanceUntilIdle()
            assertEquals(PoolUiState.ImportFailed::class, vm.state.value::class)
            assertEquals("prose", vm.pastedText.value)
            assertEquals(PoolFormat.COLLECTOR, vm.chosenFormat.value)
            assertNull(vm.currentPool.value)
        }

    @Test
    fun newPoolResetsTheTextAndTheFormat() {
        val vm = PoolViewModel(importer)
        vm.onTextChange("x")
        vm.onFormatChoose(PoolFormat.MANABOX)
        vm.startNewPool()
        assertEquals("", vm.pastedText.value)
        assertNull(vm.chosenFormat.value)
        assertEquals(PoolUiState.NoPool, vm.state.value)
    }

    @Test
    fun withNoFileImporterEveryFileIsUnreadable() =
        runTest(dispatcher) {
            val vm = PoolViewModel(importer)
            vm.analyzeFile(uri)
            testScheduler.advanceUntilIdle()
            assertEquals(
                PoolUiState.ImportFailed(
                    ImportFailureKind.UNREADABLE,
                    PoolSource.File("7"),
                    emptyList(),
                ),
                vm.state.value,
            )
        }
}
