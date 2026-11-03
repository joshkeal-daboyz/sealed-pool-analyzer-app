package spa.core.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import spa.core.cards.Color.B
import spa.core.cards.Color.G
import spa.core.cards.Color.R
import spa.core.cards.Color.U
import spa.core.cards.Color.W

/** One test per layout rule, each on a real Scryfall card object from test resources. */
class CardNormalizerTest {
    private fun card(slug: String) = CardNormalizer.card(resourceCard(slug))

    @Test
    fun `normal creature uses the top-level fields`() {
        val card = card("grizzly-bears")

        assertEquals("Grizzly Bears", card.name)
        assertEquals("normal", card.layout)
        assertEquals(setOf(G), card.colors)
        assertEquals(2.0, card.manaValue)
        assertEquals("Creature — Bear", card.typeLine)
        assertTrue(card.isCreature)
        assertFalse(card.isLand)
        assertEquals(Rarity.COMMON, card.rarity)
        val face = card.faces.single()
        assertEquals(
            Face("Grizzly Bears", "{1}{G}", 2.0, setOf(G), "Creature — Bear", "", "2", "2"),
            face,
        )
    }

    @Test
    fun `transform takes colors, mana value, type line and creature from the front face`() {
        val card = card("delver-of-secrets")

        assertEquals("Delver of Secrets // Insectile Aberration", card.name)
        assertEquals("transform", card.layout)
        assertEquals(setOf(U), card.colors)
        assertEquals(1.0, card.manaValue)
        assertEquals("Creature — Human Wizard", card.typeLine)
        assertTrue(card.isCreature)
        assertEquals(
            listOf("Delver of Secrets", "Insectile Aberration"),
            card.faces.map { it.name },
        )
        assertEquals(listOf(1.0, 0.0), card.faces.map { it.manaValue })
        assertEquals("3", card.faces[1].power)
    }

    @Test
    fun `transform with a land front face is a colorless land, not a black creature`() {
        val card = card("westvale-abbey")

        assertEquals(emptySet(), card.colors)
        assertEquals(setOf(B), card.faces[1].colors)
        assertEquals(0.0, card.manaValue)
        assertEquals("Land", card.typeLine)
        assertFalse(card.isCreature)
        assertTrue(card.isLand)
        assertFalse(card.isBasicLand)
        assertEquals(setOf(B), card.colorIdentity)
    }

    @Test
    fun `modal double-faced card unions the faces' colors and takes the rest from the front`() {
        val card = card("valki-god-of-lies")

        assertEquals("modal_dfc", card.layout)
        assertEquals(setOf(B, R), card.colors)
        assertEquals(setOf(B), card.faces[0].colors)
        assertEquals(2.0, card.manaValue)
        assertEquals("Legendary Creature — God", card.typeLine)
        assertTrue(card.isCreature)
        assertEquals(7.0, card.faces[1].manaValue)
        assertEquals("Valki, God of Lies // Tibalt, Cosmic Impostor", card.name)
    }

    @Test
    fun `modal double-faced spell with a land back is not a land`() {
        val card = card("agadeems-awakening")

        assertEquals(setOf(B), card.colors)
        assertEquals(3.0, card.manaValue)
        assertEquals("Sorcery", card.typeLine)
        assertFalse(card.isCreature)
        assertFalse(card.isLand)
        assertEquals("Land", card.faces[1].typeLine)
    }

    @Test
    fun `adventure unions the faces' colors even where Scryfall's top level does not`() {
        val card = card("mosswood-dreadknight")

        assertEquals("adventure", card.layout)
        assertEquals(setOf(B, G), card.colors)
        assertEquals(listOf(setOf(G), setOf(B)), card.faces.map { it.colors })
        assertEquals(2.0, card.manaValue)
        assertEquals("Creature — Human Knight", card.typeLine)
        assertTrue(card.isCreature)
        assertEquals("Dread Whispers", card.faces[1].name)
        assertNull(card.faces[1].power)
    }

