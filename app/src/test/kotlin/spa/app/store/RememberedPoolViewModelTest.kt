package spa.app.store

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import spa.app.FileImporter
import spa.app.Importer
import spa.app.PoolUiState
import spa.app.PoolViewModel
import spa.app.importing.ImportFailureKind
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.pool.PROSE_TEXT
import spa.app.results.ResultsKit
import spa.app.results.ResultsUi
import spa.app.testing.TestImports
import spa.core.intake.PoolFormat

/**
 * Criteria 1, 2, and 3 at the ViewModel: a successful import is saved, a failed one leaves the file
 * byte for byte, a new one replaces it; at launch the saved pool is re-imported with its stored
 * format and gives the same results; a saved pool that cannot be re-read is the saved-pool
 * unreadable error; and Clear pool deletes the file. Imports go through the app's real
 * [spa.app.importing.PoolImporter], run to completion before the ViewModel sees them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RememberedPoolViewModelTest {
    @get:Rule val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val store by lazy { PoolStore(folder.root) }

    /** Every import the ViewModel asked for, as (text, format, source). */
    private val imports = mutableListOf<Triple<String, PoolFormat?, PoolSource>>()

    /** The real import, run at once. */
    private val importer = Importer { text, format, source ->
        imports += Triple(text, format, source)
        runBlocking { TestImports.poolImporter.import(text, format, source) }
    }

    /** "Open file" of [bytes] named [name], read once through the real stream import. */
    private class OneFile(private val name: String, private val bytes: ByteArray) : FileImporter {
        var opened = 0

        override fun displayName(uri: Uri): String = name

        override suspend fun import(uri: Uri, source: PoolSource): ImportOutcome = runBlocking {
            TestImports.poolImporter.importStream(
                {
                    opened++
                    bytes.inputStream()
                },
                source,
            )
        }
    }

    private fun json(): JSONObject = JSONObject(store.file.readText())

    private fun manabox01() = TestImports.pool("trk-manabox-01.csv")

    @Test
    fun aSuccessfulPasteIsSavedWithItsTextFormatAndSource() =
        runTest(dispatcher) {
            val vm = PoolViewModel(importer, store = store)
            val text = TestImports.pool("trk-text-01.txt")

            vm.analyze(text, null)
            testScheduler.advanceUntilIdle()

            assertTrue(vm.state.value is PoolUiState.Loaded)
            val json = json()
            assertEquals(setOf("text", "format", "source"), json.keys().asSequence().toSet())
            assertEquals(text, json.getString("text"))
            assertEquals("TEXT", json.getString("format"))
            assertEquals("pasted", json.getString("source"))
        }

    @Test
    fun aPasteReadAsAChosenFormatIsSavedWithThatFormat() =
        runTest(dispatcher) {
            val vm = PoolViewModel(importer, store = store)
            val text = TestImports.pool("trk-collector-01.txt")

            vm.analyze(text, PoolFormat.COLLECTOR)
            testScheduler.advanceUntilIdle()

            assertEquals("COLLECTOR", json().getString("format"))
        }

    @Test
    fun anOpenedFileIsSavedWithItsTextAndNameAndIsReadOnce() =
        runTest(dispatcher) {
            val bytes = manabox01().toByteArray(Charsets.UTF_8)
            val files = OneFile("trk-manabox-01.csv", bytes)
            val vm = PoolViewModel(importer, files, store)

            vm.analyzeFile(Uri.parse("content://docs/doc/1"))
            testScheduler.advanceUntilIdle()

            assertTrue(vm.state.value is PoolUiState.Loaded)
            val json = json()
            assertEquals(String(bytes, Charsets.UTF_8), json.getString("text"))
            assertEquals("MANABOX", json.getString("format"))
            assertEquals("trk-manabox-01.csv", json.getString("source"))
            assertEquals(1, files.opened)
        }

    @Test
    fun aFailedImportLeavesTheFileByteForByte() =
        runTest(dispatcher) {
            val vm =
                PoolViewModel(importer, OneFile("broken.csv", byteArrayOf(0xC3.toByte())), store)
            vm.analyze(manabox01(), null)
            testScheduler.advanceUntilIdle()
            val before = store.file.readBytes()

            vm.analyze(PROSE_TEXT, null)
            testScheduler.advanceUntilIdle()
            assertTrue(vm.state.value is PoolUiState.ImportFailed)
            assertArrayEquals(before, store.file.readBytes())

            vm.analyzeFile(Uri.parse("content://docs/doc/2"))
            testScheduler.advanceUntilIdle()
            assertEquals(
                ImportFailureKind.UNREADABLE,
                (vm.state.value as PoolUiState.ImportFailed).kind,
            )
            assertArrayEquals(before, store.file.readBytes())
        }

    @Test
    fun aFailedImportWithNoSavedPoolWritesNoFile() =
        runTest(dispatcher) {
            val vm = PoolViewModel(importer, store = store)

            vm.analyze(PROSE_TEXT, null)
            testScheduler.advanceUntilIdle()

            assertFalse(store.file.exists())
        }

    @Test
    fun aNewPoolReplacesTheSavedOne() =
        runTest(dispatcher) {
            val vm = PoolViewModel(importer, store = store)
            vm.analyze(manabox01(), null)
            testScheduler.advanceUntilIdle()

            val text = TestImports.pool("trk-text-01.txt")
            vm.analyze(text, null)
            testScheduler.advanceUntilIdle()

            assertEquals(text, json().getString("text"))
            assertEquals("TEXT", json().getString("format"))
        }

    @Test
    fun withNoSavedPoolLaunchIsNoPool() =
        runTest(dispatcher) {
            val vm = PoolViewModel(importer, store = store)
            testScheduler.advanceUntilIdle()

            assertFalse(vm.startsWithSavedPool)
            assertEquals(PoolUiState.NoPool, vm.state.value)
            assertTrue(imports.isEmpty())
        }

    @Test
    fun aSavedPoolIsReImportedAtLaunchWithItsFormatAndGivesTheSameResults() =
        runTest(dispatcher) {
            val first =
                PoolViewModel(
                    importer,
                    OneFile("trk-manabox-01.csv", manabox01().toByteArray()),
                    store,
                )
            first.analyzeFile(Uri.parse("content://docs/doc/1"))
            testScheduler.advanceUntilIdle()
            val original = first.currentPool.value!!
            val saved = store.file.readBytes()

            val source = PoolSource.File("trk-manabox-01.csv")
            val vm = PoolViewModel(importer, store = store)
            assertTrue(vm.startsWithSavedPool)
            assertEquals(PoolUiState.Loading(source), vm.state.value)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(Triple(manabox01(), PoolFormat.MANABOX, source)), imports)
            val restored = vm.state.value as PoolUiState.Loaded
            assertEquals(restored, vm.currentPool.value)
            assertEquals(source, restored.source)
            assertEquals(PoolFormat.MANABOX, restored.format)
            assertEquals(
                ResultsUi.from(original.result, ResultsKit.baseline),
                ResultsUi.from(restored.result, ResultsKit.baseline),
            )
            assertArrayEquals("the restore leaves the file", saved, store.file.readBytes())
        }

    @Test
    fun theStoredFormatIsUsedNotDetection() =
        runTest(dispatcher) {
            // Collector numbers, stored as chosen by hand.
            val text = TestImports.pool("trk-collector-01.txt")
            store.save(text, PoolFormat.COLLECTOR, PoolSource.Pasted)

            val vm = PoolViewModel(importer, store = store)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(Triple(text, PoolFormat.COLLECTOR, PoolSource.Pasted)), imports)
            assertEquals(PoolFormat.COLLECTOR, (vm.state.value as PoolUiState.Loaded).format)
        }

    @Test
    fun aSavedPoolThatNowResolvesNoEntriesIsTheSavedPoolUnreadableError() =
        runTest(dispatcher) {
            store.save(PROSE_TEXT, PoolFormat.TEXT, PoolSource.File("old-pool.txt"))
            val before = store.file.readBytes()

            val vm = PoolViewModel(importer, store = store)
            assertTrue(vm.startsWithSavedPool)
            testScheduler.advanceUntilIdle()

            assertEquals(
                PoolUiState.ImportFailed(
                    ImportFailureKind.SAVED_POOL_UNREADABLE,
                    PoolSource.File("old-pool.txt"),
                    emptyList(),
                ),
                vm.state.value,
            )
            assertNull(vm.currentPool.value)
            assertArrayEquals("only Clear pool deletes it", before, store.file.readBytes())
        }

    @Test
    fun aCorruptSavedFileIsTheSavedPoolUnreadableErrorWithNoName() =
        runTest(dispatcher) {
            store.file.writeText("{ not json")

            val vm = PoolViewModel(importer, store = store)
            testScheduler.advanceUntilIdle()

            assertFalse("a file that names no pool opens Pool at once", vm.startsWithSavedPool)
            assertEquals(
                PoolUiState.ImportFailed(
                    ImportFailureKind.SAVED_POOL_UNREADABLE,
                    PoolViewModel.UNNAMED_SAVED_POOL,
                    emptyList(),
                ),
                vm.state.value,
            )
            assertTrue(imports.isEmpty())
            assertTrue(store.file.exists())
        }

    @Test
    fun aSavedFileWithAnUnknownFormatKeepsItsSourceName() =
        runTest(dispatcher) {
            store.file.writeText("""{"text":"x","format":"DECKBOX","source":"pool.csv"}""")

            val vm = PoolViewModel(importer, store = store)
            testScheduler.advanceUntilIdle()

            assertEquals(
                PoolUiState.ImportFailed(
                    ImportFailureKind.SAVED_POOL_UNREADABLE,
                    PoolSource.File("pool.csv"),
                    emptyList(),
                ),
                vm.state.value,
            )
        }

    @Test
    fun clearPoolDeletesTheFileAndForgetsThePool() =
        runTest(dispatcher) {
            val vm = PoolViewModel(importer, store = store)
            vm.analyze(manabox01(), null)
            testScheduler.advanceUntilIdle()
            vm.onTextChange("left in the box")
            vm.onFormatChoose(PoolFormat.TEXT)

            vm.clearPool()

            assertFalse(store.file.exists())
            assertEquals(PoolUiState.NoPool, vm.state.value)
            assertNull(vm.currentPool.value)
            assertEquals("", vm.pastedText.value)
            assertNull(vm.chosenFormat.value)
        }

    @Test
    fun clearPoolOnAnUnreadableSavedPoolDeletesTheFile() =
        runTest(dispatcher) {
            store.file.writeText("{ not json")
            val vm = PoolViewModel(importer, store = store)

            vm.clearPool()

            assertFalse(store.file.exists())
            assertEquals(PoolUiState.NoPool, vm.state.value)
        }

    @Test
    fun aViewModelWithNoStoreStillImports() =
        runTest(dispatcher) {
            val vm = PoolViewModel(importer)
            vm.analyze(manabox01(), null)
            testScheduler.advanceUntilIdle()
            vm.clearPool()

            assertFalse(vm.startsWithSavedPool)
            assertEquals(PoolUiState.NoPool, vm.state.value)
            assertNull(vm.currentPool.value)
        }
}
