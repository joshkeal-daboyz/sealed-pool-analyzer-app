package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.build.Deck
import spa.core.cards.CardIndex
import spa.core.cards.Printing
import spa.core.intake.PoolFormat
import spa.core.intake.PoolParser

/** SPA-072 criteria 2 to 5: the rows, the `.csv` and the `.txt` of an exported deck. */
class DeckExportTest {
    @TempDir lateinit var temp: Path

    private val data = DataDir(Path.of(Fixtures.data))
    private val session =
        BuildSession.open(data, "trk", Path.of(Fixtures.data, "weights", "deck-tuned.json"))
    private val index = session.index
    private val pool =
        PoolParser.parse(Files.readString(Path.of(Fixtures.pool("trk-manabox-01.csv"))), index).pool
    private val decks = session.rank(pool).take(3).map { it.scored.deck }
    private val header =
        "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID," +
            "Purchase price,Misprint,Altered,Condition,Language,Purchase date," +
            "Purchase price currency"
    private val basicNames =
        mapOf('W' to "Plains", 'U' to "Island", 'B' to "Swamp", 'R' to "Mountain", 'G' to "Forest")

    private fun csvLines(deck: Deck) =
        DeckExport.csv(DeckExport.rows(index, deck), "trk", "Star Trek").lines()

    private fun csvRows(deck: Deck) = csvLines(deck).drop(1).dropLast(1).map(::fields)

