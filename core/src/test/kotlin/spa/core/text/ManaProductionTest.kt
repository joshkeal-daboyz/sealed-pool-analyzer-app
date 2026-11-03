package spa.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.core.text.Cards as C
import spa.core.text.Color.B
import spa.core.text.Color.G
import spa.core.text.Color.U
import spa.core.text.Color.W

class ManaProductionTest {
    private val all = Color.entries.toSet()

    private fun colors(card: OracleCard) = ManaProduction.colors(card.typeLine, card.oracleText)

    @Test
    fun `basic land types in the type line give their colors`() {
        assertEquals(setOf(G), colors(C.forest))
        assertEquals(setOf(U, G), colors(C.breedingPool))
        assertEquals(
            Color.entries.toSet(),
            ManaProduction.colors("Land — Plains Island Swamp Mountain Forest", ""),
        )
    }

    @Test
    fun `add patterns give their colors`() {
        assertEquals(setOf(G), colors(C.llanowarElves))
        assertEquals(setOf(W, G), colors(C.selesnyaGuildgate)) // Add {G} or {W}
        assertEquals(setOf(B, G), colors(C.golgariSignet))
    }

    @Test
    fun `any color gives all five`() {
        assertEquals(all, colors(C.birdsOfParadise))
        assertEquals(
            all,
            ManaProduction.colors("Instant", "Add two mana in any combination of colors."),
        )
    }

    @Test
    fun `a land entering then add text gives its colors`() {
        assertEquals(all, colors(C.lotusCobra))
    }

    @Test
    fun `treasure makers give all five whatever the count`() {
        assertEquals(all, colors(C.prosperousInnkeeper)) // create a Treasure token
        assertEquals(all, colors(C.bigScore)) // create two Treasure tokens
        assertEquals(all, colors(C.spellSwindle)) // Create X Treasure tokens
        assertEquals(all, colors(C.treasureVault)) // Create X Treasure tokens, after {C}
        assertEquals(all, colors(C.grimHireling)) // create two Treasure tokens
    }

    @Test
    fun `colorless and non-mana cards give no colors`() {
        assertEquals(emptySet(), colors(C.solRing))
        assertEquals(emptySet(), colors(C.ornithopter))
        assertEquals(emptySet(), colors(C.murder))
    }

    @Test
    fun `reminder text alone gives no colors`() {
        // A Forest's mana comes from its type, not from "({T}: Add {G}.)".
        assertEquals(emptySet(), ManaProduction.colors("Land", C.forest.oracleText))
        // A Treasure's reminder text alone, without "create", is not a Treasure maker.
        assertEquals(
            emptySet(),
            ManaProduction.colors(
                "Artifact",
                "(It's an artifact with \"{T}: Add one mana of any color.\")",
            ),
        )
    }

    @Test
    fun `colors come back in enum order`() {
        assertEquals(listOf(B, G), colors(C.golgariSignet).toList())
        assertEquals(listOf(U, G), colors(C.breedingPool).toList())
    }
}
