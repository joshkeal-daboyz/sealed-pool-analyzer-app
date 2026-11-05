package spa.agent.synergy

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import spa.agent.tags.Axis
import spa.agent.tags.FactSupply
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.agent.tags.TagRole

/**
 * SPA-051 criterion 4: supply through `suppliedBy`, on a hand-built tag file of set `xyz`.
 *
 * Axes: `landfall` supplied by `land` 2; `deaths` supplied by `creature` 1 and `kw:deathtouch` 3;
 * `legends` supplied by nothing.
 *
 * | #   | Card         | Facts                   | Roles             |
 * |-----|--------------|-------------------------|-------------------|
 * | 1   | Plain Land   | land                    | none              |
 * | 2   | Landfall Fan | creature                | landfall payoff 3 |
 * | 3   | Big Enabler  | creature                | deaths enabler 3  |
 * | 4   | Death Fan    | creature                | deaths payoff 2   |
 * | 5   | Small Killer | creature, kw:deathtouch | deaths enabler 1  |
 * | 6   | Spell        | instant                 | none              |
 */
class FactSupplyTermTest {
    private fun enabler(axis: String, strength: Int) = TagRole(axis, "enabler", strength)

    private fun payoff(axis: String, strength: Int) = TagRole(axis, "payoff", strength)

    private val axes =
        listOf(
            Axis("landfall", "Landfall", "Lands entering.", listOf(FactSupply("land", 2))),
            Axis(
                "deaths",
                "Deaths",
                "Creatures dying.",
                listOf(FactSupply("creature", 1), FactSupply("kw:deathtouch", 3)),
            ),
            Axis("legends", "Legends", "Legendary cards.", emptyList()),
        )

    private val cards =
        listOf(
            TagCard("xyz/1", "Plain Land", emptyList(), emptyList()),
            TagCard("xyz/2", "Landfall Fan", listOf(payoff("landfall", 3)), emptyList()),
            TagCard("xyz/3", "Big Enabler", listOf(enabler("deaths", 3)), emptyList()),
            TagCard("xyz/4", "Death Fan", listOf(payoff("deaths", 2)), emptyList()),
            TagCard("xyz/5", "Small Killer", listOf(enabler("deaths", 1)), emptyList()),
            TagCard("xyz/6", "Spell", emptyList(), emptyList()),
        )

    private val facts =
        mapOf(
            "xyz/1" to setOf("land"),
            "xyz/2" to setOf("creature"),
            "xyz/3" to setOf("creature"),
            "xyz/4" to setOf("creature"),
            "xyz/5" to setOf("creature", "kw:deathtouch"),
            "xyz/6" to setOf("instant"),
        )

    private val tags = TagFile("xyz", "hand-built, tags prompt v2", axes, cards)

    private val table = SynergyTable(tags, facts = facts)

    private fun parts(a: Int, b: Int) = table.parts("xyz/$a", "xyz/$b")

    @Test
    fun `a supplied tag file scores with the axis, partner and fact terms`() {
        assertEquals(listOf("axis", "partner", "fact"), table.terms.map { it.name })
    }

    @Test
    fun `a land with no roles scores with a landfall payoff through suppliedBy`() {
        // F(land, landfall) = 2, E = 0: (2 − 0)·P(fan, landfall) 3.
        assertEquals(listOf(0, 0, 6), parts(1, 2))
        assertEquals(listOf(0, 0, 6), parts(2, 1))
        assertEquals(6, table.score("xyz/1", "xyz/2"))
    }

    @Test
    fun `an enabler at 3 gains nothing from a strength-1 fact`() {
        // Ê(Big Enabler, deaths) = max(3, 1) = 3 = E; the axis term already gives 3·2.
        assertEquals(listOf(6, 0, 0), parts(3, 4))
    }

    @Test
    fun `a weak enabler is raised to its strongest supplying fact`() {
        // Small Killer: E 1, F = max(creature 1, kw:deathtouch 3) = 3; (3 − 1)·2 = 4, total 3·2.
        assertEquals(listOf(2, 0, 4), parts(5, 4))
        assertEquals(6, table.score("xyz/4", "xyz/5"))
    }

    @Test
    fun `the diagonal uses the same formula`() {
        // Death Fan with itself: (Ê − E)·P both ways, (1 − 0)·2 + (1 − 0)·2.
        assertEquals(listOf(0, 0, 4), parts(4, 4))
        // Small Killer has no payoff, Plain Land neither: nothing on the diagonal.
        assertEquals(listOf(0, 0, 0), parts(5, 5))
        assertEquals(listOf(0, 0, 0), parts(1, 1))
    }

    @Test
    fun `a card with no supplying fact adds nothing`() {
        assertEquals(listOf(0, 0, 0), parts(6, 2))
        assertEquals(listOf(0, 0, 0), parts(6, 4))
        // Landfall Fan with Death Fan: (1 − 0)·P(Death Fan, deaths) 2.
        assertEquals(listOf(0, 0, 2), parts(2, 4))
    }

    @Test
    fun `an unknown id still scores 0`() {
        assertEquals(listOf(0, 0, 0), table.parts("xyz/1", "xyz/99"))
    }

    @Test
    fun `a tag file with no suppliedBy scores exactly as before`() {
        val plain = tags.copy(axes = axes.map { Axis(it.id, it.name, it.definition) })
        val empty = tags.copy(axes = axes.map { it.copy(suppliedBy = emptyList()) })

        listOf(plain, empty).forEach { file ->
            val withFacts = SynergyTable(file, facts = facts)
            assertEquals(listOf<PairTerm>(AxisTerm, PartnerTerm), withFacts.terms)
            assertEquals(listOf(0, 0), withFacts.parts("xyz/1", "xyz/2"))
            assertEquals(listOf(6, 0), withFacts.parts("xyz/3", "xyz/4"))
        }
        assertNull(FactSupplyTerm.forTable(plain, facts))
    }

    @Test
    fun `the table with supply is symmetric`() {
        cards.forEach { a ->
            cards.forEach { b -> assertEquals(table.parts(a.id, b.id), table.parts(b.id, a.id)) }
        }
    }
}
