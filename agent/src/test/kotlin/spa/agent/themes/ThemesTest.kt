package spa.agent.themes

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.cards.Rarity
import spa.core.text.Color

/** Criterion 1: the theme rules and counts on small hand-built cards. */
class ThemesTest {
    private fun face(name: String, type: String, text: String) =
        Face(name, "{1}", 1.0, emptySet(), type, text, null, null)

    private fun card(
        name: String,
        type: String = "Creature — Bear",
        text: String = "",
        keywords: List<String> = emptyList(),
        colors: Set<Color> = setOf(Color.G),
        rarity: Rarity = Rarity.COMMON,
    ): ThemeCard {
        val faces = listOf(face(name.substringBefore(" // "), type, text))
        val card =
            Card(
                oracleId = "oracle-$name",
                name = name,
                layout = "normal",
                faces = faces,
                colors = colors,
                colorIdentity = colors,
                manaValue = 1.0,
                typeLine = faces.joinToString(" // ") { it.typeLine },
                isCreature = true,
                isLand = false,
                isBasicLand = false,
                rarity = rarity,
            )
        return ThemeCard(card, rarity, keywords)
    }

    /** [front] with a second face, [back]. */
    private fun withBack(front: ThemeCard, back: Face): ThemeCard =
        front.copy(card = front.card.copy(layout = "transform", faces = front.card.faces + back))

    private val station = Theme("Station", ThemeKind.KEYWORD)
    private val spacecraft = Theme("Spacecraft", ThemeKind.TYPE)
    private val human = Theme("Human", ThemeKind.TYPE)

    @Test
    fun `a keyword that is not evergreen is a keyword theme the card has`() {
        val c = card("Shuttle", type = "Artifact — Spacecraft", keywords = listOf("Station"))
        assertTrue(station in ThemeRules.themesOf(c, emptyList()))
        assertTrue(ThemeRules.has(c, station))
        assertFalse(ThemeRules.has(card("Bear"), station))
    }

    @Test
    fun `an evergreen keyword is no theme unless the brief names it as a mechanic`() {
        val c = card("Hawk", keywords = listOf("Flying", "Mill"))
        val themes = ThemeRules.themesOf(c, emptyList()).filter { it.kind == ThemeKind.KEYWORD }
        assertEquals(emptyList(), themes)
        val withBrief = ThemeRules.themesOf(c, listOf("Flying"))
        assertTrue(Theme("Flying", ThemeKind.KEYWORD) in withBrief)
        assertFalse(Theme("Mill", ThemeKind.KEYWORD) in withBrief)
    }

    @Test
    fun `every subtype on every face is a type theme the card has`() {
        val c =
            withBack(
                card("Picard // Locutus", type = "Legendary Creature — Human Soldier"),
                face("Locutus", "Legendary Creature — Borg", ""),
            )
        val types = ThemeRules.themesOf(c, emptyList()).filter { it.kind == ThemeKind.TYPE }
        assertEquals(setOf("Human", "Soldier", "Borg"), types.map { it.name }.toSet())
        assertTrue(ThemeRules.has(c, Theme("Borg", ThemeKind.TYPE)))
        assertFalse(ThemeRules.has(c, Theme("Legendary", ThemeKind.TYPE)))
        assertFalse(ThemeRules.has(c, Theme("Creature", ThemeKind.TYPE)))
    }

    @Test
    fun `a card refers to a theme named as a whole word in any case`() {
        assertTrue(
            ThemeRules.refersTo(card("Quark", text = "Target spacecraft gets +1/+1."), spacecraft)
        )
        assertFalse(ThemeRules.refersTo(card("Odo", text = "Draw a card."), spacecraft))
        assertFalse(ThemeRules.refersTo(card("Rom", text = "Humanity endures."), human))
    }

    @Test
    fun `the plural counts as a reference`() {
        assertTrue(
            ThemeRules.refersTo(card("Quark", text = "Humans you control get +1/+1."), human)
        )
    }

    @Test
    fun `reminder text does not count as a reference`() {
        val c = card("Quark", text = "Ward {1} (Whenever this Human or a Spacecraft is targeted.)")
        assertFalse(ThemeRules.refersTo(c, spacecraft))
        assertFalse(ThemeRules.refersTo(c, human))
    }

    @Test
    fun `the card's own face names do not count as a reference`() {
        val c =
            withBack(
                card(
                    "Human Shield // Spacecraft Wreck",
                    text = "When Human Shield dies, draw a card.",
                ),
                face("Spacecraft Wreck", "Artifact", "Spacecraft Wreck can't block."),
            )
        assertFalse(ThemeRules.refersTo(c, human))
        assertFalse(ThemeRules.refersTo(c, spacecraft))
    }

