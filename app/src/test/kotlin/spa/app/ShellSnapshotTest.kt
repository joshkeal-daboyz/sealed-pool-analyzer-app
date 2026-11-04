package spa.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.store.PoolStore
import spa.app.testing.TestImports
import spa.app.timing.SpaTiming
import spa.app.timing.TimingSink
import spa.core.intake.PoolFormat

/**
 * Snapshot tests. Each compares against its baseline in `src/test/snapshots` when the gate runs
 * `verifyRoborazziDebug`; `./gradlew :app:recordRoborazziDebug` rewrites the baselines.
 */
@RunWith(AndroidJUnit4::class)
class ShellSnapshotTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val folder = TemporaryFolder()

    /**
     * Snapshot 6, `results_loading`: launch with a remembered `trk-manabox-01.csv` (SPA-022), its
     * re-read held in Loading past the 300 ms after which the indicator shows. The clock is paused
     * so the indicator's animation is captured at the same frame every run.
     */
    @Test
    fun resultsLoading() {
        val store = PoolStore(folder.root)
        val file = PoolSource.File("trk-manabox-01.csv")
        store.save(TestImports.pool("trk-manabox-01.csv"), PoolFormat.MANABOX, file)
        val held = Importer { _, _, _ -> CompletableDeferred<ImportOutcome>().await() }
        val vm = PoolViewModel(held, store = store)
        compose.setContent { SpaApp(vm, SpaTiming(TimingSink { _, _ -> })) }
        compose.waitForIdle()
        compose.onNodeWithText("trk-manabox-01.csv").assertExists()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(HELD_MS)
        compose.onRoot().captureRoboImage(Snapshots.path("results_loading"), Snapshots.options)
    }

    private companion object {
        const val HELD_MS = 1_000L
    }
}
