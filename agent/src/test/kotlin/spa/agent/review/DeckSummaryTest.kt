package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.build.BuildConfig
import spa.agent.build.BuildTestCards
import spa.agent.build.Candidate
import spa.agent.build.DeckCard
import spa.agent.build.DeckPartScore
import spa.agent.build.RankedDeck
import spa.agent.build.ScoredDeck
import spa.core.text.Color

/** SPA-069 criteria 1 and 2: a built deck as the review shows it. */
class DeckSummaryTest {
    private val bear = BuildTestCards.spell("Bear", BuildTestCards.W, "{1}{W}", 2)
    private val axe = BuildTestCards.spell("Axe", BuildTestCards.U, "{1}{U}", 2)
    private val envoy = BuildTestCards.spell("Envoy", BuildTestCards.U, "{1}{U}", 2)
    private val dual = BuildTestCards.land("Dual Land", setOf(Color.W, Color.U))

    private fun ranked(
        spells: List<DeckCard>,
        parts: List<DeckPartScore>,
        sameAs: List<String> = emptyList(),
    ): RankedDeck {
        val copies = spells.groupingBy { it }.eachCount()
        val pool = copies.map { (card, n) -> Candidate(card, n) } + Candidate(dual, 1)
        val setup = BuildTestCards.setup(pool, "WU")
        val deck = BuildTestCards.deck(setup, spells, 17)
        return RankedDeck(
            BuildConfig.parse("WU"),
            setup,
            ScoredDeck(deck, parts),
            sameAs.map(BuildConfig::parse),
        )
    }

    private val threeParts =
        listOf(
            DeckPartScore("grades", 10.0, "strong"),
            DeckPartScore("curve", -1.5, "light"),
            DeckPartScore("fixing", 0.3, "ok"),
            DeckPartScore("removal", 0.0, null),
        )
    private val mixed = listOf(envoy, bear, axe, bear)

    @Test
    fun `a deck is summed up with its colours, score, spells, lands and the two biggest parts`() {
        val summary = DeckSummary.of(2, ranked(mixed, threeParts, listOf("WB", "UB")))

        assertEquals(2, summary.rank)
        assertEquals("WU", summary.colours)
        assertEquals(listOf("WB", "UB"), summary.sameAs)
        assertEquals(8.8, summary.score, 1e-9)
        assertEquals(
            listOf(NameCount("Axe", 1), NameCount("Bear", 2), NameCount("Envoy", 1)),
            summary.spells,
        )
        assertEquals(17, summary.landCount)
        assertEquals(
            listOf(NameCount("Dual Land", 1), NameCount("Plains", 8), NameCount("Island", 8)),
            summary.lands,
        )
        assertEquals("grades +10.00: strong; curve -1.50: light", summary.why)
    }

    @Test
    fun `a colour with no basic land is left out of the lands`() {
        val summary = DeckSummary.of(1, ranked(listOf(bear, bear), threeParts))

        assertEquals(listOf(NameCount("Dual Land", 1), NameCount("Plains", 16)), summary.lands)
    }

    @Test
    fun `why holds one part when only one has a reason, and nothing when none has`() {
        val one = listOf(DeckPartScore("grades", 2.0, "fine"), DeckPartScore("curve", 5.0, null))
        val none = listOf(DeckPartScore("grades", 2.0, null), DeckPartScore("curve", 5.0, null))

        assertEquals("grades +2.00: fine", DeckSummary.of(1, ranked(mixed, one)).why)
        assertEquals("", DeckSummary.of(1, ranked(mixed, none)).why)
    }

    @Test
    fun `parts of the same size keep the order of the parts`() {
        val parts =
            listOf(
                DeckPartScore("a", 1.0, "x"),
                DeckPartScore("b", -1.0, "y"),
                DeckPartScore("c", 1.0, "z"),
            )

        assertEquals("a +1.00: x; b -1.00: y", DeckSummary.of(1, ranked(mixed, parts)).why)
    }

    private val deck =
        DeckSummary(
            rank = 1,
            colours = "WU",
            sameAs = listOf("WB", "UB"),
            score = 12.5,
            spells = listOf(NameCount("Bear", 2), NameCount("Envoy", 1)),
            landCount = 17,
            lands = listOf(NameCount("Dual Land", 1), NameCount("Plains", 16)),
            why = "grades +10.00: strong; curve -1.50: light",
        )

    @Test
    fun `text is the deck block with its same-as note and why line`() {
        assertEquals(
            listOf(
                    "Rank 1: WU (same cards as WB, UB)  score 12.50",
                    "  Spells (3): 2x Bear, 1x Envoy",
                    "  Lands (17): 1x Dual Land, 16x Plains",
                    "  Why: grades +10.00: strong; curve -1.50: light",
                )
                .joinToString("\n"),
            deck.text(),
        )
    }

    @Test
    fun `text leaves out the same-as note and the why line when there is none`() {
        assertEquals(
            listOf(
                    "Rank 3: WU  score 12.50",
                    "  Spells (3): 2x Bear, 1x Envoy",
                    "  Lands (17): 1x Dual Land, 16x Plains",
                )
                .joinToString("\n"),
            deck.copy(rank = 3, sameAs = emptyList(), why = "").text(),
        )
    }
}
