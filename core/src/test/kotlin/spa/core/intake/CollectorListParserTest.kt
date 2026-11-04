package spa.core.intake

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import spa.core.sim.ExpectedUnresolved
import spa.core.sim.FixtureManifest
import spa.core.sim.poolFixturesDir
import spa.core.sim.trkIndex

class CollectorListParserTest {
    private val file = intakeResource("collector-mixed.txt")

    @Test
    fun `every syntax, a letter suffix, and a companion line resolve`() {
        val result = CollectorListParser.parse(file, synWithCompanion)

        assertEquals(PoolFormat.COLLECTOR, result.format)
        assertEquals(
            listOf(
                "syn 1" to 2, // 1, and 1 again on line 13
                "syn 3" to 2, // 3 x2
                "syn 4" to 2, // 2 4
                "syn 5" to 2, // 2x 5
                "syn 6" to 1, // SYN 6
                "syn 8" to 3, // syn 8 x3
                "syn 7s" to 1, // letter suffix
                "sds 11" to 1, // companion set
            ),
            result.counts(),
        )
        assertEquals("Sheoldred, the Apocalypse", result.pool.entries.last().printing.card.name)
    }

    @Test
    fun `a set outside the index and a missing number are unresolved`() {
        val result = CollectorListParser.parse(file, synWithCompanion)

        assertEquals(listOf(10 to "wrong-set", 11 to "unknown-collector-number"), result.reasons())
        assertEquals(2, result.skippedLines)
        assertEveryLineAccounted(file, result)
    }

    @Test
    fun `against syn alone the companion line is wrong-set and the variant number is missing`() {
        val result = CollectorListParser.parse(file, synIndex)

        assertEquals(
            listOf(
                8 to "unknown-collector-number",
                9 to "wrong-set",
                10 to "wrong-set",
                11 to "unknown-collector-number",
            ),
            result.reasons(),
        )
        assertEveryLineAccounted(file, result)
    }

    @Test
    fun `counts on both sides, a zero count, and a non-collector line are unresolved`() {
        val result = CollectorListParser.parse("2 5 x3\n0 5\nGrizzly Bears\n7S\n", synWithCompanion)

        assertEquals(
            listOf(1 to "bad-quantity", 2 to "bad-quantity", 3 to "unparsed"),
            result.reasons(),
        )
        assertEquals(listOf("syn 7s" to 1), result.counts())
    }

    private fun assertPicardLine(line: String) {
        val result = CollectorListParser.parse("$line\n", trkIndex)

        assertEquals(1, result.pool.entries.size, line)
        assertEquals(1, result.pool.totalCount, line)
        assertEquals(listOf("trk 1701D" to 1), result.counts(), line)
        assertEquals(emptyList(), result.unresolved, line)
    }

    @Test fun `the line 1701d resolves to trk 1701D`() = assertPicardLine("1701d")

    @Test fun `the line 1701D resolves to trk 1701D`() = assertPicardLine("1701D")

    @Test fun `the line TRK 1701d resolves to trk 1701D`() = assertPicardLine("TRK 1701d")

    @Test fun `the line trk 1701d resolves to trk 1701D`() = assertPicardLine("trk 1701d")

    @Test
    fun `trk-collector-bad-number-01 still parses as its golden and manifest say`() {
        val file = "trk-collector-bad-number-01.txt"
        val entry = FixtureManifest.load(poolFixturesDir).fixtures.single { it.file == file }
        val golden =
            Json.parseToJsonElement(
                    poolFixturesDir.resolveSibling("golden").resolve("$file.json").readText()
                )
                .jsonObject
        val goldenPool = golden.getValue("pool").jsonObject
        val goldenUnresolved =
            goldenPool.getValue("unresolved").jsonArray.map {
                val line = it.jsonObject
                ExpectedUnresolved(
                    line.getValue("line").jsonPrimitive.int,
                    line.getValue("reason").jsonPrimitive.content,
                )
            }

        val result = CollectorListParser.parse(poolFixturesDir.resolve(file).readText(), trkIndex)

        assertEquals(goldenPool.getValue("entries").jsonPrimitive.int, result.pool.entries.size)
        assertEquals(entry.expectedCards, result.pool.totalCount)
        assertEquals(
            golden.getValue("metrics").jsonObject.getValue("totalCards").jsonPrimitive.int,
            result.pool.totalCount,
        )
        assertEquals(entry.expectedUnresolved, goldenUnresolved)
        assertEquals(
            entry.expectedUnresolved,
            result.unresolved.map { ExpectedUnresolved(it.lineNumber, it.reason.code) },
        )
    }

    @Test
    fun `the grammar accepts collector lines and rejects names`() {
        listOf("123", "123 x2", "2 123", "2x 123", "SET 123", "SET 123 x2", "123a", "7s", "2xm 12")
            .forEach { assertTrue(CollectorListParser.matches(it), it) }
        listOf("Grizzly Bears", "2 Island", "Gravedigger x2", "Fire // Ice").forEach {
            assertFalse(CollectorListParser.matches(it), it)
        }
    }
}
