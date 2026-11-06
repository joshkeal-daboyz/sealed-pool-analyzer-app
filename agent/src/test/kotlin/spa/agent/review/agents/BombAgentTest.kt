package spa.agent.review.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import spa.agent.review.NumberEntry
import spa.agent.review.ReviewInput
import spa.agent.review.ReviewTestCards
import spa.agent.review.ReviewTestCards.card
import spa.agent.review.ReviewTestCards.entry
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Rarity
import spa.core.metrics.MetricEngine
import spa.core.text.Color

class BombAgentTest {
    // A rare of grade 2.0 (a candidate), colourless.
    private val relic = card("Relic", cost = "{3}", type = "Artifact")

    // A common of grade 4.0 (a candidate); removal, so a removal splash in three pairs.
    private val vex =
        card(
            "Vex",
            setOf(Color.W, Color.U, Color.R),
            "{W}{U}{R}",
            type = "Sorcery",
            text = "Destroy target creature.",
        )

    // Mythics of grade 4.5: the colours are given out of WUBRG order on purpose; one name has a
    // slash.
    private val prism = card("Prism", setOf(Color.G, Color.U, Color.W), "{W}{U}{G}")
    private val fireIce = card("Fire/Ice", setOf(Color.G, Color.B, Color.R), "{B}{R}{G}")

    // Not candidates: a common of grade 3.5, an ungraded rare, a land of grade 5.0.
    private val mid = card("Mid", setOf(Color.G), "{1}{G}")
    private val ghost = card("Ghost", cost = "{2}", type = "Artifact")
    private val mountain = card("Mountain", cost = "", type = "Basic Land — Mountain")

    private val pool =
        Pool(
            listOf(
                entry(relic, 1, Rarity.RARE),
                entry(vex, 1),
                entry(prism, 1, Rarity.MYTHIC),
                entry(fireIce, 1, Rarity.MYTHIC),
                entry(mid, 2),
                entry(ghost, 1, Rarity.RARE),
                entry(mountain, 3, Rarity.RARE),
            )
        )

    private val grades =
        mapOf(
            "Relic" to 2.0,
            "Vex" to 4.0,
            "Prism" to 4.5,
            "Fire/Ice" to 4.5,
            "Mid" to 3.5,
            "Mountain" to 5.0,
        )

    private fun input(pool: Pool = this.pool, grades: Map<String, Double> = this.grades) =
        ReviewInput(
            "xyz",
            pool,
            MetricEngine.compute(pool),
            null,
            ReviewTestCards.brief,
            { grades[it.card.name] },
            emptyList(),
        )

    @Test
    fun `the agent has its id and title and always runs`() {
        assertEquals("bombs", BombAgent.id)
        assertEquals("Bombs and splashes", BombAgent.title)
        assertNull(BombAgent.unavailable(input()))
        assertNull(BombAgent.unavailable(input(Pool(emptyList()))))
    }

    @Test
    fun `the numbers hold the candidates' grades and the splash counts`() {
        val splashCounts =
            mapOf(
                "BG" to 1.0,
                "BR" to 1.0,
                "RG" to 1.0,
                "UB" to 0.0,
                "UG" to 1.0,
                "UR" to 1.0,
                "WB" to 0.0,
                "WG" to 1.0,
                "WR" to 1.0,
                "WU" to 2.0,
            )
        val expected =
            listOf(
                NumberEntry("/grades/Fire~1Ice", 4.5, null),
                NumberEntry("/grades/Prism", 4.5, null),
                NumberEntry("/grades/Vex", 4.0, null),
                NumberEntry("/grades/Relic", 2.0, null),
            ) +
                splashCounts.map { (pair, count) ->
                    NumberEntry("/pairs/$pair/splashCandidates/count", count, null)
                }

        assertEquals(expected, BombAgent.numbers(input()).entries)
    }

    @Test
    fun `a tilde in a name is escaped before the slash`() {
        val odds = Pool(listOf(entry(card("A~B/C"), 1, Rarity.RARE)))

        val keys = BombAgent.numbers(input(odds, mapOf("A~B/C" to 1.0))).entries.map { it.key }

        assertEquals("/grades/A~0B~1C", keys.first())
    }

    @Test
    fun `a card in two printings is one candidate with its best grade`() {
        val twice =
            Pool(
                listOf(
                    PoolEntry(ReviewTestCards.printing(vex, "a"), 1),
                    PoolEntry(ReviewTestCards.printing(vex, "b"), 1),
                )
            )
        val twiceInput = input(twice).copy(gradeOf = { if (it.scryfallId == "a") 4.0 else 4.5 })

        val kept = BombAgent.numbers(twiceInput).entries.filter { it.key.startsWith("/grades/") }

        assertEquals(listOf(NumberEntry("/grades/Vex", 4.5, null)), kept)
    }

    @Test
    fun `with twenty-six candidates the best twenty-five are kept, a tie broken by name`() {
        val names = (1..26).map { "C" + it.toString().padStart(2, '0') }
        val many = input(Pool(names.map { entry(card(it), 1) }), names.associateWith { 4.0 })

        val kept = BombAgent.numbers(many).entries.filter { it.key.startsWith("/grades/") }

        assertEquals(names.take(25).map { "/grades/$it" }, kept.map { it.key })
        val facts = BombAgent.facts(many)
        assertTrue("C25 (" in facts && "C26" !in facts)
    }

    @Test
    fun `the facts list the candidates best first and the splash candidates of each pair`() {
        val expected =
            """
            Candidates:
            Fire/Ice (mythic, colours BRG)
            Prism (mythic, colours WUG)
            Vex (common, colours WUR)
            Relic (rare, colours colourless)
            Splash candidates WU: Prism (G, rare or mythic), Vex (R, removal)
            Splash candidates WR: Vex (U, removal)
            Splash candidates WG: Prism (U, rare or mythic)
            Splash candidates UR: Vex (W, removal)
            Splash candidates UG: Prism (W, rare or mythic)
            Splash candidates BR: Fire/Ice (G, rare or mythic)
            Splash candidates BG: Fire/Ice (R, rare or mythic)
            Splash candidates RG: Fire/Ice (B, rare or mythic)
            """
                .trimIndent()

        assertEquals(expected, BombAgent.facts(input()))
    }
}
