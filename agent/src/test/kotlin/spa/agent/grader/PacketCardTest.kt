package spa.agent.grader

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.grader.GraderTestCards.card
import spa.agent.grader.GraderTestCards.face
import spa.agent.grader.GraderTestCards.printing
import spa.core.cards.Rarity

/** Criterion 1: what one card in a card file says. */
class PacketCardTest {
    @Test
    fun `a single-faced card carries its own fields and its printing's rarity`() {
        val bears = card("Bears", face("Bears", "{1}{G}", "Creature — Bear", "Trample", "2", "2"))

        assertEquals(
            PacketCard(
                id = "xyz/7",
                name = "Bears",
                manaCost = "{1}{G}",
                typeLine = "Creature — Bear",
                rarity = "uncommon",
                oracleText = "Trample",
                power = "2",
                toughness = "2",
            ),
            PacketCard.of(printing(bears, "xyz", "7", rarity = Rarity.UNCOMMON)),
        )
    }

    @Test
    fun `a double-faced card joins its faces with a double slash`() {
        val dfc =
            card(
                "Front // Back",
                face("Front", "{U}", "Creature — Human", "Flip it.", "1", "1"),
                face("Back", "", "Creature — Insect", "Flying", "3", "2"),
            )

        assertEquals(
            PacketCard(
                id = "xyz/11",
                name = "Front // Back",
                manaCost = "{U} // ",
                typeLine = "Creature — Human // Creature — Insect",
                rarity = "common",
                oracleText = "Flip it. // Flying",
                power = "1 // 3",
                toughness = "1 // 2",
            ),
            PacketCard.of(printing(dfc, "xyz", "11")),
        )
    }

    @Test
    fun `power and toughness are null when no face has them, and blank on a face without`() {
        val spell =
            card("Bolt", face("Bolt", "{R}", "Instant", "Deal 3.", power = null, toughness = null))
        val adventure =
            card(
                "Giant // Stomp",
                face("Giant", "{2}{R}", "Creature — Giant", "", "4", "3"),
                face("Stomp", "{1}{R}", "Instant — Adventure", "Deal 2.", null, null),
            )

        val bolt = PacketCard.of(printing(spell, "xyz", "1"))
        val giant = PacketCard.of(printing(adventure, "xyz", "2"))

        assertEquals(null, bolt.power)
        assertEquals(null, bolt.toughness)
        assertEquals("4 // ", giant.power)
        assertEquals("3 // ", giant.toughness)
    }
}
