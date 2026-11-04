package spa.app.share

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

/**
 * Criteria 2 to 4 at the ViewModel: an incoming file is imported as a picked one, an intent with no
 * file is unreadable, and each one is marked once for the navigation to move to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class IncomingImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val imported = mutableListOf<Uri>()

    private val files =
        object : FileImporter {
            override fun displayName(uri: Uri): String = uri.lastPathSegment.orEmpty()

            override suspend fun import(uri: Uri, source: PoolSource): ImportOutcome {
                imported += uri
                return TestImports.manabox01.copy(source = source)
            }
        }

    private val vm = PoolViewModel(Importer { _, _, _ -> error("no paste here") }, files)

    @Test
    fun anIncomingFileIsImportedUnderItsName() = runTest(dispatcher) {
        val uri = Uri.parse("content://x/doc/trk-manabox-01.csv")
        vm.importIncoming(uri)
        assertEquals(PoolUiState.Loading(PoolSource.File("trk-manabox-01.csv")), vm.state.value)
        advanceUntilIdle()
        assertEquals(listOf(uri), imported)
        val loaded = vm.state.value as PoolUiState.Loaded
        assertEquals(PoolSource.File("trk-manabox-01.csv"), loaded.source)
        assertEquals(loaded, vm.currentPool.value)
    }

    @Test
    fun anIntentWithNoFileIsUnreadable() = runTest(dispatcher) {
        vm.importIncoming(null)
        advanceUntilIdle()
        val failed = vm.state.value as PoolUiState.ImportFailed
        assertEquals(ImportFailureKind.UNREADABLE, failed.kind)
        assertEquals(emptyList<Uri>(), imported)
        assertNull(vm.currentPool.value)
    }

    @Test
    fun eachIncomingImportIsMarkedUntilTheNavigationShowsIt() = runTest(dispatcher) {
        assertNull(vm.incomingImport.value)
        vm.importIncoming(Uri.parse("content://x/doc/a.csv"))
        val first = checkNotNull(vm.incomingImport.value)
        vm.importIncoming(Uri.parse("content://x/doc/b.csv"))
        val second = checkNotNull(vm.incomingImport.value)
        assertNotEquals(first, second)

        // Showing an older one leaves the newer one marked.
        vm.onIncomingShown(first)
        assertEquals(second, vm.incomingImport.value)
        vm.onIncomingShown(second)
        assertNull(vm.incomingImport.value)
        advanceUntilIdle()
    }

    @Test
    fun theLatestIncomingImportWins() = runTest(dispatcher) {
        vm.importIncoming(Uri.parse("content://x/doc/trk-manabox-01.csv"))
        vm.importIncoming(Uri.parse("content://x/doc/trk-manabox-02.csv"))
        advanceUntilIdle()
        val loaded = vm.state.value as PoolUiState.Loaded
        assertEquals(PoolSource.File("trk-manabox-02.csv"), loaded.source)
    }
}
