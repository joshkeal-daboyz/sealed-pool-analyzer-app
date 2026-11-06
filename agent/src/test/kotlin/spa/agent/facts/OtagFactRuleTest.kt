package spa.agent.facts

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.agent.facts.FactTestCards.card
import spa.agent.facts.FactTestCards.face
import spa.core.cards.CardIndex
import spa.core.cards.Printing
import spa.core.cards.Rarity

/** SPA-054 criteria 1 and 2: otag facts, and the fact names a cares table may use. */
class OtagFactRuleTest {
    @Test
    fun `each of a card's otags is an otag fact`() {
        val card = FactCard(card(face("Bear")), otags = listOf("draw", "card-advantage"))

        assertEquals(setOf("otag:draw", "otag:card-advantage"), OtagFactRule.facts(card))
        assertEquals(
            setOf("creature", "type:bear", "otag:draw", "otag:card-advantage"),
            CardFacts.of(card),
        )
    }

    @Test
    fun `a card with no otags has no otag fact`() {
        assertEquals(emptySet(), OtagFactRule.facts(FactCard(card(face("Bear")))))
    }

    @Test
    fun `the pool's otags come by packet id, and a card the map lacks gets none`() {
        val index =
            CardIndex(
                listOf("xyz"),
                listOf(
                    Printing("p1", "xyz", "1", Rarity.COMMON, emptyList(), card(face("One"))),
                    Printing(
                        "p2",
                        "xyz",
                        "2",
                        Rarity.COMMON,
                        emptyList(),
                        card(face("Two")).copy(oracleId = "o2"),
                    ),
                ),
            )

        val facts = CardFacts.pool(index, emptyMap(), otags = mapOf("xyz/2" to listOf("ramp")))

        assertEquals(setOf("creature", "type:bear"), facts["xyz/1"])
        assertEquals(setOf("creature", "type:bear", "otag:ramp"), facts["xyz/2"])
    }

    @Test
    fun `every code fact family knows its names`() {
        for (fact in
            listOf(
                "land",
                "creature",
                "artifact",
                "enchantment",
                "planeswalker",
                "instant",
                "sorcery",
                "battle",
                "legendary",
                "type:human",
                "kw:station",
                "kw:basic-landcycling",
                "cheap",
                "enters-trigger",
                "dies-trigger",
                "makes-token",
            )) {
            assertTrue(FactRules.knows(fact), fact)
        }
    }

    @Test
    fun `other names are unknown to the code rules, otag facts included`() {
        for (fact in listOf("tribal", "kw:", "type:", "otag:draw", "Legendary", "cares:land")) {
            assertFalse(FactRules.knows(fact), fact)
        }
    }

    @Test
    fun `a test-only rule that knows a name makes it known`() {
        val green =
            object : FactRule {
                override fun facts(card: FactCard) = setOf("green")

                override fun knows(fact: String) = fact == "green"
            }

        assertFalse(FactRules.knows("green"))
        assertTrue(FactRules.knows("green", FactRules.ALL + green))
    }
}
