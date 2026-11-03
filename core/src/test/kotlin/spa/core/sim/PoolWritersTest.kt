package spa.core.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.intake.CsvReader
import spa.core.intake.PoolFormat
import spa.core.intake.PoolParser
import spa.core.intake.syn
import spa.core.intake.synIndex
import spa.core.intake.synWithCompanion

class PoolWritersTest {
    private val sampler = KitSampler(synIndex, synKit)
    private val kits = (1L..20L).map { sampler.sampleKit(it) }
    private val synNames = mapOf("syn" to "Synthetic Test Set")

    private fun assertRoundTrip(pool: Pool, text: String, format: PoolFormat) {
        val result = PoolParser.parse(text, synIndex, format)
        assertEquals(emptyList(), result.unresolved, text)
        assertEquals(pool.cardCounts(), result.pool.cardCounts())
        assertEquals(pool.totalCount, result.pool.totalCount)
    }

    @Test
    fun `a ManaBox export parses back to the same cards`() {
        kits.forEach { kit ->
            val csv = PoolWriters.manaBox(kit.pool, synNames, kit.foilCounts())
            assertRoundTrip(kit.pool, csv, PoolFormat.MANABOX)
            assertEquals(PoolFormat.MANABOX, PoolParser.detect(csv))
        }
    }

    @Test
    fun `a ManaBox export has the full header and fills every column`() {
        val kit = kits.first()
        val records = CsvReader.records(PoolWriters.manaBox(kit.pool, synNames, kit.foilCounts()))
        assertEquals(
            "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID," +
                "Purchase price,Misprint,Altered,Condition,Language,Purchase date," +
                "Purchase price currency",
            records.first().raw,
        )
        val header = records.first().fields
        records.drop(1).forEach { record ->
            assertEquals(header.size, record.fields.size, record.raw)
            assertTrue(record.fields.all { it.isNotEmpty() }, record.raw)
            val row = header.zip(record.fields).toMap()
            assertEquals("SYN", row["Set code"])
            assertEquals("Synthetic Test Set", row["Set name"])
            assertTrue(row.getValue("Foil") in setOf("normal", "foil"))
            assertTrue(row.getValue("ManaBox ID").toInt() in 100_000..999_999)
            assertTrue(Regex("""\d+\.\d\d""").matches(row.getValue("Purchase price")))
            assertEquals("near_mint", row["Condition"])
            assertEquals("en", row["Language"])
        }
    }

    @Test
    fun `foil copies get their own row and the quantities still add up`() {
        val bears = syn("1")
        val pool = Pool(listOf(PoolEntry(bears, 3)))
        val rows =
            CsvReader.records(PoolWriters.manaBox(pool, foilCounts = mapOf(bears.scryfallId to 1)))
                .drop(1)
                .map { it.fields }
        assertEquals(listOf("normal" to "2", "foil" to "1"), rows.map { it[4] to it[6] })
        assertTrue(rows[0][7] != rows[1][7], "a foil has its own ManaBox ID")
        // With no set names given, the set name falls back to the code.
        assertEquals("SYN", rows[0][2])
        val allFoil =
            CsvReader.records(PoolWriters.manaBox(pool, foilCounts = mapOf(bears.scryfallId to 3)))
        assertEquals(listOf("foil"), allFoil.drop(1).map { it.fields[4] })
    }

    @Test
    fun `a name with a comma is quoted and quotes are doubled`() {
        val agadeem = syn("13")
        val csv = PoolWriters.manaBox(Pool(listOf(PoolEntry(agadeem, 1))), synNames)
        assertTrue(csv.lines()[1].startsWith("\"Agadeem's Awakening // Agadeem, the Undercrypt\","))
        assertEquals("\"say \"\"hi\"\"\"", PoolWriters.csvField("say \"hi\""))
        assertEquals("\"two\nlines\"", PoolWriters.csvField("two\nlines"))
        assertEquals("plain", PoolWriters.csvField("plain"))
    }

    @Test
    fun `a text list parses back to the same cards with full double-faced names`() {
        kits.forEach { kit ->
            val text = PoolWriters.text(kit.pool)
            assertRoundTrip(kit.pool, text, PoolFormat.TEXT)
            assertTrue(
                text.lines().filter { it.isNotEmpty() }.all { Regex("""\d+ .+""").matches(it) }
            )
        }
        val pool = Pool(listOf(PoolEntry(syn("11"), 2), PoolEntry(syn("16"), 1)))
        assertEquals(
            "2 Delver of Secrets // Insectile Aberration\n1 Fire // Ice\n",
            PoolWriters.text(pool),
        )
    }

    @Test
    fun `a text list can write chosen cards by front face and still parse back`() {
        val delver = syn("11")
        val pool = Pool(listOf(PoolEntry(delver, 2), PoolEntry(syn("12"), 1)))
        val text = PoolWriters.text(pool, setOf(delver.card.oracleId))
        assertEquals(
            "2 Delver of Secrets\n1 Huntmaster of the Fells // Ravager of the Fells\n",
            text,
        )
        assertRoundTrip(pool, text, PoolFormat.TEXT)
    }

    @Test
    fun `a text list merges copies of one card across printings`() {
        val elves = synWithCompanion.printings.filter { it.card.name == "Llanowar Elves" }
        val pool = Pool(elves.map { PoolEntry(it, 1) })
        assertEquals("${elves.size} Llanowar Elves\n", PoolWriters.text(pool))
    }

    @Test
    fun `a collector list parses back to the same printings`() {
        kits.forEach { kit ->
            val text = PoolWriters.collector(kit.pool)
            assertRoundTrip(kit.pool, text, PoolFormat.COLLECTOR)
            assertEquals(PoolFormat.COLLECTOR, PoolParser.detect(text))
            val parsed = PoolParser.parse(text, synIndex, PoolFormat.COLLECTOR).pool
            assertEquals(kit.pool.entries.toSet(), parsed.entries.toSet())
        }
        val pool = Pool(listOf(PoolEntry(syn("1"), 3), PoolEntry(syn("21"), 1)))
        assertEquals("SYN 1 x3\nSYN 21\n", PoolWriters.collector(pool))
    }

    @Test
    fun `companion printings keep their set code in every format`() {
        val bonus = synWithCompanion.printings("sds").first()
        val pool = Pool(listOf(PoolEntry(syn("2"), 1), PoolEntry(bonus, 2)))
        val collector = PoolWriters.collector(pool)
        assertTrue(collector.lines()[1].startsWith("SDS ${bonus.collectorNumber}"))
        val parsed = PoolParser.parse(collector, synWithCompanion, PoolFormat.COLLECTOR)
        assertEquals(pool.entries, parsed.pool.entries)
        val csv = PoolWriters.manaBox(pool, mapOf("sds" to "Stardates"))
        assertTrue(csv.lines()[2].contains(",SDS,Stardates,"))
        val fromCsv = PoolParser.parse(csv, synWithCompanion, PoolFormat.MANABOX)
        assertEquals(pool.entries, fromCsv.pool.entries)
    }
}
