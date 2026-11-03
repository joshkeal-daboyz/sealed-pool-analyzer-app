package spa.core.intake

import kotlin.test.Test
import kotlin.test.assertEquals

class TextListParserTest {
    private val file = intakeResource("text-mixed.txt")

    @Test
    fun `every count syntax, full and face names, and set suffixes resolve`() {
        val result = TextListParser.parse(file, synIndex)

        assertEquals(PoolFormat.TEXT, result.format)
        assertEquals(
            listOf(
                "syn 7" to 4, // 4 Llanowar Elves
                "syn 8" to 2, // 2x Giant Growth
                "syn 5" to 3, // 3 x Doom Blade
                "syn 6" to 2, // Gravedigger x2
                "syn 4" to 1, // Counterspell
                "syn 11" to 1, // full double-faced name
                "syn 12" to 1, // front-face name with (SYN) 12
                "syn 14" to 2, // Bonecrusher Giant [SYN] and Stomp, merged
                "syn 16" to 1, // fire // ice, any case
            ),
            result.counts(),
        )
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 10, 11, 15), result.resolvedLines)
    }

    @Test
    fun `misspelled names are unresolved, never guessed, and comments are skipped`() {
        val result = TextListParser.parse(file, synIndex)

        assertEquals(listOf(13 to "unknown-name", 14 to "unknown-name"), result.reasons())
        assertEquals("Lightning Helixx x2", result.unresolved[1].rawLine)
        assertEquals(3, result.skippedLines)
        assertEveryLineAccounted(file, result)
    }

    @Test
    fun `a name picks the target set's lowest collector number, else the companion's printing`() {
        val text = "Llanowar Elves\nGrizzly Bears\n2 Sheoldred, the Apocalypse\n"

        val result = TextListParser.parse(text, synWithCompanion)

        assertEquals(listOf("syn 7" to 1, "syn 1" to 1, "sds 11" to 2), result.counts())
    }

    @Test
    fun `a companion card is unknown in an index without that companion`() {
        val result = TextListParser.parse("Sheoldred, the Apocalypse", synIndex)

        assertEquals(listOf(1 to "unknown-name"), result.reasons())
    }

    @Test
    fun `a zero count is a bad quantity and a name starting with a letter x is not a count`() {
        val result = TextListParser.parse("0 Doom Blade\n2 Island\n1 x Island x\n", synIndex)

        assertEquals(listOf("syn 22" to 2), result.counts())
        assertEquals(listOf(1 to "bad-quantity", 3 to "unknown-name"), result.reasons())
    }
}
