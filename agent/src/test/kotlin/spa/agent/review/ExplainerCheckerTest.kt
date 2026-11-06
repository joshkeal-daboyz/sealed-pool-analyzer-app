package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExplainerCheckerTest {
    private val names = setOf("Bear", "Smite", "Envoy", "Sun // Moon")
    private val ranks = listOf(1, 2, 3)
    private val agents = setOf("fake-a", "fake-b")

    private fun deck(rank: String = "1", note: String = "A note.", cards: String = "[\"Bear\"]") =
        """{"rank": $rank, "note": "$note", "cards": $cards}"""

    private fun disagreement(
        topic: String = "Blue depth",
        agents: String = "[\"fake-a\", \"fake-b\"]",
        cards: String = "[\"Envoy\"]",
        note: String = "It depends.",
    ) = """{"topic": "$topic", "agents": $agents, "cards": $cards, "note": "$note"}"""

    private fun answer(
        decks: List<String> = listOf(deck()),
        disagreements: List<String> = emptyList(),
        confidence: String = "0.7",
        verdict: String = "Register deck one.",
    ) =
        """{"confidence": $confidence, "verdict": "$verdict", """ +
            """"decks": [${decks.joinToString(", ")}], """ +
            """"disagreements": [${disagreements.joinToString(", ")}]}"""

    private fun check(text: String) = ExplainerChecker.check(text, names, ranks, agents)

    private fun assertNames(field: String, text: String) {
        val message = assertFailsWith<SchemaError> { check(text) }.message.orEmpty()
        assertTrue(message.contains(field), "'$message' should name '$field'")
    }

    private fun dropReasons(text: String) = check(text).dropped.map { it.reason }

    @Test
    fun `a valid answer is accepted with its fields`() {
        val checked =
            check(
                answer(
                    decks = listOf(deck("2", "Second.", "[\"Bear\", \"Smite\"]")),
                    disagreements = listOf(disagreement()),
                    confidence = "0.75",
                    verdict = "Register deck two.",
                )
            )

        val expected =
            ExplainerResult(
                0.75,
                "Register deck two.",
                listOf(DeckNote(2, "Second.", listOf("Bear", "Smite"))),
                listOf(
                    Disagreement(
                        "Blue depth",
                        listOf("fake-a", "fake-b"),
                        listOf("Envoy"),
                        "It depends.",
                    )
                ),
            )
        assertEquals(expected, checked.result)
        assertEquals(emptyList(), checked.dropped)
    }

    @Test
    fun `an answer in a code fence or a sentence is accepted`() {
        val body = answer()
        assertEquals(1, check("```json\n$body\n```").result.decks.size)
        assertEquals(1, check("Here it is: $body Done.").result.decks.size)
    }

    @Test
    fun `an answer with no decks and no disagreements is accepted`() {
        val checked = check(answer(decks = emptyList()))
        assertEquals(emptyList(), checked.result.decks)
        assertEquals(emptyList(), checked.result.disagreements)
    }

    @Test
    fun `text that is not JSON is a schema error`() {
        assertNames("not JSON", "no object here")
        assertNames("not valid JSON", "{ nope }")
    }

    @Test
    fun `an unknown key is a schema error naming the key`() {
        assertNames("extra", answer().removeSuffix("}") + ", \"extra\": 1}")
        assertNames(
            "decks[0].mood",
            answer(decks = listOf("""{"rank": 1, "note": "n", "cards": ["Bear"], "mood": 1}""")),
        )
        assertNames(
            "disagreements[0].mood",
            answer(
                disagreements =
                    listOf(
                        """{"topic": "t", "agents": ["fake-a", "fake-b"], "cards": ["Bear"], """ +
                            """"note": "n", "mood": 1}"""
                    )
            ),
        )
    }

    @Test
    fun `a missing field is a schema error naming the field`() {
        assertNames("verdict", """{"confidence": 0.5, "decks": [], "disagreements": []}""")
        assertNames("confidence", """{"verdict": "v", "decks": [], "disagreements": []}""")
        assertNames("decks", """{"confidence": 0.5, "verdict": "v", "disagreements": []}""")
        assertNames("disagreements", """{"confidence": 0.5, "verdict": "v", "decks": []}""")
        assertNames("decks[0].note", answer(decks = listOf("""{"rank": 1, "cards": ["Bear"]}""")))
    }

    @Test
    fun `a wrong type is a schema error naming the field`() {
        assertNames("confidence", answer(confidence = "\"high\""))
        assertNames("verdict", answer().replace("\"Register deck one.\"", "7"))
        assertNames("decks[0].rank", answer(decks = listOf(deck(rank = "\"one\""))))
        assertNames("decks[0].cards", answer(decks = listOf(deck(cards = "\"Bear\""))))
        assertNames(
            "disagreements[0].agents",
            answer(disagreements = listOf(disagreement(agents = "7"))),
        )
    }

    @Test
    fun `a confidence outside 0 to 1 is a schema error`() {
        assertNames("confidence", answer(confidence = "1.5"))
        assertNames("confidence", answer(confidence = "-0.1"))
    }

    @Test
    fun `an empty verdict is a schema error`() {
        assertNames("verdict", answer(verdict = ""))
        assertNames("verdict", answer(verdict = "   "))
    }

    @Test
    fun `a deck note with a rank that is not offered is dropped as an unknown rank`() {
        val checked = check(answer(decks = listOf(deck("4", "Fourth."), deck("1", "First."))))

        assertEquals(listOf("First."), checked.result.decks.map { it.note })
        assertEquals(
            listOf(DroppedItem(ItemKind.DECK, ExplainerDropReason.UNKNOWN_RANK, "Fourth.")),
            checked.dropped,
        )
    }

    @Test
    fun `a deck note that repeats a rank is dropped as a duplicate`() {
        val checked = check(answer(decks = listOf(deck("1", "First."), deck("1", "Again."))))

        assertEquals(listOf("First."), checked.result.decks.map { it.note })
        assertEquals(
            listOf(DroppedItem(ItemKind.DECK, ExplainerDropReason.DUPLICATE_RANK, "Again.")),
            checked.dropped,
        )
    }

    @Test
    fun `a deck note with no card or an unknown card is dropped`() {
        val checked =
            check(
                answer(
                    decks =
                        listOf(
                            deck("1", "Bare.", "[]"),
                            deck("2", "Ghostly.", "[\"Bear\", \"Ghost\"]"),
                            deck("3", "Fine."),
                        )
                )
            )

        assertEquals(listOf("Fine."), checked.result.decks.map { it.note })
        assertEquals(
            listOf(ExplainerDropReason.NO_CARD, ExplainerDropReason.UNKNOWN_CARD),
            checked.dropped.map { it.reason },
        )
    }

    @Test
    fun `a disagreement with fewer than two distinct agents is dropped`() {
        val one = disagreement("One", agents = "[\"fake-a\"]")
        val same = disagreement("Same", agents = "[\"fake-a\", \"fake-a\"]")
        val none = disagreement("None", agents = "[]")
        val checked = check(answer(disagreements = listOf(one, same, none)))

        assertEquals(emptyList(), checked.result.disagreements)
        assertEquals(
            List(3) { ExplainerDropReason.TOO_FEW_AGENTS },
            checked.dropped.map { it.reason },
        )
        assertEquals(listOf("One", "Same", "None"), checked.dropped.map { it.text })
        assertTrue(checked.dropped.all { it.kind == ItemKind.DISAGREEMENT })
    }

    @Test
    fun `a disagreement naming an agent that did not answer is dropped`() {
        val text = answer(disagreements = listOf(disagreement(agents = "[\"fake-a\", \"fake-z\"]")))

        assertEquals(listOf(ExplainerDropReason.UNKNOWN_AGENT), dropReasons(text))
    }

    @Test
    fun `a disagreement with no card or an unknown card is dropped`() {
        val text =
            answer(
                disagreements =
                    listOf(
                        disagreement("Bare", cards = "[]"),
                        disagreement("Ghostly", cards = "[\"Ghost\"]"),
                    )
            )

        assertEquals(
            listOf(ExplainerDropReason.NO_CARD, ExplainerDropReason.UNKNOWN_CARD),
            dropReasons(text),
        )
    }

    @Test
    fun `a front face name of a multi face card is kept and becomes the full name`() {
        val checked =
            check(
                answer(
                    decks = listOf(deck(cards = "[\"Sun\", \"Bear\"]")),
                    disagreements = listOf(disagreement(cards = "[\"Sun\"]")),
                )
            )

        assertEquals(listOf("Sun // Moon", "Bear"), checked.result.decks.single().cards)
        assertEquals(listOf("Sun // Moon"), checked.result.disagreements.single().cards)
        assertEquals(emptyList(), checked.dropped)
    }

    @Test
    fun `a back face name or a partial name is not a pool card`() {
        val text =
            answer(decks = listOf(deck("1", "Back.", "[\"Moon\"]"), deck("2", "Cut.", "[\"Be\"]")))

        assertEquals(
            listOf(ExplainerDropReason.UNKNOWN_CARD, ExplainerDropReason.UNKNOWN_CARD),
            dropReasons(text),
        )
    }
}
