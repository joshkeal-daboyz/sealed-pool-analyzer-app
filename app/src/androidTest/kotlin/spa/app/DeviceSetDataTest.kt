package spa.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.data.SetData
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolImporter
import spa.app.importing.PoolSource

/**
 * Loads the bundled set data through core on a real device, where `java.nio.file.Files` lacks
 * `readString` unless the build desugars it.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSetDataTest {
    private val setData = SetData.forContext(ApplicationProvider.getApplicationContext())

    @Test
    fun loadsTheBundledSetDataThroughCore() = runBlocking {
        val set = setData.load()

        assertEquals(TRK_PRINTINGS, set.index.printings("trk").size)
        assertEquals(SDS_PRINTINGS, set.index.printings("sds").size)
        assertEquals(KITS, set.baseline.kits)
    }

    @Test
    fun importsTheManaBoxFixture() = runBlocking {
        val text =
            InstrumentationRegistry.getInstrumentation()
                .context
                .assets
                .open(FIXTURE)
                .bufferedReader()
                .use { it.readText() }

        val outcome = PoolImporter(setData).import(text, null, PoolSource.File(FIXTURE))

        assertTrue("$outcome", outcome is ImportOutcome.Success)
        val success = outcome as ImportOutcome.Success
        assertEquals(TOTAL_CARDS, success.metrics.totalCards)
        assertEquals(ENTRIES, success.result.pool.entries.size)
    }

    private companion object {
        const val FIXTURE = "trk-manabox-01.csv"
        const val TRK_PRINTINGS = 581
        const val SDS_PRINTINGS = 30
        const val KITS = 2000
        const val TOTAL_CARDS = 85
        const val ENTRIES = 71
    }
}
