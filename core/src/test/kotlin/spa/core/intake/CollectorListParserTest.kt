package spa.core.intake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `the grammar accepts collector lines and rejects names`() {
        listOf("123", "123 x2", "2 123", "2x 123", "SET 123", "SET 123 x2", "123a", "7s", "2xm 12")
            .forEach { assertTrue(CollectorListParser.matches(it), it) }
        listOf("Grizzly Bears", "2 Island", "Gravedigger x2", "Fire // Ice").forEach {
            assertFalse(CollectorListParser.matches(it), it)
        }
    }
}