    @Test
    fun `split unions colors, takes the smaller mana value, joins type lines, and is no creature`() {
        val card = card("wear-tear")

        assertEquals("Wear // Tear", card.name)
        assertEquals("split", card.layout)
        assertEquals(setOf(R, W), card.colors)
        assertEquals(1.0, card.manaValue)
        assertEquals(listOf(2.0, 1.0), card.faces.map { it.manaValue })
        assertEquals("Instant // Instant", card.typeLine)
        assertFalse(card.isCreature)
        assertFalse(card.isLand)
    }

    @Test
    fun `basic land`() {
        val card = card("forest")

        assertEquals(emptySet(), card.colors)
        assertEquals(setOf(G), card.colorIdentity)
        assertEquals(0.0, card.manaValue)
        assertEquals("Basic Land — Forest", card.typeLine)
        assertTrue(card.isLand)
        assertTrue(card.isBasicLand)
        assertFalse(card.isCreature)
    }

    @Test
    fun `nonbasic land`() {
        val card = card("evolving-wilds")

        assertTrue(card.isLand)
        assertFalse(card.isBasicLand)
        assertEquals(emptySet(), card.colors)
        assertEquals(emptySet(), card.colorIdentity)
    }

    @Test
    fun `colorless artifact`() {
        val card = card("mind-stone")

        assertEquals(emptySet(), card.colors)
        assertEquals(emptySet(), card.colorIdentity)
        assertEquals(2.0, card.manaValue)
        assertEquals("Artifact", card.typeLine)
        assertFalse(card.isCreature)
        assertFalse(card.isLand)
    }

    @Test
    fun `hybrid mana card has both colors and counts each hybrid symbol once`() {
        val card = card("kitchen-finks")

        assertEquals(setOf(G, W), card.colors)
        assertEquals(3.0, card.manaValue)
        assertEquals("{1}{G/W}{G/W}", card.faces.single().manaCost)
        assertEquals(3.0, ManaCost.manaValue("{1}{G/W}{G/W}"))
        assertEquals(setOf(G, W), ManaCost.colors("{1}{G/W}{G/W}"))
        assertTrue(card.isCreature)
    }

    @Test
    fun `two-color card`() {
        val card = card("lightning-helix")

        assertEquals(setOf(R, W), card.colors)
        assertEquals(setOf(R, W), card.colorIdentity)
        assertEquals(2.0, card.manaValue)
        assertEquals("Instant", card.typeLine)
        assertEquals(Rarity.UNCOMMON, card.rarity)
    }

    @Test
    fun `printings of one oracle card share one Card instance`() {
        val first = resourceCard("grizzly-bears")
        val reprint =
            json(
                first
                    .toString()
                    .replace(first["id"].toString(), "\"another-id\"")
                    .replace("\"rarity\":\"common\"", "\"rarity\":\"rare\"")
            )

        val printings = CardNormalizer.printings(listOf(first, reprint))

        assertEquals(2, printings.size)
        assertSame(printings[0].card, printings[1].card)
        assertEquals("another-id", printings[1].scryfallId)
        assertEquals(Rarity.RARE, printings[1].rarity)
        assertEquals(Rarity.COMMON, printings[1].card.rarity)
        assertEquals("10e", printings[0].setCode)
    }

    @Test
    fun `a printing keeps set, collector number, rarity and promo types`() {
        val promo =
            json(
                cardJson("p1", set = "abc", number = "7★")
                    .replace(
                        "\"rarity\":\"common\"",
                        "\"rarity\":\"mythic\",\"promo_types\":[\"prerelease\",\"datestamped\"]",
                    )
            )

        val printing = CardNormalizer.printings(listOf(promo)).single()

        assertEquals("p1", printing.scryfallId)
        assertEquals("abc", printing.setCode)
        assertEquals("7★", printing.collectorNumber)
        assertEquals(Rarity.MYTHIC, printing.rarity)
        assertEquals(listOf("prerelease", "datestamped"), printing.promoTypes)
    }

