package spa.agent.otags

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import spa.agent.facts.FactTestCards.card
import spa.agent.facts.FactTestCards.face
import spa.agent.grader.GradingPool
import spa.agent.otags.OtagBulkTest.Companion.tag
import spa.core.cards.CardIndex
import spa.core.cards.Printing
import spa.core.cards.Rarity

/** SPA-054 criteria 1 and 2: a set's otags file, built from a hand-built bulk file. */
class OtagFileTest {
    private fun printing(number: String, name: String) =
        Printing(
            "id-$number",
            "xyz",
            number,
            Rarity.COMMON,
            emptyList(),
            card(face(name)).copy(oracleId = "o$number"),
        )

    private val index =
        CardIndex(
            listOf("xyz"),
            listOf(printing("10", "Ten"), printing("2", "Two"), printing("1", "One")),
        )

    private val bulk =
        OtagBulk.parse(
            sequenceOf(
                tag("t1", "removal"),
                tag("t2", "burn", parents = listOf("t1"), cards = listOf("o1", "o10")),
                tag("t3", "art-dog", type = "illustration", cards = listOf("o2")),
                tag("t4", "pump", cards = listOf("o10")),
            )
        )

    private val file =
        OtagFile.build(
            "xyz",
            GradingPool.printings(index),
            bulk,
            "hand-built",
            "2026-11-05T22:00:00Z",
        )

    @Test
    fun `cards are the sealed pool in collector order, each with its tags and ancestors`() {
        assertEquals(
            listOf(
                OtagCard("xyz/1", listOf("burn", "removal")),
                OtagCard("xyz/2", emptyList()),
                OtagCard("xyz/10", listOf("burn", "pump", "removal")),
            ),
            file.cards,
        )
        assertEquals(
            mapOf(
                "xyz/1" to listOf("burn", "removal"),
                "xyz/2" to emptyList(),
                "xyz/10" to listOf("burn", "pump", "removal"),
            ),
            file.byId(),
        )
    }

    @Test
    fun `the file carries its set, source, date and Scryfall's attribution`() {
        assertEquals("xyz", file.set)
        assertEquals("hand-built", file.source)
        assertEquals("2026-11-05T22:00:00Z", file.updatedAt)
        assertEquals("Card tags from Scryfall Tagger (tagger.scryfall.com)", file.attribution)
    }

    @Test
    fun `it counts cards with tags, tags with parents and distinct tags`() {
        assertEquals(2, file.tagged)
        assertEquals(5, file.total)
        assertEquals(3, file.distinct)
    }

    @Test
    fun `the text is one card a line, and parses back to the same file`() {
        val text = file.encode()

        assertEquals(
            """
            |{
            |  "set": "xyz",
            |  "source": "hand-built",
            |  "updatedAt": "2026-11-05T22:00:00Z",
            |  "attribution": "Card tags from Scryfall Tagger (tagger.scryfall.com)",
            |  "cards": [
            |    { "id": "xyz/1", "otags": ["burn", "removal"] },
            |    { "id": "xyz/2", "otags": [] },
            |    { "id": "xyz/10", "otags": ["burn", "pump", "removal"] }
            |  ]
            |}
            |"""
                .trimMargin(),
            text,
        )
        assertEquals(file, OtagFile.parse(text))
    }

    @Test
    fun `text that is not an otags file is an error`() {
        assertFailsWith<IllegalArgumentException> { OtagFile.parse("{\"set\": \"xyz\"}") }
        assertFailsWith<IllegalArgumentException> { OtagFile.parse("not json") }
    }
}
