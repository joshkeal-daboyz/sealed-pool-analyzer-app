package spa.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.data.SetData

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

    private companion object {
        const val TRK_PRINTINGS = 581
        const val SDS_PRINTINGS = 30
        const val KITS = 2000
    }
}
