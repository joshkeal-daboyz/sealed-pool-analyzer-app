package spa.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.FileNotFoundException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun loadsTheBundledAssetsThroughCore() = runTest {
        val set = SetData.forContext(context).load()

        assertEquals(TRK_PRINTINGS, set.index.printings("trk").size)
        assertEquals(SDS_PRINTINGS, set.index.printings("sds").size)
        val baseline = JSONObject(TestFixtures.text("baselines/trk.json"))
        assertEquals(baseline.getString("setCode"), set.baseline.setCode)
        assertEquals(baseline.getInt("kits"), set.baseline.kits)
        assertEquals(baseline.getLong("seed"), set.baseline.seed)
        val brief = JSONObject(TestFixtures.text("briefs/trk.json"))
        assertEquals(brief.getInt("version"), set.brief.version)
    }

    @Test
    fun copiesEveryBundledFileUnderFilesDir() = runTest {
        SetData.forContext(context).load()

        val root = context.filesDir.resolve(SetData.ASSET_DIR)
        SetData.FILES.forEach { path ->
            assertTrue(
                "$path differs from fixtures",
                root
                    .resolve(path)
                    .readBytes()
                    .contentEquals(TestFixtures.root.resolve(path).readBytes()),
            )
        }
    }

    @Test
    fun aSecondLoadReturnsTheCachedDataWithoutReadingAgain() = runTest {
        val data = SetData.forContext(context)

        val first = data.load()
        val second = data.load()

        assertSame(first, second)
        assertEquals(1, data.reads)
        assertEquals(1, data.copies)
    }

    @Test
    fun aNewSetDataOverTheSameFilesDirDoesNotCopyAgain() = runTest {
        SetData.forContext(context).load()

        val restarted = SetData.forContext(context)
        restarted.load()

        assertEquals(0, restarted.copies)
        assertEquals(1, restarted.reads)
    }

    @Test
    fun aNewStampCopiesAgain() = runTest {
        val dir = context.filesDir
        TestFixtures.setData(dir, stamp = "install-1").load()

        val updated = TestFixtures.setData(dir, stamp = "install-2")
        updated.load()

        assertEquals(1, updated.copies)
    }

    @Test
    fun loadsOffTheCallingThread() = runTest {
        val threads = mutableListOf<String>()
        val data =
            SetData(
                assets = { path ->
                    synchronized(threads) { threads += Thread.currentThread().name }
                    TestFixtures.root.resolve(path).inputStream()
                },
                root = context.filesDir.resolve("threaded"),
                stamp = "test",
            )
        val caller = Thread.currentThread().name

        data.load()

        assertTrue(threads.isNotEmpty())
        assertFalse(threads.any { it == caller })
        assertTrue(threads.all { it.startsWith("DefaultDispatcher-worker") })
    }

    @Test
    fun anEmptyAssetSourceFailsWithSetDataException() = runTest {
        val data =
            SetData(
                assets = { path -> throw FileNotFoundException(path) },
                root = context.filesDir.resolve("empty"),
                stamp = "test",
            )

        val error = runCatching { data.load() }.exceptionOrNull()

        assertTrue(error is SetDataException)
        assertEquals(0, data.copies)
    }

    @Test
    fun aCorruptBundledFileFailsWithSetDataException() = runTest {
        val data =
            SetData(
                assets = { path ->
                    if (path.startsWith("baselines/")) "not json".byteInputStream()
                    else TestFixtures.root.resolve(path).inputStream()
                },
                root = context.filesDir.resolve("corrupt"),
                stamp = "test",
            )

        assertThrows(SetDataException::class.java) {
            kotlinx.coroutines.runBlocking { data.load() }
        }
    }

    private companion object {
        // The ticket's acceptance criterion 3 names these counts.
        const val TRK_PRINTINGS = 581
        const val SDS_PRINTINGS = 30
    }
}
