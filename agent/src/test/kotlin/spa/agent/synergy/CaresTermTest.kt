package spa.agent.synergy

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.agent.otags.CaresTable
import spa.agent.tags.Axis
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.agent.tags.TagRole

/**
 * SPA-054 criteria 2 and 5: the cares term, on a hand-built set `xyz` with no roles but one axis
 * term pair (cards 4 and 5).
 *
 * | #   | Card         | Facts                          | Cares                    |
 * |-----|--------------|--------------------------------|--------------------------|
 * | 1   | Legend       | creature, legendary            | none                     |
 * | 2   | Legend Lover | creature                       | legendary 3              |
 * | 3   | Drawing Fan  | creature, legendary, otag:draw | legendary 1, otag:draw 2 |
 * | 4   | Maker        | instant                        | none                     |
 * | 5   | Taker        | sorcery                        | none                     |
 */
class CaresTermTest {
    private val cards =
        listOf(
            TagCard("xyz/1", "Legend", emptyList(), emptyList()),
            TagCard("xyz/2", "Legend Lover", emptyList(), emptyList()),
            TagCard("xyz/3", "Drawing Fan", emptyList(), emptyList()),
            TagCard("xyz/4", "Maker", listOf(TagRole("tokens", "enabler", 2)), emptyList()),
            TagCard("xyz/5", "Taker", listOf(TagRole("tokens", "payoff", 3)), emptyList()),
        )

    private val tags =
        TagFile("xyz", "hand-built", listOf(Axis("tokens", "Tokens", "Tokens.")), cards)

    private val facts =
        mapOf(
            "xyz/1" to setOf("creature", "legendary"),
            "xyz/2" to setOf("creature"),
            "xyz/3" to setOf("creature", "legendary", "otag:draw"),
            "xyz/4" to setOf("instant"),
            "xyz/5" to setOf("sorcery"),
        )

    private val cares =
        mapOf(
            "xyz/2" to mapOf("legendary" to 3),
            "xyz/3" to mapOf("legendary" to 1, "otag:draw" to 2),
        )

    private val table = SynergyTable(tags, facts = facts, cares = cares)

    private fun parts(a: Int, b: Int) = table.parts("xyz/$a", "xyz/$b")

    @Test
    fun `the table sums the axis, partner and cares terms`() {
        assertEquals(listOf("axis", "partner", "cares"), table.terms.map { it.name })
    }

    @Test
    fun `one card's fact times the other's cares strength`() {
        // Legend Lover cares about Legend's legendary at 3.
        assertEquals(listOf(0, 0, 3), parts(1, 2))
        assertEquals(listOf(0, 0, 3), parts(2, 1))
    }

    @Test
    fun `the term counts both ways`() {
        // Legend Lover cares about Drawing Fan's legendary (3); Drawing Fan cares about nothing
        // Legend Lover has.
        assertEquals(listOf(0, 0, 3), parts(2, 3))
        // Drawing Fan cares about Legend's legendary (1); Legend cares about nothing.
        assertEquals(listOf(0, 0, 1), parts(1, 3))
        assertEquals(listOf(0, 0, 1), parts(3, 1))
    }

    @Test
    fun `on the diagonal the same formula counts twice`() {
        // Drawing Fan has legendary and otag:draw and cares about both: (1 + 2) twice.
        assertEquals(listOf(0, 0, 6), parts(3, 3))
        assertEquals(listOf(0, 0, 0), parts(2, 2))
    }

    @Test
    fun `0 when no fact of one card is cared about by the other`() {
        assertEquals(listOf(0, 0, 0), parts(1, 4))
        assertEquals(listOf(0, 0, 0), parts(2, 4))
        assertEquals(listOf(6, 0, 0), parts(4, 5))
    }

    @Test
    fun `the cares part is printed only when it is not 0`() {
        assertFalse(CaresTerm.printsZero)
        assertTrue(AxisTerm.printsZero)
        assertTrue(table.terms.first { it.name == "cares" }.let { !it.printsZero })
    }

    @Test
    fun `without cares tags there is no cares term, and the table scores exactly as before`() {
        val before = SynergyTable(tags, PairTerms.ALL - CaresTerm, facts)
        for (none in listOf(emptyMap(), mapOf("xyz/1" to emptyMap<String, Int>()))) {
            val after = SynergyTable(tags, facts = facts, cares = none)

            assertEquals(listOf("axis", "partner"), after.terms.map { it.name })
            assertEquals(before.pairs(), after.pairs())
            assertNull(CaresTerm.forTable(tags, facts, none))
        }
        assertEquals(0, CaresTerm.score(cards[0], cards[1]))
    }

    @Test
    fun `a test-only cares entry and a test-only term need no other code change`() {
        val entry =
            CaresTable.parse(
                """{ "cares": { "test-only-matters": [{ "fact": "instant", "strength": 2 }] } }"""
            )
        val withEntry =
            SynergyTable(
                tags,
                facts = facts,
                cares = entry.pool(mapOf("xyz/5" to listOf("test-only-matters"))),
            )
        val one =
            object : PairTerm {
                override val name = "one"

                override fun score(a: TagCard, b: TagCard) = 1
            }
        val extended = SynergyTable(tags, PairTerms.ALL + one, facts, cares)

        assertEquals(listOf(6, 0, 2), withEntry.parts("xyz/4", "xyz/5"))
        assertEquals(listOf(0, 0, 3, 1), extended.parts("xyz/1", "xyz/2"))
        assertEquals(4, extended.score("xyz/1", "xyz/2"))
    }
}
