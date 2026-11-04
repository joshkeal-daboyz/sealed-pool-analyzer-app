package spa.app.nav

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import spa.app.Importer
import spa.app.PoolViewModel
import spa.app.SpaApp
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.store.PoolStore
import spa.app.testing.TestImports
import spa.app.timing.SpaTiming
import spa.app.timing.TimingSink
import spa.core.intake.PoolFormat

/**
 * The Navigation flow with a remembered pool: launch opens Results (Loading) as the whole back
 * stack, a re-read that fails moves to Pool alone, and after Clear pool Pool is the only screen.
 */
@RunWith(AndroidJUnit4::class)
class RememberedPoolNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val folder = TemporaryFolder()

    private lateinit var nav: NavHostController

    private val store by lazy { PoolStore(folder.root) }

    private fun launch(importer: Importer) {
        val vm = PoolViewModel(importer, store = store)
        compose.setContent {
            nav = rememberNavController()
            SpaApp(vm, SpaTiming(TimingSink { _, _ -> }), nav)
        }
        compose.waitForIdle()
    }

    private fun shows(text: String): Boolean =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aRememberedPoolOpensResultsInLoadingAsTheWholeStack() {
        store.save(TestImports.pool("trk-manabox-01.csv"), PoolFormat.MANABOX, FILE)
        launch { _, _, _ -> CompletableDeferred<ImportOutcome>().await() }

        assertEquals(Routes.RESULTS, nav.currentDestination?.route)
        assertNull(nav.previousBackStackEntry)
        compose.onNodeWithText("trk-manabox-01.csv").assertExists()
        compose.onNodeWithText("Reading your pool…").assertExists()
        assertFalse("Loading has no buttons", shows("Clear pool"))
    }

    @Test
    fun aRememberedPoolThatFailsToReReadMovesToPoolAlone() {
        store.save("not a card", PoolFormat.TEXT, FILE)
        launch(TestImports.importer)

        compose.waitUntil(TIMEOUT) { shows("Your last pool couldn't be read") }
        compose.waitForIdle()
        assertEquals(Routes.POOL, nav.currentDestination?.route)
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun afterClearPoolPoolIsTheOnlyScreen() {
        store.save(TestImports.pool("trk-manabox-01.csv"), PoolFormat.MANABOX, FILE)
        launch(TestImports.importer)
        compose.waitUntil(TIMEOUT) { shows("New pool") }

        compose
            .onNode(hasText("Clear pool") and hasClickAction() and !hasAnyAncestor(isDialog()))
            .performScrollTo()
            .performClick()
        compose
            .onNode(hasText("Clear pool") and hasClickAction() and hasAnyAncestor(isDialog()))
            .performClick()
        compose.waitUntil(TIMEOUT) { shows("Pool cleared") }

        assertEquals(Routes.POOL, nav.currentDestination?.route)
        assertNull(nav.previousBackStackEntry)
        assertFalse(store.file.exists())
        compose.onNodeWithText("Add your pool").assertExists()
    }

    private companion object {
        val FILE = PoolSource.File("trk-manabox-01.csv")
        const val TIMEOUT = 30_000L
    }
}
