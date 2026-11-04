package spa.app

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.importing.ImportOutcome
import spa.app.testing.TestImports
import spa.app.timing.SpaTiming
import spa.app.timing.TimingSink

/**
 * Snapshot tests. Each compares against its baseline in `src/test/snapshots` when the gate runs
 * `verifyRoborazziDebug`; `./gradlew :app:recordRoborazziDebug` rewrites the baselines.
 */
@RunWith(AndroidJUnit4::class)
class ShellSnapshotTest {
    @get:Rule val compose = createComposeRule()

    /**
     * Snapshot 6, `results_loading`: a pasted `trk-manabox-01.csv` held in Loading, past the 300 ms
     * after which the indicator shows. The spec's "remembered" pool is SPA-022's. The clock is
     * paused so the indicator's animation is captured at the same frame every run.
     */
    @Test
    fun resultsLoading() {
        val held = Importer { _, _, _ -> CompletableDeferred<ImportOutcome>().await() }
        compose.setContent { SpaApp(PoolViewModel(held), SpaTiming(TimingSink { _, _ -> })) }
        compose
            .onNode(hasSetTextAction())
            .performTextReplacement(TestImports.pool("trk-manabox-01.csv"))
        compose.onNodeWithText("Analyze pool").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(HELD_MS)
        compose.onRoot().captureRoboImage(Snapshots.path("results_loading"), Snapshots.options)
    }

    private companion object {
        const val HELD_MS = 1_000L
    }
}
