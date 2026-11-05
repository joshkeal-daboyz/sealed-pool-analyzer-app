package spa.agent.fixing

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.agent.fixing.FixingTestCards.card
import spa.agent.fixing.FixingTestCards.face
import spa.core.text.Color

/** SPA-042 criteria 1 and 5: each fixing rule alone on hand-built cards, and the seam. */
class FixingClassifierTest {
    private val classifier = FixingClassifier()

    private fun fixing(name: String, type: String, text: String): CardFixing? =
        classifier.classify(card(name, type, text))

    /** The card's kinds joined by `+` and its colours in WUBRG order, or `-` when not fixing. */
    private fun summary(fixing: CardFixing?): String =
        if (fixing == null) "-"
        else
            fixing.kinds.joinToString("+") { it.name } +
                " " +
                fixing.colours.joinToString("") { it.symbol.toString() }

    @Test
    fun `a two-colour land is MANA in its two colours`() {
        val dual = fixing("Rocky Shore", "Land", "This land enters tapped.\n{T}: Add {R} or {W}.")
        assertEquals("MANA WR", summary(dual))
        assertTrue(dual!!.isLand)
    }

    @Test
    fun `an any color creature is MANA in all five`() {
        val elf = fixing("Prism Elf", "Creature — Elf Druid", "{T}: Add one mana of any color.")
        assertEquals("MANA WUBRG", summary(elf))
        assertEquals(false, elf!!.isLand)
    }

    @Test
    fun `any one color, any combination of colors and chosen color are all five`() {
        listOf(
                "{T}: Add one mana of any one color.",
                "{T}: Add two mana in any combination of colors.",
                "As this enters, choose a color.\n{T}: Add one mana of the chosen color.",
            )
            .forEach { assertEquals("MANA WUBRG", summary(fixing("X", "Artifact", it)), it) }
    }

    @Test
    fun `a colourless land and a single-colour mana creature are not fixing`() {
        assertNull(fixing("Wasteland Hut", "Land", "{T}: Add {C}."))
        assertNull(fixing("Llanowar Pup", "Creature — Elf", "{T}: Add {G}."))
        assertNull(fixing("Ruby Pair", "Artifact", "{T}: Add {R}{R}."))
    }

    @Test
    fun `basic landcycling is all five and mountaincycling is red`() {
        val basic = fixing("Wanderer", "Creature — Beast", "Basic landcycling {2}")
        val mountain = fixing("Crag Goat", "Creature — Goat", "Mountaincycling {2}")
        assertEquals("CYCLING WUBRG", summary(basic))
        assertEquals("CYCLING R", summary(mountain))
    }

    @Test
    fun `each land-type cycling gives its land type's colour`() {
        mapOf("Plains" to "W", "Island" to "U", "Swamp" to "B", "Forest" to "G").forEach {
            (type, colour) ->
            assertEquals("CYCLING $colour", summary(fixing("X", "Creature", "${type}cycling {2}")))
        }
    }

    @Test
    fun `a land search to the battlefield is SEARCH and one to the graveyard is not fixing`() {
        val ramp =
            fixing(
                "Grow Wide",
                "Sorcery",
                "Search your library for a basic land card, put it onto the battlefield tapped, " +
                    "then shuffle.",
            )
        val mill =
            fixing(
                "Grave Roots",
                "Creature — Plant",
                "When this creature enters, search your library for a land card, put it into " +
                    "your graveyard, then shuffle.",
            )
        assertEquals("SEARCH WUBRG", summary(ramp))
        assertNull(mill)
    }

    @Test
    fun `a search for a card that is not a land is not fixing`() {
        assertNull(
            fixing("Tutor", "Sorcery", "Search your library for a card, put it into your hand.")
        )
    }

    @Test
    fun `a Treasure maker is TREASURE in all five`() {
        val pirate =
            fixing(
                "Lucky Pirate",
                "Creature — Pirate",
                "When this creature enters, create a Treasure token.",
            )
        assertEquals("TREASURE WUBRG", summary(pirate))
    }

    @Test
    fun `a creature whose only match is reminder text is not fixing`() {
        val reminder =
            "Ward {2} (Whenever this creature becomes the target of a spell, create a Treasure " +
                "token. Search your library for a basic land card. {T}: Add one mana of any color.)"
        assertNull(fixing("Quiet Guard", "Creature — Soldier", reminder))
    }