    /** The fields of one CSV line, with quotes read as the owner's export writes them. */
    private fun fields(line: String): List<String> {
        val out = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> field.append('"').also { i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> out.add(field.toString()).also { field.clear() }
                else -> field.append(c)
            }
            i++
        }
        return out + field.toString()
    }

    @Test
    fun `the csv has the header, 40 cards and a Scryfall id that agrees with the cache`() {
        assertEquals(3, decks.size)
        decks.forEach { deck ->
            val lines = csvLines(deck)
            assertEquals(header, lines.first())
            assertEquals("", lines.last(), "ends with a line feed")
            val rows = csvRows(deck)
            assertTrue(rows.all { it.size == 16 }, "16 columns")
            assertEquals(40, rows.sumOf { it[6].toInt() })
            rows.forEach {
                val printing = requireNotNull(index.byScryfallId(it[8])) { "id ${it[8]}" }
                assertEquals(printing.setCode.uppercase(), it[1])
                assertEquals(printing.collectorNumber, it[3])
                assertEquals(printing.card.name, it[0])
                assertEquals(printing.rarity.scryfallName, it[5])
                assertEquals("normal", it[4])
                assertEquals(listOf("", "", "false", "false"), listOf(it[7], it[9], it[10], it[11]))
                assertEquals(listOf("near_mint", "en", "", ""), it.drop(12))
                assertEquals(if (it[1] == "TRK") "Star Trek" else "", it[2])
            }
        }
    }

    @Test
    fun `the basics are the set's own basic lands, last, in WUBRG order`() {
        val order = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
        decks.forEach { deck ->
            val rows = csvRows(deck)
            val basics = rows.filter { it[0] in order }
            val played = basicNames.filterKeys { c ->
                deck.basics.any { it.key.symbol == c && it.value > 0 }
            }
            assertEquals(played.values.toList(), basics.map { it[0] })
            assertEquals(rows.takeLast(basics.size), basics, "basics come last")
            basics.forEach {
                assertEquals("TRK", it[1])
                assertEquals((307 + order.indexOf(it[0])).toString(), it[3])
            }
            val rest = rows.dropLast(basics.size).map { it[0] }
            assertEquals(rest.sorted(), rest, "spells and lands by name")
        }
    }

    @Test
    fun `parsing the csv again gives the deck's copies per card`() {
        decks.forEach { deck ->
            val text = DeckExport.csv(DeckExport.rows(index, deck), "trk", "Star Trek")
            val parsed = PoolParser.parse(text, index, PoolFormat.MANABOX)
            assertEquals(emptyList(), parsed.unresolved)
            assertEquals(40, parsed.pool.totalCount)
            val expected = mutableMapOf<String, Int>()
            (deck.spells + deck.nonbasics).forEach {
                expected.merge(it.card.oracleId, 1, Int::plus)
            }
            deck.basics
                .filterValues { it > 0 }
                .forEach { (colour, n) ->
                    val basic = requireNotNull(index.byName(basicNames.getValue(colour.symbol)))
                    expected.merge(basic.oracleId, n, Int::plus)
                }
            assertEquals(expected, parsed.pool.cardCounts())
        }
    }

    @Test
    fun `the txt has one line per csv row in the same order`() {
        decks.forEach { deck ->
            val lines = DeckExport.txt(DeckExport.rows(index, deck)).lines()
            assertEquals("", lines.last())
            val body = lines.dropLast(1)
            val csv = csvRows(deck)
            assertEquals(csv.size, body.size)
            val shape = Regex("""^\d+ .+ \([A-Z0-9]+\) \S+$""")
            body.zip(csv).forEach { (line, row) ->
                assertTrue(shape.matches(line), line)
                assertEquals("${row[6]} ${row[0]} (${row[1]}) ${row[3]}", line)
            }
            assertEquals(40, body.sumOf { it.substringBefore(' ').toInt() })
        }
    }

    private fun renamed(name: String): Printing {
        val printing = requireNotNull(index.byCollectorNumber("trk", "95"))
        return printing.copy(card = printing.card.copy(name = name))
    }

    @Test
    fun `a name with a comma or a quote is quoted and a multi face name is kept`() {
        val rows =
            listOf(
                DeckExport.Row(renamed("He's Dead, Jim"), 2),
                DeckExport.Row(renamed("Say \"Hi\", then go"), 1),
                DeckExport.Row(renamed("Front // Back"), 1),
            )

        val lines = DeckExport.csv(rows, "trk", "Star Trek").lines()

        assertTrue(lines[1].startsWith("\"He's Dead, Jim\",TRK,Star Trek,95,normal,common,2,,"))
        assertTrue(lines[2].startsWith("\"Say \"\"Hi\"\", then go\",TRK,"))
        assertTrue(lines[3].startsWith("Front // Back,TRK,"))
        assertEquals("2 He's Dead, Jim (TRK) 95", DeckExport.txt(rows).lines().first())
    }

    @Test
    fun `a card of another set has an empty set name`() {
        val other = requireNotNull(index.byCollectorNumber("trk", "95")).copy(setCode = "sds")

        val line = DeckExport.csv(listOf(DeckExport.Row(other, 1)), "trk", "Star Trek").lines()[1]

        assertTrue(line.startsWith("Battle-Scarred Survivalist,SDS,,95,normal,"), line)
    }

    @Test
    fun `a set with no basic land of its own falls back to its companion sets`() {
        val basics = index.printings.filter { it.card.isBasicLand && it.setCode == "trk" }
        val moved = index.printings.map { if (it in basics) it.copy(setCode = "sds") else it }
        val companion = CardIndex(listOf("trk", "sds"), moved)

        val rows = DeckExport.rows(companion, decks.first())

        val basicRows = rows.filter { it.printing.card.isBasicLand }
        assertTrue(basicRows.isNotEmpty())
        assertTrue(basicRows.all { it.printing.setCode == "sds" })
    }

    @Test
    fun `a set and companions with no such basic land cannot be exported`() {
        val stripped = CardIndex(index.setCodes, index.printings.filterNot { it.card.isBasicLand })

        val failure = runCatching { DeckExport.rows(stripped, decks.first()) }.exceptionOrNull()

        assertTrue(failure is CliFailure, failure.toString())
        assertEquals(1, (failure as CliFailure).exitCode)
    }
}
