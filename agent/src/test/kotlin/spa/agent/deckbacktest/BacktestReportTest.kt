package spa.agent.deckbacktest

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.build.BuildConfig
import spa.agent.build.BuildTestCards
import spa.agent.build.Candidate
import spa.agent.build.RankedDeck
import spa.agent.build.ScoredDeck
import spa.core.text.Color

/** SPA-046 criteria 2 and 6: the report lines, the outcome of a built deck, the measures list. */
class BacktestReportTest {
    private fun colours(text: String): Set<Color> = text.mapNotNull { Color.fromSymbol(it) }.toSet()

    private fun outcome(player: String, top: String?, built: BuiltDeck? = null) =
        BuildOutcome(
            Player(colours(player.substringBefore('/')), colours(player.substringAfter('/', "")), emptyMap(), 17),
            listOfNotNull(top).map { RankedColours(colours(it), emptySet()) },
            built,
        )

    @Test
    fun `a group prints its count and every measure in list order, dashes when empty`() {
        val outcomes =
            listOf(
                outcome("WU", "WU", BuiltDeck(emptyMap(), 17)),
                outcome("WU", "WB", BuiltDeck(emptyMap(), 14)),
                outcome("WUB", "WUB"),
            )

        val lines = BacktestReport.lines("builder", outcomes)

        assertEquals(
            listOf(
                "builder overall n 3 colour 66.7 top3 66.7 splash 100.0 overlap 0.00 of 0.00 lands 50.0",
                "builder two n 2 colour 50.0 top3 50.0 splash 100.0 overlap 0.00 of 0.00 lands 50.0",
                "builder splash n 0 colour - top3 - splash - overlap - of - lands -",
                "builder multi n 1 colour 100.0 top3 100.0 splash 100.0 overlap - of - lands -",
            ),
            lines,
        )
    }

    private object Extra : RateMeasure("extra") {
        override fun hit(outcome: BuildOutcome): Boolean = outcome.ranked.size == 1
    }

    @Test
    fun `a measure added to the list shows in every line with no other change`() {
        val outcomes = listOf(outcome("WU", "WU"), outcome("WU", null))

        val lines = BacktestReport.lines("b", outcomes, Measures.ALL + Extra)

        assertEquals(4, lines.size)
        assertEquals(true, lines[0].endsWith(" lands - extra 50.0"), lines[0])
        assertEquals(true, lines[1].endsWith(" extra 50.0"), lines[1])
        assertEquals(true, lines[2].endsWith(" lands - extra -"), lines[2])
        assertEquals(true, lines[3].endsWith(" lands - extra -"), lines[3])
    }

    @Test
    fun `the measures are the five in the goal's order`() {
        assertEquals(
            listOf("colour", "top3", "splash", "overlap", "lands"),
            Measures.ALL.map { it.key },
        )
    }

    @Test
    fun `a ranked deck and a built deck become an outcome with colours and copy counts`() {
        val a = BuildTestCards.whites("A", 1).single()
        val b = BuildTestCards.whites("B", 1).single()
        val candidates = listOf(Candidate(a, 3), Candidate(b, 2))
        val setup = BuildTestCards.setup(candidates, "W")
        val deck = BuildTestCards.deck(setup, listOf(a, a, b), lands = 17)
        val scored = ScoredDeck(deck, emptyList())
        val ranked = listOf(RankedDeck(BuildConfig.parse("WU/R"), setup, scored, emptyList()))
        val player = Player(colours("WU"), colours("R"), emptyMap(), 16)

        val outcome = Backtest.outcome(player, ranked, scored)

        assertEquals(listOf(RankedColours(colours("WU"), colours("R"))), outcome.ranked)
        assertEquals(BuiltDeck(mapOf("oracle-${a.name}" to 2, "oracle-${b.name}" to 1), 17), outcome.built)
        assertEquals(null, Backtest.outcome(player, emptyList(), null).built)
        assertEquals(player, outcome.player)
    }
}
