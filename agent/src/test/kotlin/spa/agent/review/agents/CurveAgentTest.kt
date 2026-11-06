package spa.agent.review.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import spa.agent.review.NumberEntry
import spa.agent.review.NumberTables
import spa.agent.review.ReviewInput
import spa.agent.review.ReviewTestCards
import spa.agent.review.ReviewTestCards.card
import spa.agent.review.ReviewTestCards.entry
import spa.core.baseline.Stat
import spa.core.cards.Pool
import spa.core.metrics.MetricEngine
import spa.core.text.Color

class CurveAgentTest {
    private val white = setOf(Color.W)
    private val twoDrops = ('A'..'I').map { card("Two $it", white, "{1}{W}") }

    // White-blue castable cards: Two A (2 copies) + Two B to I (8) = 10 two-drops, Golem 1,
    // Titan 1, Wurm 1, Sigil 1 = 14 cards, 13 creatures, 1 noncreature. The mana value 2
    // creatures are the 10 two-drops.
    private val pool =
        Pool(
            listOf(entry(twoDrops.first(), 2)) +
                twoDrops.drop(1).map { entry(it, 1) } +
                listOf(
                    entry(card("Golem", cost = "{1}", type = "Creature — Golem"), 1),
                    entry(card("Titan", setOf(Color.U), "{4}{U}"), 1),
                    entry(card("Wurm", white, "{5}{W}"), 1),
                    entry(card("Sigil", white, "{1}{W}", type = "Sorcery"), 1),
                    entry(card("Brute", setOf(Color.R), "{1}{R}"), 1),
                    entry(card("Plains", cost = "", type = "Basic Land — Plains"), 3),
                )
        )

    private val wuCards = Stat(median = 14.0, p10 = 12.0, p90 = 16.0, mean = 14.0)
    private val baseline = ReviewTestCards.baseline(mapOf("/pairs/WU/cards" to wuCards))

    private val input =
        ReviewInput(
            "xyz",
            pool,
            MetricEngine.compute(pool),
            baseline,
            ReviewTestCards.brief,
            { null },
            emptyList(),
        )

    @Test
    fun `the agent has its id and title and always runs`() {
        assertEquals("curve", CurveAgent.id)
        assertEquals("Curve", CurveAgent.title)
        assertNull(CurveAgent.unavailable(input))
        assertNull(CurveAgent.unavailable(input.copy(baseline = null)))
    }

    @Test
    fun `the numbers are the metrics for its four patterns with the baseline stats`() {
        val table = CurveAgent.numbers(input)

        val byKey = table.entries.associateBy { it.key }
        assertEquals(NumberEntry("/pairs/WU/cards", 14.0, wuCards), byKey["/pairs/WU/cards"])
        assertEquals(NumberEntry("/pairs/WU/creatures", 13.0, null), byKey["/pairs/WU/creatures"])
        assertEquals(
            NumberEntry("/pairs/WU/curve/buckets/2/creatures", 10.0, null),
            byKey["/pairs/WU/curve/buckets/2/creatures"],
        )
        val patterns =
            listOf(
                "/pairs/*/cards",
                "/pairs/*/creatures",
                "/pairs/*/noncreatures",
                "/pairs/*/curve/",
            )
        assertEquals(NumberTables.fromMetrics(input, patterns), table)
    }

    @Test
    fun `the facts list two-drop creatures and top-end cards castable in each pair`() {
        val wuTwo = "Golem, Two A, Two B, Two C, Two D, Two E, Two F, Two G (+2 more)"
        val wrTwo = "Brute, Golem, Two A, Two B, Two C, Two D, Two E, Two F (+3 more)"
        val two = "creatures at mana value 2 or less:"
        val five = "cards at mana value 5 or more:"
        val expected =
            """
            WU $two $wuTwo; $five Titan, Wurm
            WB $two $wuTwo; $five Wurm
            WR $two $wrTwo; $five Wurm
            WG $two $wuTwo; $five Wurm
            UB $two Golem; $five Titan
            UR $two Brute, Golem; $five Titan
            UG $two Golem; $five Titan
            BR $two Brute, Golem; $five none
            BG $two Golem; $five none
            RG $two Brute, Golem; $five none
            """
                .trimIndent()

        assertEquals(expected, CurveAgent.facts(input))
    }
}
