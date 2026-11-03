package spa.core.intake

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.core.brief.BriefLoader
import spa.core.brief.synBriefPath

class PoolParserTest {
    private val manabox = intakeResource("manabox-mixed.csv")
    private val text = intakeResource("text-mixed.txt")
    private val collector = intakeResource("collector-mixed.txt")

    @Test
    fun `detects each of the three formats`() {
        assertEquals(PoolFormat.MANABOX, PoolParser.detect(manabox))
        assertEquals(PoolFormat.TEXT, PoolParser.detect(text))
        assertEquals(PoolFormat.COLLECTOR, PoolParser.detect(collector))
    }

    @Test
    fun `79 percent collector lines is text and 80 percent is collector`() {
        val seventyNine = intakeResource("detect-79-percent.txt")
        val lines = PoolLines.split(seventyNine)
        assertEquals(24, lines.size)
        assertEquals(19, lines.count { CollectorListParser.matches(it) })

        assertEquals(PoolFormat.TEXT, PoolParser.detect(seventyNine))
        assertEquals(PoolFormat.COLLECTOR, PoolParser.detect("1\n2\n3\n4\nGrizzly Bears\n"))
    }

    @Test
    fun `comments and blank lines do not count toward detection`() {
        val withComments = "# a\n// b\n\n1\n2\n3\n4\nGrizzly Bears\n# c\n// d\n"

        assertEquals(PoolFormat.COLLECTOR, PoolParser.detect(withComments))
        assertEquals(PoolFormat.TEXT, PoolParser.detect(""))
        assertEquals(PoolFormat.TEXT, PoolParser.detect("# only a comment\n"))
    }

    @Test
    fun `ManaBox is detected from the first non-blank line only`() {
        assertEquals(PoolFormat.MANABOX, PoolParser.detect("\n  \nName,Scryfall ID,Quantity\n"))
        assertEquals(
            PoolFormat.MANABOX,
            PoolParser.detect(PoolLines.BOM + "Scryfall ID,Quantity\n"),
        )
        assertEquals(PoolFormat.TEXT, PoolParser.detect("Grizzly Bears\nScryfall ID\n"))
    }

    @Test
    fun `parse uses the detected format and matches the format's own parser`() {
        assertEquals(ManaBoxCsvParser.parse(manabox, synIndex), PoolParser.parse(manabox, synIndex))
        assertEquals(TextListParser.parse(text, synIndex), PoolParser.parse(text, synIndex))
        assertEquals(
            CollectorListParser.parse(collector, synIndex),
            PoolParser.parse(collector, synIndex),
        )
    }

    @Test
    fun `parse uses a given format over the detected one`() {
        val result = PoolParser.parse("1\n2\n", synIndex, PoolFormat.TEXT)

        assertEquals(PoolFormat.TEXT, result.format)
        assertEquals(listOf(1 to "unknown-name", 2 to "unknown-name"), result.reasons())
    }

    @Test
    fun `parse with a kit template fills in the kit warnings`() {
        val kit = BriefLoader.load(synBriefPath).kitTemplate

        val result = PoolParser.parse(text, synIndex, kitTemplate = kit)

        assertEquals(KitValidator.validate(result.pool, kit), result.warnings)
        assertEquals(listOf("card-count", "rare-count"), result.warnings.map { it.code.code })
    }

    @Test
    fun `duplicate lines merge into one entry per printing with summed counts`() {
        val result = PoolParser.parse("2 Doom Blade\nDoom Blade x3\n5\n", synIndex, PoolFormat.TEXT)
        val collectorResult = PoolParser.parse("5\n2 5\nSYN 5 x3\n", synIndex)

        assertEquals(listOf("syn 5" to 5), result.counts())
        assertEquals(listOf(1, 2), result.resolvedLines)
        assertEquals(listOf("syn 5" to 6), collectorResult.counts())
        assertEquals(mapOf(syn("5").card.oracleId to 6), collectorResult.pool.cardCounts())
        assertEquals(6, collectorResult.pool.totalCount)
    }

    @Test
    fun `cardCounts sums printings of one card`() {
        val result = PoolParser.parse("syn 7\n2 syn 7s\n3 syn 103\n", synWithCompanion)

        assertEquals(3, result.pool.entries.size)
        assertEquals(mapOf(syn("7").card.oracleId to 6), result.pool.cardCounts())
    }

    @Test
    fun `collector order is numeric first, then the whole string`() {
        assertEquals(
            listOf("1", "7", "7s", "30", "103", "a"),
            listOf("103", "a", "7s", "30", "1", "7").sortedWith(PoolLines.collectorOrder),
        )
    }
}
