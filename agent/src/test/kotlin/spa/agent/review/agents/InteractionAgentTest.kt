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
import spa.core.cards.PoolEntry
import spa.core.metrics.MetricEngine
import spa.core.text.Color

class InteractionAgentTest {
    private val smite =
        card(
            "Smite",
            setOf(Color.W),
            "{1}{W}",
            type = "Sorcery",
            text = "Destroy target creature.",
        )
    private val zap =
        card(
            "Zap",
            setOf(Color.R),
            "{R}",
            type = "Instant",
            text = "Zap deals 2 damage to any target.",
        )
    private val negate =
        card("Negate", setOf(Color.U), "{1}{U}", type = "Instant", text = "Counter target spell.")
    private val gust =
        card(
            "Gust",
            setOf(Color.U),
            "{U}",
            type = "Instant",
            text = "Return target creature to its owner's hand.",
        )
    private val growth =
        card(
            "Growth",
            setOf(Color.G),
            "{G}",
            type = "Instant",
            text = "Target creature gets +3/+3 until end of turn.",
        )
    private val snap =
        card(
            "Snap",
            setOf(Color.U),
            "{1}{U}",
            type = "Instant",
            text = "Counter target spell.\nReturn target creature to its owner's hand.",
        )
    private val bear = card("Bear", setOf(Color.W), "{1}{W}")
    private val plains =
        card("Plains", cost = "", type = "Basic Land — Plains", text = "({T}: Add {W}.)")

    // Nonland copies: Smite 2, Zap 1, Negate 1, Gust 1, Growth 1, Snap 1, Bear 2; lands 4.
    // Removal: Smite 2 + Zap 1 = 3. Interaction: Negate, Gust, Growth, Snap = 4 (Snap counts once).
    // Castable in WU: Smite 2 (removal), Negate, Gust, Snap, Bear; Zap and Growth are not.
    private val pool =
        Pool(
            listOf(
                entry(smite, 2),
                entry(zap, 1),
                entry(negate, 1),
                entry(gust, 1),
                entry(growth, 1),
                entry(snap, 1),
                entry(bear, 2),
                entry(plains, 4),
            )
        )

    private val removalStat = Stat(median = 3.0, p10 = 2.0, p90 = 4.0, mean = 3.0)

    private fun input(pool: Pool = this.pool, baseline: Boolean = true) =
        ReviewInput(
            "xyz",
            pool,
            MetricEngine.compute(pool),
            if (baseline) ReviewTestCards.baseline(mapOf("/removalTotal" to removalStat)) else null,
            ReviewTestCards.brief,
            { null },
            emptyList(),
        )

    @Test
    fun `the agent has its id and title and always runs`() {
        assertEquals("interaction", InteractionAgent.id)
        assertEquals("Interaction", InteractionAgent.title)
        assertNull(InteractionAgent.unavailable(input()))
        assertNull(InteractionAgent.unavailable(input(baseline = false)))
    }

    @Test
    fun `the numbers are the metrics for its patterns with the baseline stats`() {
        val table = InteractionAgent.numbers(input())

        val byKey = table.entries.associateBy { it.key }
        assertEquals(NumberEntry("/removalTotal", 3.0, removalStat), byKey["/removalTotal"])
        assertEquals(NumberEntry("/interactionTotal", 4.0, null), byKey["/interactionTotal"])
        assertEquals(NumberEntry("/pairs/WU/removal", 2.0, null), byKey["/pairs/WU/removal"])
        assertEquals(
            NumberEntry("/pairs/WU/interaction", 3.0, null),
            byKey["/pairs/WU/interaction"],
        )
        assertEquals(
            NumberEntry("/byColor/U/interaction", 3.0, null),
            byKey["/byColor/U/interaction"],
        )
        assertEquals(
            NumberTables.fromMetrics(
                input(),
                listOf(
                    "/removalTotal",
                    "/interactionTotal",
                    "/pairs/*/removal",
                    "/pairs/*/interaction",
                    "/byColor/*/interaction",
                ),
            ),
            table,
        )
    }

    @Test
    fun `the facts list the cards of each tag, a two-tag card under both`() {
        val expected =
            """
            Cards by tag:
            REMOVAL (3 copies): Smite x2, Zap x1
            COUNTER (2 copies): Negate x1, Snap x1
            BOUNCE (2 copies): Gust x1, Snap x1
            TRICK (1 copies): Growth x1
            """
                .trimIndent()

        assertEquals(expected, InteractionAgent.facts(input()))
    }

    @Test
    fun `a tag with no card reads none with no copies and lands are left out`() {
        val small =
            Pool(
                listOf(
                    entry(smite, 1),
                    entry(smite, 1, id = "other-printing"),
                    PoolEntry(ReviewTestCards.printing(bear), 0),
                    entry(plains, 3),
                )
            )
        val expected =
            """
            Cards by tag:
            REMOVAL (2 copies): Smite x2
            COUNTER (0 copies): none
            BOUNCE (0 copies): none
            TRICK (0 copies): none
            """
                .trimIndent()

        assertEquals(expected, InteractionAgent.facts(input(small)))
    }
}
