package spa.core.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import spa.core.cards.Color.G
import spa.core.cards.Color.R
import spa.core.cards.Color.U

/**
 * Added by the Test Inspector: layout-rule clauses where the resource cards give the same answer
 * whether or not the rule holds.
 */
class LayoutRuleInspectorTest {
    @Test
    fun `split is not a creature even when one half is`() {
        val card =
            CardNormalizer.card(
                json(
                    """{"id":"s","oracle_id":"o-s","name":"Beast // Bolt","layout":"split",
                    "set":"x","collector_number":"1","rarity":"rare","cmc":3.0,"colors":["G","R"],
                    "color_identity":["G","R"],"type_line":"Creature — Beast // Instant",
                    "mana_cost":"{1}{G} // {R}","card_faces":[
                      {"name":"Beast","mana_cost":"{1}{G}","type_line":"Creature — Beast",
                       "power":"2","toughness":"2"},
                      {"name":"Bolt","mana_cost":"{R}","type_line":"Instant"}]}"""
                )
            )

        assertFalse(card.isCreature)
        assertEquals("Creature — Beast // Instant", card.typeLine)
        assertEquals(1.0, card.manaValue)
        assertEquals(setOf(G, R), card.colors)
    }

    @Test
    fun `modal double-faced card with a noncreature front and a creature back is not a creature`() {
        val card =
            CardNormalizer.card(
                json(
                    """{"id":"m","oracle_id":"o-m","name":"Spell // Elf","layout":"modal_dfc",
                    "set":"x","collector_number":"1","rarity":"rare","cmc":2.0,
                    "color_identity":["G","U"],"type_line":"Sorcery // Creature — Elf",
                    "card_faces":[
                      {"name":"Spell","mana_cost":"{1}{G}","colors":["G"],"type_line":"Sorcery"},
                      {"name":"Elf","mana_cost":"{3}{U}","colors":["U"],
                       "type_line":"Creature — Elf","power":"3","toughness":"3"}]}"""
                )
            )

        assertFalse(card.isCreature)
        assertEquals("Sorcery", card.typeLine)
        assertEquals(2.0, card.manaValue)
        assertEquals(setOf(G, U), card.colors)
        assertFalse(card.isLand)
    }

    @Test
    fun `adventure takes the front face's mana value when the halves differ`() {
        val giant = assertNotNull(CardIndex.load(fixturesDir, "syn").byName("Bonecrusher Giant"))

        assertEquals("adventure", giant.layout)
        assertEquals(listOf(3.0, 2.0), giant.faces.map { it.manaValue })
        assertEquals(3.0, giant.manaValue)
        assertEquals("Creature — Giant", giant.typeLine)
        assertTrue(giant.isCreature)
        assertEquals(setOf(R), giant.colors)
    }
}
