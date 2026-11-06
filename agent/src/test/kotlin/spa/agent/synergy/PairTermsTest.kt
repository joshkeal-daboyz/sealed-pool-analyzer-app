package spa.agent.synergy

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import spa.agent.synergy.SynergyTestCards.tag

/** SPA-048 criterion 1: each pair term's rule, on the hand-built set `xyz`. */
class PairTermsTest {
    private fun axis(a: String, b: String) = AxisTerm.score(tag(a), tag(b))

    private fun partner(a: String, b: String) = PartnerTerm.score(tag(a), tag(b))

    @Test
    fun `the axis term one way is the enabler's strength times the payoff's`() {
        // Maker enables tokens 2, Rewarder pays off tokens 3; neither does the other side.
        assertEquals(6, axis("Maker", "Rewarder"))
        assertEquals(6, axis("Rewarder", "Maker"))
    }

    @Test
    fun `the axis term adds both directions`() {
        // Both: E 1, P 2. Twin: E 3, P 1. 1·1 + 3·2.
        assertEquals(7, axis("Both", "Twin"))
        assertEquals(7, axis("Twin", "Both"))
    }

    @Test
    fun `an axis nobody pays off adds nothing`() {
        // Both also enables counters, which no card pays off: Maker with Both is 2·2 on tokens.
        assertEquals(4, axis("Maker", "Both"))
    }

    @Test
    fun `a card with itself has axis part 2·E·P`() {
        assertEquals(2 * 1 * 2, axis("Both", "Both"))
        assertEquals(2 * 3 * 1, axis("Twin", "Twin"))
        assertEquals(0, axis("Maker", "Maker"))
    }

    @Test
    fun `the partner term one way`() {
        // One Way names Rewarder with strength 1; Rewarder names nobody.
        assertEquals(1, partner("One Way", "Rewarder"))
        assertEquals(1, partner("Rewarder", "One Way"))
    }

    @Test
    fun `the partner term both ways adds the two strengths`() {
        // Namer names Named 2, Named names Namer 1.
        assertEquals(3, partner("Namer", "Named"))
        assertEquals(3, partner("Named", "Namer"))
    }

    @Test
    fun `a card naming itself scores its strength once with itself`() {
        assertEquals(3, partner("Selfie", "Selfie"))
        assertEquals(0, partner("Namer", "Namer"))
        assertEquals(0, partner("Selfie", "Namer"))
    }

    @Test
    fun `the terms carry no partner part on axes and no axis part on partners`() {
        assertEquals(0, partner("Maker", "Rewarder"))
        assertEquals(0, axis("Namer", "Named"))
    }

    @Test
    fun `the default list is the axis term, the partner term, then the fact supply term`() {
        // SPA-051 adds FactSupplyTerm; a tag file with no suppliedBy drops it (FactSupplyTermTest).
        // SPA-054 adds CaresTerm last; a table with no cares tags drops it (CaresTermTest).
        assertEquals(
            listOf<PairTerm>(AxisTerm, PartnerTerm, FactSupplyTerm, CaresTerm),
            PairTerms.ALL,
        )
        assertEquals(listOf("axis", "partner", "fact", "cares"), PairTerms.ALL.map { it.name })
    }
}
