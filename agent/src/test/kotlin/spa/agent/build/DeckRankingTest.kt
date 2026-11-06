package spa.agent.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import spa.core.text.Color

/** SPA-045 criterion 2: the decks of every configuration, ranked. */
class DeckRankingTest {
    private val weights = BuildTestCards.weights("grades" to 1.0)
    private val ranking = DeckRanking(DeckBuilder(weights))

    private fun cards(colour: Color, count: Int, grade: Double): List<DeckCard> =
        (1..count).map {
            BuildTestCards.spell(
                "${colour.symbol} ${"%02d".format(it)}",
                setOf(colour),
                "{2}{${colour.symbol}}",
                3,
                grade,
            )
        }

    private fun rank(
        pool: List<Candidate>,
        configs: List<String>,
        ranking: DeckRanking = this.ranking,
    ): List<RankedDeck> =
        ranking.rank(configs.map(BuildConfig::parse)) { BuildTestCards.setup(pool, "$it") }

    private fun poolOf(vararg grades: Pair<Color, Double>, count: Int = 12): List<Candidate> =
        BuildTestCards.candidates(grades.flatMap { (colour, grade) -> cards(colour, count, grade) })

    @Test
    fun `decks come out sorted by score`() {
        val pool = poolOf(Color.W to 2.0, Color.U to 4.0, Color.B to 3.0, Color.R to 1.0)

        val ranked = rank(pool, listOf("WR", "WB", "WU"))

        assertEquals(listOf("WU", "WB", "WR"), ranked.map { "${it.config}" })
        assertEquals(listOf(70.0, 58.0, 35.0), ranked.map { it.scored.total })
    }

    @Test
    fun `ties go by configuration text`() {
        val pool = poolOf(Color.W to 3.0, Color.U to 3.0, Color.B to 3.0)

        val ranked = rank(pool, listOf("WU", "WB", "UB"))

        assertEquals(listOf("UB", "WB", "WU"), ranked.map { "${it.config}" })
        assertEquals(1, ranked.map { it.scored.total }.toSet().size)
    }

    @Test
    fun `a configuration with too few candidates is left out`() {
        val pool = poolOf(Color.W to 3.0, Color.U to 3.0)

        val ranked = rank(pool, listOf("WG", "WU"))

        assertEquals(listOf("WU"), ranked.map { "${it.config}" })
    }

    private object ColourCount : DeckPart {
        override val key = "colours"

        override fun score(deck: Deck, weight: Double, params: PartParams) =
            DeckPartScore(key, weight * deck.config.colours.size, "colours")
    }

    @Test
    fun `two configurations with the same 40 cards give one deck, the higher scoring one`() {
        val weights = BuildTestCards.weights("grades" to 1.0, "colours" to 1.0)
        val ranking = DeckRanking(DeckBuilder(weights, listOf(GradesDeckPart, ColourCount)))
        val pool = poolOf(Color.W to 3.0, count = 24)

        val ranked = rank(pool, listOf("WU", "WUB", "WB"), ranking)

        assertEquals(1, ranked.size)
        assertEquals("WUB", "${ranked.single().config}")
        assertEquals(listOf("WB", "WU"), ranked.single().sameAs.map { "$it" })
    }

    @Test
    fun `a deck with the same score but other cards is its own deck`() {
        val pool = poolOf(Color.W to 3.0, Color.U to 3.0, Color.B to 3.0)

        val ranked = rank(pool, listOf("WU", "WB"))

        assertEquals(2, ranked.size)
        assertTrue(ranked.all { it.sameAs.isEmpty() })
    }

    private fun top(pool: List<Candidate>): RankedDeck {
        val configs = ConfigRules.configs(pool, ConfigTestWeights.parse())
        return ranking.rank(configs) { BuildTestCards.setup(pool, "$it") }.first()
    }

    @Test
    fun `the top deck is a pair where one pair is clearly strongest`() {
        val pool =
            poolOf(
                Color.W to 4.0,
                Color.U to 4.0,
                Color.B to 2.0,
                Color.R to 2.0,
                Color.G to 2.0,
            )

        val best = top(pool)

        assertEquals(setOf(Color.W, Color.U), best.config.main)
        assertTrue(best.config.splash.size <= 1)
    }

    @Test
    fun `the top deck has three colours where every pair is weak and a fixing land helps`() {
        val tri = BuildTestCards.land("Tri", setOf(Color.W, Color.U, Color.B))
        val pool =
            poolOf(Color.W to 3.0, Color.U to 3.0, Color.B to 3.0, count = 8) + Candidate(tri, 1)

        val best = top(pool)

        assertEquals(setOf(Color.W, Color.U, Color.B), best.config.main)
        assertEquals(40, best.scored.deck.size)
        assertEquals(listOf(tri), best.scored.deck.nonbasics)
    }
}
