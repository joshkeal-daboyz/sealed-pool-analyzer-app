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

class ManaAgentTest {
    private val plains =
        card("Plains", cost = "", type = "Basic Land — Plains", text = "({T}: Add {W}.)")
    private val azure = card("Azure Dual", cost = "", type = "Land", text = "{T}: Add {W} or {U}.")
    private val dusk = card("Dusk Dual", cost = "", type = "Land", text = "{T}: Add {B} or {R}.")
    private val torch =
        card(
            "Torch",
            setOf(Color.R),
            "{R}",
            type = "Sorcery",
            text = "Destroy target creature.",
        )
    private val bear = card("Bear", setOf(Color.W), "{1}{W}")

    // 4 basic lands, 3 other lands: Azure Dual (white-blue) 2 copies and Dusk Dual (black-red) 1.
    // Two fixing sources in the pool. White-blue has the 2 copies of Azure Dual as sources.
    // Torch is a red removal spell with one red pip: a splash for every pair without red.
    private val pool =
        Pool(
            listOf(entry(plains, 4), entry(azure, 2), entry(dusk, 1), entry(torch, 1), entry(bear, 3))
        )

    private val wuFixing = Stat(median = 3.0, p10 = 1.0, p90 = 5.0, mean = 3.0)
    private val baseline = ReviewTestCards.baseline(mapOf("/pairs/WU/fixingSources" to wuFixing))

    private fun input(pool: Pool = this.pool) =
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
        assertEquals("mana", ManaAgent.id)
        assertEquals("Mana and fixing", ManaAgent.title)
        assertNull(ManaAgent.unavailable(input()))
        assertNull(ManaAgent.unavailable(input().copy(baseline = null)))
    }

    @Test
    fun `the numbers are the metrics for its patterns with the baseline stats`() {
        val table = ManaAgent.numbers(input())

        val byKey = table.entries.associateBy { it.key }
        assertEquals(NumberEntry("/basicLands", 4.0, null), byKey["/basicLands"])
        assertEquals(NumberEntry("/fixingSources/count", 2.0, null), byKey["/fixingSources/count"])
        assertEquals(
            NumberEntry("/pairs/WU/fixingSources", 2.0, wuFixing),
            byKey["/pairs/WU/fixingSources"],
        )
        val patterns =
            listOf(
                "/basicLands",
                "/nonbasicLands",
                "/fixingSources/count",
                "/pairs/*/fixingSources",
                "/pairs/*/splashCandidates/count",
                "/pairs/*/landRecommendation/",
                "/pairs/*/castProbability/",
            )
        assertEquals(NumberTables.fromMetrics(input(), patterns), table)
    }

    @Test
    fun `the facts list fixing sources and each pair with splash candidates`() {
        val expected =
            """
            Fixing sources: Azure Dual x2, Dusk Dual x1
            Splash candidates WU: Torch (R)
            Splash candidates WB: Torch (R)
            Splash candidates WG: Torch (R)
            Splash candidates UB: Torch (R)
            Splash candidates UG: Torch (R)
            Splash candidates BG: Torch (R)
            """
                .trimIndent()

        assertEquals(expected, ManaAgent.facts(input()))
    }

    @Test
    fun `a pool with no fixing and no splash candidate says none`() {
        val plain = Pool(listOf(entry(plains, 4), entry(bear, 3)))

        assertEquals("Fixing sources: none", ManaAgent.facts(input(plain)))
    }
}