    @Test
    fun `flip takes the front face, and a costless bottom half takes the card's colors`() {
        val card =
            CardNormalizer.card(
                json(
                    """{"id":"f","oracle_id":"o-f","name":"Top // Bottom","layout":"flip",
                    "set":"x","collector_number":"1","rarity":"uncommon","cmc":2.0,
                    "colors":["W"],"color_identity":["W"],"type_line":"Creature — Human // Creature — Spirit",
                    "card_faces":[
                      {"name":"Top","mana_cost":"{1}{W}","type_line":"Creature — Human",
                       "power":"1","toughness":"1"},
                      {"name":"Bottom","mana_cost":"","type_line":"Legendary Creature — Spirit",
                       "power":"3","toughness":"3"}]}"""
                )
            )

        assertEquals(setOf(W), card.colors)
        assertEquals(listOf(setOf(W), setOf(W)), card.faces.map { it.colors })
        assertEquals(2.0, card.manaValue)
        assertEquals("Creature — Human", card.typeLine)
        assertTrue(card.isCreature)
    }

    @Test
    fun `a face color indicator is used when the face has no colors field`() {
        val card =
            CardNormalizer.card(
                json(
                    """{"id":"i","oracle_id":"o-i","name":"A // B","layout":"transform",
                    "set":"x","collector_number":"1","rarity":"rare","color_identity":["G","U"],
                    "card_faces":[
                      {"name":"A","mana_cost":"{G}","type_line":"Creature — Elf"},
                      {"name":"B","mana_cost":"","color_indicator":["U"],"type_line":"Creature — Elf"}]}"""
                )
            )

        assertEquals(listOf(setOf(G), setOf(U)), card.faces.map { it.colors })
        assertEquals(setOf(G), card.colors)
    }

    @Test
    fun `meld has no faces, so the front face is the card itself`() {
        val card =
            CardNormalizer.card(
                json(
                    """{"id":"m","oracle_id":"o-m","name":"Bruna, the Fading Light","layout":"meld",
                    "set":"x","collector_number":"1","rarity":"rare","mana_cost":"{5}{W}{W}","cmc":7.0,
                    "colors":["W"],"color_identity":["W"],"type_line":"Legendary Creature — Angel Horror",
                    "power":"5","toughness":"7"}"""
                )
            )

        assertEquals(setOf(W), card.colors)
        assertEquals(7.0, card.manaValue)
        assertTrue(card.isCreature)
        assertEquals("5", card.faces.single().power)
    }

    @Test
    fun `an oracle id only on the faces is used, and none at all is an error`() {
        val faced =
            """{"id":"r","name":"R // R","layout":"reversible_card","set":"x","collector_number":"1",
            "rarity":"rare","color_identity":[],"card_faces":[
              {"name":"R","oracle_id":"o-r","mana_cost":"{2}","cmc":2.0,"colors":[],"type_line":"Artifact"},
              {"name":"R","oracle_id":"o-r","mana_cost":"{2}","cmc":2.0,"colors":[],"type_line":"Artifact"}]}"""

        val card = CardNormalizer.card(json(faced))

        assertEquals("o-r", card.oracleId)
        assertEquals(2.0, card.manaValue)
        assertEquals("Artifact", card.typeLine)
        assertFailsWith<IllegalArgumentException> {
            CardNormalizer.card(json(faced.replace("\"oracle_id\":\"o-r\",", "")))
        }
    }

    @Test
    fun `mana value counts numbers, variables, hybrids, Phyrexian and half symbols`() {
        assertEquals(0.0, ManaCost.manaValue(""))
        assertEquals(3.0, ManaCost.manaValue("{X}{X}{1}{R}{R}"))
        assertEquals(6.0, ManaCost.manaValue("{2/W}{2/U}{2/B}"))
        assertEquals(2.0, ManaCost.manaValue("{G/P}{C}"))
        assertEquals(0.5, ManaCost.manaValue("{½}"))
        assertEquals(1.5, ManaCost.manaValue("{HW}{1}"))
        assertEquals(setOf(W, U, B), ManaCost.colors("{2/W}{U/P}{B}{C}"))
    }

    @Test
    fun `colors keep WUBRG order and rarities must be known`() {
        assertEquals(
            listOf(W, U, B, R, G),
            Color.setOf(listOf("G", "R", "C", "B", "U", "W")).toList(),
        )
        assertNull(Color.fromSymbol("C"))
        assertEquals(Rarity.SPECIAL, Rarity.fromScryfall("special"))
        assertEquals(Rarity.BONUS, Rarity.fromScryfall("bonus"))
        assertFailsWith<IllegalArgumentException> { Rarity.fromScryfall("legendary") }
    }
}
