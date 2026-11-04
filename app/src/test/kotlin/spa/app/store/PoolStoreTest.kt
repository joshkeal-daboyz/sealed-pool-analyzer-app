package spa.app.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import spa.app.importing.PoolSource
import spa.core.intake.PoolFormat

/**
 * Criterion 1 at the store: `last-pool.json` holds exactly `text`, `format`, and `source`, a new
 * pool replaces it, and what was saved reads back. The file is read here with org.json,
 * independently of the store.
 */
@RunWith(AndroidJUnit4::class)
class PoolStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val store by lazy { PoolStore(folder.root) }

    private fun json(): JSONObject = JSONObject(folder.root.resolve("last-pool.json").readText())

    private fun keys(json: JSONObject): Set<String> = json.keys().asSequence().toSet()

    @Test
    fun theFileIsLastPoolJsonInTheGivenFolder() {
        assertEquals(folder.root.resolve("last-pool.json"), store.file)
    }

    @Test
    fun aSavedFileHoldsExactlyTextFormatAndSource() {
        store.save("1 Spock\n2 Kirk\n", PoolFormat.TEXT, PoolSource.File("pool.txt"))

        val json = json()
        assertEquals(setOf("text", "format", "source"), keys(json))
        assertEquals("1 Spock\n2 Kirk\n", json.getString("text"))
        assertEquals("TEXT", json.getString("format"))
        assertEquals("pool.txt", json.getString("source"))
    }

    @Test
    fun pastedTextIsSavedWithTheSourcePasted() {
        store.save("TRK 1", PoolFormat.COLLECTOR, PoolSource.Pasted)

        val json = json()
        assertEquals("pasted", json.getString("source"))
        assertEquals("COLLECTOR", json.getString("format"))
    }

    @Test
    fun eachFormatIsSavedByItsName() {
        PoolFormat.entries.forEach { format ->
            store.save("x", format, PoolSource.Pasted)
            assertEquals(format.name, json().getString("format"))
        }
        assertEquals(
            setOf("MANABOX", "TEXT", "COLLECTOR"),
            PoolFormat.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun aNewPoolReplacesTheSavedOne() {
        store.save("old", PoolFormat.TEXT, PoolSource.File("old.txt"))
        store.save("new", PoolFormat.MANABOX, PoolSource.File("new.csv"))

        val json = json()
        assertEquals("new", json.getString("text"))
        assertEquals("MANABOX", json.getString("format"))
        assertEquals("new.csv", json.getString("source"))
    }

    @Test
    fun savingLeavesOnlyThePoolFileBehind() {
        store.save("old", PoolFormat.TEXT, PoolSource.Pasted)
        store.save("new", PoolFormat.TEXT, PoolSource.Pasted)

        assertEquals(listOf("last-pool.json"), folder.root.list()!!.toList())
    }

    @Test
    fun whatWasSavedReadsBack() {
        val text = "Name,Set code\n\"Spock, Commander\",TRK\n"
        store.save(text, PoolFormat.MANABOX, PoolSource.File("pool.csv"))
        assertEquals(
            SavedPool.Pool(text, PoolFormat.MANABOX, PoolSource.File("pool.csv")),
            store.read(),
        )

        store.save("TRK 1", PoolFormat.COLLECTOR, PoolSource.Pasted)
        assertEquals(SavedPool.Pool("TRK 1", PoolFormat.COLLECTOR, PoolSource.Pasted), store.read())
    }

    @Test
    fun withNoFileThereIsNoSavedPool() {
        assertEquals(SavedPool.None, store.read())
    }

    @Test
    fun aFileThatIsNotJsonIsUnreadableWithNoSource() {
        store.file.writeText("{ not json")

        assertEquals(SavedPool.Unreadable(null), store.read())
    }

    @Test
    fun aFileMissingAFieldIsUnreadable() {
        store.file.writeText("""{"text":"TRK 1","source":"pool.txt"}""")

        assertEquals(SavedPool.Unreadable(PoolSource.File("pool.txt")), store.read())
    }

    @Test
    fun aFileWithAnUnknownFormatIsUnreadableWithItsSource() {
        store.file.writeText("""{"text":"TRK 1","format":"DECKBOX","source":"pasted"}""")

        assertEquals(SavedPool.Unreadable(PoolSource.Pasted), store.read())
    }

    @Test
    fun clearDeletesThePoolFile() {
        store.save("x", PoolFormat.TEXT, PoolSource.Pasted)

        store.clear()

        assertFalse(store.file.exists())
        assertEquals(SavedPool.None, store.read())
    }

    @Test
    fun clearWithNoFileDoesNothing() {
        store.clear()

        assertFalse(store.file.exists())
    }

    @Test
    fun readingLeavesTheFileByteForByte() {
        store.save("TRK 1", PoolFormat.COLLECTOR, PoolSource.Pasted)
        val before = store.file.readBytes()

        store.read()

        assertArrayEquals(before, store.file.readBytes())
    }
}