    @Test
    fun `reminder text is everything in parentheses`() {
        assertEquals(
            "Basic landcycling {1}{G} ",
            ReminderText.strip(
                "Basic landcycling {1}{G} ({1}{G}, Discard this card: Search your library for a " +
                    "basic land card, reveal it, put it into your hand, then shuffle.)"
            ),
        )
        assertEquals("a  b  c", ReminderText.strip("a (x) b (y) c"))
    }

    @Test
    fun `a card with two kinds has both, and colours are their union`() {
        val both =
            fixing(
                "Golden Idol",
                "Artifact",
                "{T}: Add {B} or {G}.\nWhen this artifact enters, create a Treasure token.",
            )
        assertEquals("MANA+TREASURE WUBRG", summary(both))
        val narrow = fixing("Swamp Elk", "Creature", "{T}: Add {B} or {G}.\nMountaincycling {2}")
        assertEquals("CYCLING+MANA BRG", summary(narrow))
    }

    @Test
    fun `a two-faced card whose second face matches is fixing`() {
        val mdfc =
            card(
                face("Wild Ritual", "Instant", "Target creature gets +2/+2 until end of turn."),
                face("Wild Grove", "Land", "{T}: Add {G} or {U}."),
                layout = "modal_dfc",
            )
        val fixing = classifier.classify(mdfc)
        assertEquals("MANA UG", summary(fixing))
        assertEquals(false, fixing!!.isLand, "the front face is an instant")
    }

    @Test
    fun `a quoted token ability counts`() {
        val anomaly =
            fixing(
                "Space Rift",
                "Land",
                "{T}: Add {C}.\n{3}, {T}, Sacrifice this land: Create a tapped Planet land " +
                    "token named New Planet with \"{T}: Add one mana of any color.\"",
            )
        assertEquals("MANA WUBRG", summary(anomaly))
    }

    @Test
    fun `a lowercase add mid-sentence is a MANA clause`() {
        val orphan =
            fixing(
                "Orphan Hand",
                "Legendary Creature — Human",
                "Whenever a land you control enters, add {R} or {G}.",
            )
        assertEquals("MANA RG", summary(orphan))
        val pair = fixing("Red Pair", "Creature", "Whenever a land you control enters, add {R}{R}.")
        assertNull(pair)
    }

    @Test
    fun `a MANA clause stops at the period`() {
        assertNull(fixing("Split Mana", "Artifact", "{T}: Add {R}. Spend it only on {G} spells."))
    }

    @Test
    fun `a SEARCH clause stops at the period`() {
        val tutor =
            fixing(
                "Tutor",
                "Sorcery",
                "Search your library for a card. You may reveal a land card from your hand.",
            )
        assertNull(tutor)
    }

    @Test
    fun `a TREASURE match stays within one sentence`() {
        assertNull(fixing("Token Maker", "Sorcery", "Create a token. Sacrifice a Treasure."))
    }

    @Test
    fun `a two-faced card whose front face matches and back face does not keeps its kind`() {
        val mdfc =
            card(
                face("Wild Grove", "Land", "{T}: Add {G} or {U}."),
                face("Wild Ritual", "Instant", "Target creature gets +2/+2 until end of turn."),
                layout = "modal_dfc",
            )
        val fixing = classifier.classify(mdfc)
        assertEquals("MANA UG", summary(fixing))
        assertTrue(fixing!!.isLand, "the front face is a land")
    }

    @Test
    fun `the seam - a test-only rule added to the list gives its kind with no other change`() {
        val scry =
            object : FixingRule {
                override val kind = FixingKind("SCRY")

                override fun colours(faceText: String): Set<Color> =
                    if ("Scry" in faceText) setOf(Color.U) else emptySet()
            }
        val seer = card("Seer", "Creature — Human", "When this creature enters, scry 2.\nScry 1.")
        assertNull(classifier.classify(seer))
        val extended = FixingClassifier(FixingClassifier.DEFAULT_RULES + scry)
        assertEquals("SCRY U", summary(extended.classify(seer)))
        assertEquals(
            listOf("CYCLING", "MANA", "SCRY", "SEARCH", "TREASURE"),
            extended.kinds.map { it.name },
        )
    }

    @Test
    fun `the default rules are the four kinds`() {
        assertEquals(
            listOf("CYCLING", "MANA", "SEARCH", "TREASURE"),
            classifier.kinds.map { it.name },
        )
    }
}
