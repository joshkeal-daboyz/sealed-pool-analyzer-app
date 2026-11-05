package spa.agent.facts

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.facts.FactTestCards.card
import spa.agent.facts.FactTestCards.face
import spa.agent.synergy.AxisTerm
import spa.agent.synergy.PartnerTerm
import spa.agent.tags.TagFile
import spa.core.cards.CardIndex
import spa.core.cards.Printing
import spa.core.cards.Rarity

/** A pool's facts by packet id, keywords from the Scryfall cache, and their counts. */
class CardFactsTest {
    @TempDir lateinit var temp: Path

    private fun printing(number: String, name: String, type: String, mv: Double = 4.0) =
        Printing(
            "id-$number",
            "xyz",
            number,
            Rarity.COMMON,
            emptyList(),
            card(face(name, type), manaValue = mv).copy(oracleId = "o$number"),
        )

    private val index =
        CardIndex(
            listOf("xyz"),
            listOf(
                printing("2", "Bird", "Creature — Bird", 1.0),
                printing("1", "Wall", "Artifact Creature — Wall"),
                printing("3", "Field", "Land", 0.0),
            ),
        )

    @Test
    fun `the pool's facts by id, in collector order, with keywords by oracle id`() {
        val facts = CardFacts.pool(index, mapOf("o2" to listOf("Flying")))

        assertEquals(listOf("xyz/1", "xyz/2", "xyz/3"), facts.keys.toList())
        assertEquals(setOf("artifact", "creature", "type:wall"), facts["xyz/1"])
        assertEquals(setOf("cheap", "creature", "kw:flying", "type:bird"), facts["xyz/2"])
        assertEquals(setOf("land"), facts["xyz/3"])
    }

    @Test
    fun `load reads the keywords from the cache`() {
        val dir = Files.createDirectories(temp.resolve("xyz"))
        Files.writeString(dir.resolve("set.json"), """{"code":"xyz"}""")
        Files.writeString(
            dir.resolve("cards.json"),
            """[{"oracle_id":"o1","keywords":["Defender"]}]""",
        )
        Files.writeString(dir.resolve("fetched-at.txt"), "2026-11-05T00:00:00Z")

        val facts = CardFacts.load(temp, index)

        assertEquals(setOf("artifact", "creature", "kw:defender", "type:wall"), facts["xyz/1"])
    }

    @Test
    fun `counts are most common first, then by name`() {
        val counts = CardFacts.counts(CardFacts.pool(index, emptyMap()))

        assertEquals(
            listOf(
                FactCount("creature", 2),
                FactCount("artifact", 1),
                FactCount("cheap", 1),
                FactCount("land", 1),
                FactCount("type:bird", 1),
                FactCount("type:wall", 1),
            ),
            counts,
        )
    }

    @Test
    fun `a term keeps itself for a table by default`() {
        val tags = TagFile("xyz", "p", emptyList(), emptyList())

        assertSame(AxisTerm, AxisTerm.forTable(tags, emptyMap()))
        assertSame(PartnerTerm, PartnerTerm.forTable(tags, emptyMap()))
    }
}