    @Test
    fun `this theme does not count as a reference but another mention does`() {
        val self =
            card("Quark", type = "Artifact — Spacecraft", text = "This Spacecraft can't block.")
        assertFalse(ThemeRules.refersTo(self, spacecraft))
        val other =
            card(
                "Odo",
                type = "Artifact — Spacecraft",
                text = "This Spacecraft gets +1/+1 for each other Spacecraft you control.",
            )
        assertTrue(ThemeRules.refersTo(other, spacecraft))
    }

    @Test
    fun `a card with the keyword does not refer to it`() {
        val c =
            card(
                "Quark",
                text = "Station. Whenever you station, scry 1.",
                keywords = listOf("Station"),
            )
        assertFalse(ThemeRules.refersTo(c, station))
        assertTrue(
            ThemeRules.refersTo(card("Odo", text = "Untap target creature with station."), station)
        )
    }

    @Test
    fun `a theme needs three cards that have it or refer to it`() {
        val two = listOf(card("Quark", keywords = listOf("Kindle")), card("Odo", text = "kindle"))
        assertEquals(
            emptyList(),
            SetThemes.count(two, emptyList()).filter { it.theme.name == "Kindle" },
        )
        val three = two + card("Rom", text = "Kindles happen.")
        val kindle = SetThemes.count(three, emptyList()).single { it.theme.name == "Kindle" }
        assertEquals(1, kindle.has.total)
        assertEquals(2, kindle.refers.total)
        assertEquals(3, kindle.either.total)
    }

    /** The Human theme's counts in a pool of three Humans, two cards naming Humans, and a Bear. */
    private fun humanSplit(): ThemeCount {
        val cards =
            listOf(
                card("W1", type = "Creature — Human", colors = setOf(Color.W)),
                card(
                    "U1",
                    type = "Creature — Human",
                    colors = setOf(Color.U),
                    rarity = Rarity.UNCOMMON,
                ),
                card(
                    "WU",
                    type = "Creature — Human",
                    text = "Other Humans get +1/+1.",
                    colors = setOf(Color.W, Color.U),
                    rarity = Rarity.RARE,
                ),
                card(
                    "Relic",
                    type = "Artifact",
                    text = "Target Human gains flying.",
                    colors = emptySet(),
                    rarity = Rarity.MYTHIC,
                ),
                card("Bear", colors = setOf(Color.G)),
            )
        return SetThemes.count(cards, emptyList()).single { it.theme == human }
    }

    @Test
    fun `the has count splits by colour and rarity`() {
        val h = humanSplit()
        assertEquals(3, h.has.total)
        assertEquals(2, h.refers.total)
        assertEquals(4, h.either.total)
        assertEquals(
            mapOf(
                ColourGroup.W to 1,
                ColourGroup.U to 1,
                ColourGroup.B to 0,
                ColourGroup.R to 0,
                ColourGroup.G to 0,
                ColourGroup.MULTICOLOURED to 1,
                ColourGroup.COLOURLESS to 0,
            ),
            h.has.byColour,
        )
        assertEquals(
            mapOf(Rarity.COMMON to 1, Rarity.UNCOMMON to 1, Rarity.RARE to 1, Rarity.MYTHIC to 0),
            h.has.byRarity,
        )
    }

    @Test
    fun `the refers and either counts split by colour and rarity`() {
        val h = humanSplit()
        assertEquals(1, h.refers.byColour[ColourGroup.MULTICOLOURED])
        assertEquals(1, h.refers.byColour[ColourGroup.COLOURLESS])
        assertEquals(0, h.refers.byColour[ColourGroup.W])
        assertEquals(
            mapOf(Rarity.COMMON to 0, Rarity.UNCOMMON to 0, Rarity.RARE to 1, Rarity.MYTHIC to 1),
            h.refers.byRarity,
        )
        assertEquals(1, h.either.byColour[ColourGroup.COLOURLESS])
        assertEquals(0, h.either.byColour[ColourGroup.G])
        assertEquals(
            mapOf(Rarity.COMMON to 1, Rarity.UNCOMMON to 1, Rarity.RARE to 1, Rarity.MYTHIC to 1),
            h.either.byRarity,
        )
    }

    @Test
    fun `themes are sorted by the either count, highest first, ties by name`() {
        val cards =
            (1..4).map { card("H$it", type = "Creature — Human") } +
                (1..3).map { card("Z$it", type = "Creature — Zombie") } +
                (1..3).map { card("E$it", type = "Creature — Elf") }
        assertEquals(
            listOf("Human", "Elf", "Zombie"),
            SetThemes.count(cards, emptyList()).map { it.theme.name },
        )
    }

    @Test
    fun `a colour group is a colour, multicoloured or colourless`() {
        assertEquals(ColourGroup.R, ColourGroup.of(setOf(Color.R)))
        assertEquals(ColourGroup.MULTICOLOURED, ColourGroup.of(setOf(Color.B, Color.G)))
        assertEquals(ColourGroup.COLOURLESS, ColourGroup.of(emptySet()))
    }
}
