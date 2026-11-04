package spa.app.timing

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import spa.app.PoolViewModel
import spa.app.SpaApp
import spa.app.SpaApplication
import spa.app.testing.RecordingSink
import spa.app.testing.TestImports

/** Criterion 9: the four timing events, each once per occurrence, in order. */
@RunWith(AndroidJUnit4::class)
class SpaTimingTest {
    @get:Rule val compose = createComposeRule()

    private var now = 1000L
    private val sink = RecordingSink()
    private val timing = SpaTiming(sink) { now++ }

    @Test
    fun aPasteImportLogsTheFourEventsInOrder() {
        timing.appCreated()
        compose.setContent { SpaApp(PoolViewModel(TestImports.importer), timing) }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextReplacement("TRK 1701d")
        compose.onNodeWithText("Analyze pool").performClick()
        awaitEvents("results-shown", 1)
        compose.waitForIdle()

        assertEquals(
            listOf("app-created", "first-screen-shown", "import-confirmed", "results-shown"),
            sink.events,
        )
        sink.lines.forEach { (tag, message) ->
            assertEquals("SpaTiming", tag)
            assertTrue(message, message.matches(Regex("[a-z-]+ \\d+")))
        }
    }

    @Test
    fun eachEventCarriesTheClockReading() {
        timing.importConfirmed()
        assertEquals("SpaTiming" to "import-confirmed 1000", sink.lines.single())
    }

    @Test
    fun firstScreenShownIsLoggedOncePerProcess() {
        timing.firstScreenShown()
        timing.firstScreenShown()
        assertEquals(listOf("first-screen-shown"), sink.events)
    }

    @Test
    fun resultsShownIsLoggedOncePerImport() {
        val first = Any()
        val second = Any()
        timing.resultsShown(first)
        timing.resultsShown(first)
        timing.resultsShown(second)
        assertEquals(listOf("results-shown", "results-shown"), sink.events)
    }

    @Test
    fun aSecondImportLogsImportConfirmedAndResultsShownAgain() {
        compose.setContent { SpaApp(PoolViewModel(TestImports.importer), timing) }
        repeat(2) {
            compose.waitForIdle()
            if (it == 1) compose.onNodeWithText("New pool").performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("TRK 1701d")
            compose.onNodeWithText("Analyze pool").performClick()
            awaitEvents("results-shown", it + 1)
        }
        compose.waitForIdle()
        assertEquals(
            listOf(
                "first-screen-shown",
                "import-confirmed",
                "results-shown",
                "import-confirmed",
                "results-shown",
            ),
            sink.events,
        )
    }

    @Test
    fun theApplicationLogsAppCreatedToLogcat() {
        ApplicationProvider.getApplicationContext<SpaApplication>()
        val logged = ShadowLog.getLogsForTag("SpaTiming").map { it.msg }
        assertEquals(1, logged.count { it.startsWith("app-created ") })
        assertTrue(logged.toString(), logged.first().matches(Regex("app-created \\d+")))
    }

    /**
     * Waits until [event] has been logged [times] times. Each check reads the semantics tree, which
     * lets Compose and the main looper go idle; reading only the sink would not, and the import's
     * result, posted to the main thread, would never be delivered.
     */
    private fun awaitEvents(event: String, times: Int) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            sink.events.count { it == event } == times
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
