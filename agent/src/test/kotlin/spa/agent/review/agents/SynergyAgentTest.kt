package spa.agent.review.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import spa.agent.review.NumberEntry
import spa.agent.review.ReviewInput
import spa.agent.review.ReviewTestCards
import spa.agent.review.SynergyView
import spa.agent.synergy.PairTerm
import spa.agent.synergy.SynergyTable
import spa.agent.synergy.SynergyTestCards
import spa.agent.synergy.SynergyTestCards.card
import spa.agent.synergy.SynergyTestCards.printing
import spa.agent.tags.Axis
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.metrics.MetricEngine

class SynergyAgentTest {
    /** A pair term that scores by the two card names, [default] for a pair it does not list. */
    private class NameTerm(private val scores: Map<Set<String>, Int>, private val default: Int) :
        PairTerm {
        override val name: String = "names"

        override fun score(a: TagCard, b: TagCard): Int = scores[setOf(a.name, b.name)] ?: default
    }

    private val stranger = printing(card("Stranger", emptySet()), "99")

    private fun poolOf(vararg counts: Pair<String, Int>) =
        Pool(
            counts.map { (name, count) ->
                PoolEntry(
                    if (name == "Stranger") stranger else SynergyTestCards.printing(name),
                    count,
                )
            }
        )

    private fun input(
        pool: Pool,
        tags: TagFile = SynergyTestCards.TAGS,
        term: PairTerm = NameTerm(emptyMap(), 0),
        withSynergy: Boolean = true,
    ): ReviewInput {
        val view =
            if (withSynergy) {
                SynergyView(tags, SynergyTable(tags, listOf(term))) {
                    "${it.setCode}/${it.collectorNumber}"
                }
            } else {
                null
            }
        return ReviewInput(
            "xyz",
            pool,
            MetricEngine.compute(pool),
            null,
            ReviewTestCards.brief,
            { null },
            emptyList(),
            view,
        )
    }

    @Test
    fun `the agent has its id and title`() {
        assertEquals("synergy", SynergyAgent.id)
        assertEquals("Synergy", SynergyAgent.title)
    }

    @Test
    fun `the agent is unavailable only without synergy tags`() {
        val pool = poolOf("Maker" to 1)

        assertEquals(
            "no synergy tags for this set",
            SynergyAgent.unavailable(input(pool, withSynergy = false)),
        )
        assertNull(SynergyAgent.unavailable(input(pool)))
    }

    @Test
    fun `the numbers count role copies on the axes in play, in the tag file's order`() {
        val tags =
            SynergyTestCards.TAGS.copy(
                axes =
                    listOf(Axis("graveyard", "Graveyard", "Uses it.")) +
                        SynergyTestCards.TAGS.axes.reversed()
            )
        val pool =
            poolOf(
                "Maker" to 2,
                "Rewarder" to 1,
                "Both" to 3,
                "Blank" to 1,
                "Stranger" to 4,
                "Red Payoff" to 0,
            )

        val table = SynergyAgent.numbers(input(pool, tags))

        // tokens: enablers Maker 2 + Both 3 = 5, payoffs Rewarder 1 + Both 3 = 4.
        // counters: enabler Both 3, no payoff. The graveyard axis has no card in the pool.
        assertEquals(
            listOf(
                NumberEntry("/axes/counters/enablers", 3.0, null),
                NumberEntry("/axes/counters/payoffs", 0.0, null),
                NumberEntry("/axes/tokens/enablers", 5.0, null),
                NumberEntry("/axes/tokens/payoffs", 4.0, null),
            ),
            table.entries,
        )
    }

    @Test
    fun `a pool with no roles has no numbers and no axes`() {
        val pool = poolOf("Namer" to 1, "Blank" to 2)

        assertEquals(emptyList(), SynergyAgent.numbers(input(pool)).entries)
        assertEquals(
            "Axes in play:\nStrongest pairs in the pool:\nnone",
            SynergyAgent.facts(input(pool)),
        )
    }

    @Test
    fun `the facts list the axes and the strongest pairs, highest first`() {
        val term =
            NameTerm(
                mapOf(
                    setOf("Maker", "Rewarder") to 5,
                    setOf("Both") to 4,
                    setOf("Maker", "Both") to 3,
                    setOf("Rewarder", "Both") to 3,
                    setOf("Namer", "Named") to 2,
                    setOf("Maker", "Blank") to 0,
                    setOf("Selfie") to 6,
                ),
                0,
            )
        val pool =
            poolOf(
                "Maker" to 1,
                "Rewarder" to 1,
                "Both" to 2,
                "Namer" to 1,
                "Named" to 1,
                "Selfie" to 1,
                "Blank" to 1,
                "Stranger" to 2,
            )

        val expected =
            """
            Axes in play:
            tokens (Tokens): Makes tokens.
            counters (Counters): Puts counters.
            Strongest pairs in the pool:
            5 Maker with Rewarder
            4 Both with itself
            3 Both with Maker
            3 Both with Rewarder
            2 Named with Namer
            """
                .trimIndent()
        assertEquals(expected, SynergyAgent.facts(input(pool, term = term)))
    }

    @Test
    fun `the facts list at most ten pairs, ties broken by the two names`() {
        val pool =
            poolOf(
                "Both" to 1,
                "Blank" to 1,
                "Maker" to 1,
                "Named" to 1,
                "Namer" to 1,
                "Rewarder" to 1,
            )

        val lines = SynergyAgent.facts(input(pool, term = NameTerm(emptyMap(), 1))).lines()

        assertEquals(
            listOf(
                "1 Blank with Both",
                "1 Blank with Maker",
                "1 Blank with Named",
                "1 Blank with Namer",
                "1 Blank with Rewarder",
                "1 Both with Maker",
                "1 Both with Named",
                "1 Both with Namer",
                "1 Both with Rewarder",
                "1 Maker with Named",
            ),
            lines.dropWhile { it != "Strongest pairs in the pool:" }.drop(1),
        )
    }
}
