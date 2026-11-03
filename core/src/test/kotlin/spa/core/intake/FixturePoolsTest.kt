package spa.core.intake

import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import spa.core.cards.CardIndex
import spa.core.cards.fixturesDir
import spa.core.sim.ExpectedUnresolved
import spa.core.sim.FixtureEntry
import spa.core.sim.FixtureManifest
import spa.core.sim.poolFixturesDir

/** Every committed pool in `fixtures/pools/` parses exactly as its manifest entry says. */
class FixturePoolsTest {
    private val manifest = FixtureManifest.load(poolFixturesDir)
    private val indexes = mutableMapOf<String, CardIndex>()

    private fun index(set: String) = indexes.getOrPut(set) { CardIndex.load(fixturesDir, set) }

    private fun parse(entry: FixtureEntry): ParseResult =
        PoolParser.parse(
            poolFixturesDir.resolve(entry.file).readText(),
            index(entry.set),
            entry.poolFormat,
        )

    private fun entry(file: String) = manifest.fixtures.single { it.file == file }

    @Test
    fun `every listed fixture parses to its expected count and exact unresolved lines`() {
        assertTrue(manifest.fixtures.isNotEmpty())
        manifest.fixtures.forEach { entry ->
            val text = poolFixturesDir.resolve(entry.file).readText()
            val result = parse(entry)
            assertEquals(entry.expectedCards, result.pool.totalCount, entry.file)
            assertEquals(
                entry.expectedUnresolved,
                result.unresolved.map { ExpectedUnresolved(it.lineNumber, it.reason.code) },
                entry.file,
            )
            val header = if (entry.poolFormat == PoolFormat.MANABOX) listOf(1) else emptyList()
            assertEveryLineAccounted(text, result, header)
        }
    }

    @Test
    fun `every file in the directory is in the manifest and every entry has a file`() {
        val files =
            poolFixturesDir
                .listDirectoryEntries()
                .map { it.name }
                .filterNot { it == FixtureManifest.FILE_NAME || it == "README.md" }
                .sorted()
        assertEquals(files, manifest.fixtures.map { it.file }.sorted())
        assertTrue(Files.exists(poolFixturesDir.resolve("README.md")))
    }

    @Test
    fun `the fixture set has every series the goal asks for`() {
        assertTrue(manifest.fixtures.size >= 24)
        fun files(prefix: String, count: Int, extension: String) =
            (1..count).map { "$prefix-%02d.$extension".format(it) }
        val expected =
            files("trk-manabox", 10, "csv") +
                files("trk-text", 5, "txt") +
                files("trk-collector", 5, "txt") +
                files("syn-text", 2, "txt") +
                files("syn-collector", 1, "txt")
        expected.forEach { file ->
            val entry = entry(file)
            assertTrue(entry.seed != null, file)
            assertEquals(emptyList(), entry.expectedUnresolved, file)
            assertEquals(file.substringBefore('-'), entry.set, file)
            assertEquals(file.split('-')[1], entry.format, file)
        }
        assertTrue(manifest.fixtures.count { it.poolFormat == PoolFormat.MANABOX } >= 5)
    }

    @Test
    fun `the hand-edited copies hold the planned faults`() {
        val misspelled = entry("trk-text-misspelled-01.txt")
        val wrongSet = entry("trk-manabox-wrong-set-01.csv")
        val badNumber = entry("trk-collector-bad-number-01.txt")
        listOf(misspelled, wrongSet, badNumber).forEach { assertEquals(null, it.seed, it.file) }
        assertEquals(listOf("unknown-name", "unknown-name", "unknown-name"), reasons(misspelled))
        assertEquals(listOf("wrong-set", "wrong-set"), reasons(wrongSet))
        assertEquals(
            listOf("unknown-collector-number", "unknown-collector-number"),
            reasons(badNumber),
        )
        val wrongRows = parse(wrongSet).unresolved.map { it.rawLine }
        assertTrue(wrongRows.all { it.contains(",TRC,") }, "$wrongRows")
        val trk = index("trk")
        val ids = wrongRows.map { CsvReader.records(it).single().fields[8] }
        assertTrue(ids.all { trk.byScryfallId(it) == null }, "$ids")
    }

    @Test
    fun `some trk fixtures hold sds cards from the bonus sheet`() {
        val withSds =
            manifest.fixtures
                .filter { it.set == "trk" && it.seed != null }
                .filter { entry -> parse(entry).pool.entries.any { it.printing.setCode == "sds" } }
                .map { it.file }
        assertTrue(withSds.isNotEmpty())
    }

    @Test
    fun `the syn text lists hold full and front-face-only double-faced names`() {
        val syn = index("syn")
        val doubleFaced = syn.cards.filter { it.layout == "transform" || it.layout == "modal_dfc" }
        listOf("syn-text-01.txt", "syn-text-02.txt").forEach { file ->
            val names =
                poolFixturesDir
                    .resolve(file)
                    .readText()
                    .lines()
                    .filter { it.isNotBlank() }
                    .map { it.substringAfter(' ') }
            assertTrue(names.any { name -> doubleFaced.any { it.name == name } }, file)
            assertTrue(
                names.any { name -> doubleFaced.any { it.faces.first().name == name } },
                file,
            )
        }
    }

    private fun reasons(entry: FixtureEntry) = entry.expectedUnresolved.map { it.reason }
}
