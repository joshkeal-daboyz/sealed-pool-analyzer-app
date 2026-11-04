package spa.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import spa.app.importing.ImportFailureKind
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.testing.TestImports
import spa.core.intake.PoolFormat

/** Criterion 4: the ViewModel's states, and the latest import wins. */
@OptIn(ExperimentalCoroutinesApi::class)
class PoolViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** An importer whose every call waits for the test to complete it. */
    private class SuspendingImporter(private val nonCancellable: Boolean = false) : Importer {
        val calls = mutableListOf<Call>()

        class Call(val text: String, val format: PoolFormat?, val source: PoolSource) {
            val outcome = CompletableDeferred<ImportOutcome>()
        }

        override suspend fun import(
            text: String,
            format: PoolFormat?,
            source: PoolSource,
        ): ImportOutcome {
            val call = Call(text, format, source)
            calls += call
            return if (nonCancellable) withContext(NonCancellable) { call.outcome.await() }
            else call.outcome.await()
        }
    }

    @Test
    fun startsWithNoPool() {
        val vm = PoolViewModel(SuspendingImporter())
        assertEquals(PoolUiState.NoPool, vm.state.value)
    }

    @Test
    fun analyzeMovesToLoadingThenLoaded() =
        runTest(dispatcher) {
            val importer = SuspendingImporter()
            val vm = PoolViewModel(importer)
            vm.analyze("text", null)
            assertEquals(PoolUiState.Loading(PoolSource.Pasted), vm.state.value)
            testScheduler.advanceUntilIdle()
            assertEquals(1, importer.calls.size)

            val success = TestImports.manabox01
            importer.calls.single().outcome.complete(success)
            testScheduler.advanceUntilIdle()
            assertEquals(
                PoolUiState.Loaded(success, PoolSource.Pasted, PoolFormat.MANABOX),
                vm.state.value,
            )
        }

    @Test
    fun analyzePassesTheTextTheChosenFormatAndSourcePasted() =
        runTest(dispatcher) {
            val importer = SuspendingImporter()
            val vm = PoolViewModel(importer)
            vm.analyze("TRK 1701d", PoolFormat.COLLECTOR)
            testScheduler.advanceUntilIdle()
            val call = importer.calls.single()
            assertEquals("TRK 1701d", call.text)
            assertEquals(PoolFormat.COLLECTOR, call.format)
            assertEquals(PoolSource.Pasted, call.source)
        }

    @Test
    fun aFailedImportIsImportFailedWithItsKindSourceAndLines() =
        runTest(dispatcher) {
            val importer = SuspendingImporter()
            val vm = PoolViewModel(importer)
            vm.analyze("nothing", null)
            testScheduler.advanceUntilIdle()
            val lines = TestImports.manabox01.result.unresolved
            importer.calls
                .single()
                .outcome
                .complete(
                    ImportOutcome.Failure(ImportFailureKind.NOT_A_POOL, PoolSource.Pasted, lines)
                )
            testScheduler.advanceUntilIdle()
            assertEquals(
                PoolUiState.ImportFailed(ImportFailureKind.NOT_A_POOL, PoolSource.Pasted, lines),
                vm.state.value,
            )
        }

    @Test
    fun missingSetDataIsDataMissing() =
        runTest(dispatcher) {
            val importer = SuspendingImporter()
            val vm = PoolViewModel(importer)
            vm.analyze("text", null)
            testScheduler.advanceUntilIdle()
            importer.calls.single().outcome.complete(ImportOutcome.DataMissing(Exception("gone")))
            testScheduler.advanceUntilIdle()
            assertEquals(PoolUiState.DataMissing, vm.state.value)
        }

    @Test fun theLatestImportWins() = latestImportWins(nonCancellable = false)

    @Test
    fun theLatestImportWinsEvenWhenTheEarlierImportIgnoresCancellation() =
        latestImportWins(nonCancellable = true)

    private fun latestImportWins(nonCancellable: Boolean) =
        runTest(dispatcher) {
            val importer = SuspendingImporter(nonCancellable)
            val vm = PoolViewModel(importer)
            val seen = mutableListOf<PoolUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.state.collect { seen += it }
            }

            vm.analyze("A", null)
            testScheduler.advanceUntilIdle()
            vm.analyze("B", null)
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("A", "B"), importer.calls.map { it.text })

            val a = TestImports.manabox01
            val b = TestImports.success("TRK 1701d")
            importer.calls[0].outcome.complete(a)
            testScheduler.advanceUntilIdle()
            assertEquals(PoolUiState.Loading(PoolSource.Pasted), vm.state.value)
            importer.calls[1].outcome.complete(b)
            testScheduler.advanceUntilIdle()

            assertEquals(PoolUiState.Loaded(b, PoolSource.Pasted, b.format), vm.state.value)
            assertFalse(
                "A's result was emitted: $seen",
                seen.any { it is PoolUiState.Loaded && it.result === a },
            )
            assertTrue(seen.any { it is PoolUiState.Loaded && it.result === b })
        }

    @Test
    fun pastedTextIsKeptAndANewPoolClearsIt() {
        val vm = PoolViewModel(SuspendingImporter())
        vm.onTextChange("TRK 1701d")
        assertEquals("TRK 1701d", vm.pastedText.value)
        vm.startNewPool()
        assertEquals("", vm.pastedText.value)
    }
}
