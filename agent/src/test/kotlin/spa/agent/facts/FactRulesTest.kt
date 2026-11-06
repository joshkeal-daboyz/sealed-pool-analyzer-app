package spa.agent.facts

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.agent.facts.FactTestCards.card
import spa.agent.facts.FactTestCards.face
import spa.agent.facts.FactTestCards.facts

/**
 * SPA-051 criterion 1: each fact rule on hand-built cards. Mana value is 4 unless a test sets it.
 */
class FactRulesTest {
    @Test
    fun `each card type of the front face is a fact`() {
        assertEquals(setOf("land"), facts(type = "Land"))
        assertEquals(setOf("creature", "type:bear"), facts(type = "Creature — Bear"))
        assertEquals(setOf("artifact"), facts(type = "Artifact"))
        assertEquals(setOf("enchantment"), facts(type = "Enchantment — Aura"))
        assertEquals(setOf("planeswalker"), facts(type = "Planeswalker — Jace"))
        assertEquals(setOf("instant"), facts(type = "Instant"))
        assertEquals(setOf("sorcery"), facts(type = "Sorcery"))
        assertEquals(setOf("battle"), facts(type = "Battle — Siege"))
        assertEquals(
            setOf("artifact", "creature", "type:golem"),
            facts(type = "Artifact Creature — Golem"),
        )
    }

    @Test
    fun `legendary is a fact of a legendary front face`() {
        assertEquals(
            setOf("legendary", "artifact"),
            facts(type = "Legendary Artifact — Spacecraft"),
        )
        assertEquals(setOf("legendary", "land"), facts(type = "Legendary Land"))
        assertFalse("legendary" in facts(type = "Artifact — Legendary"))
    }

    @Test
    fun `only the front face's types count`() {
        val card = card(face("Front", "Creature — Human"), face("Back", "Legendary Land"))

        assertEquals(setOf("creature", "type:human"), CardFacts.of(FactCard(card)))
    }

    @Test
    fun `a creature's subtypes are slugged, and other subtypes are not facts`() {
        assertEquals(
            setOf("creature", "type:human", "type:assembly-worker", "type:time", "type:lord"),
            facts(type = "Creature — Human Assembly-Worker Time Lord"),
        )
        assertEquals(
            setOf("legendary", "creature", "type:klingon", "type:warrior"),
            facts(type = "Legendary Creature — Klingon Warrior"),
        )
        assertEquals(setOf("artifact"), facts(type = "Artifact — Equipment"))
        assertEquals(
            setOf("enchantment", "creature", "type:o-l"),
            facts(type = "Enchantment Creature — O'L"),
        )
    }

    @Test
    fun `scryfall keywords are slugged`() {
        assertEquals(
            setOf("instant", "kw:basic-landcycling", "kw:first-strike", "kw:deathtouch"),
            facts(
                type = "Instant",
                keywords = listOf("Basic landcycling", "First strike", "Deathtouch"),
            ),
        )
    }

    @Test
    fun `a self-granted keyword with until end of turn`() {
        // Moopsy: deathtouch from Scryfall, reach granted to itself.
        assertEquals(
            setOf("cheap", "creature", "kw:deathtouch", "kw:reach", "type:beast"),
            facts(
                name = "Moopsy",
                type = "Creature — Beast",
                text = "Deathtouch\n{2}: This creature gains reach until end of turn.",
                keywords = listOf("Deathtouch"),
                manaValue = 1.0,
            ),
        )
    }

    @Test
    fun `a keyword granted by full name, by short name, and mid-sentence`() {
        val gowron = "Gowron, Eager for Battle"
        val full = "Sacrifice another creature: $gowron gains trample until end of turn."
        val short = "Sacrifice another creature: Gowron gains trample until end of turn."
        val during = "During your turn, this creature has flying."

        assertTrue("kw:trample" in facts(name = gowron, text = full))
        assertTrue("kw:trample" in facts(name = gowron, text = short))
        assertTrue("kw:flying" in facts(text = during))
    }

    @Test
    fun `a granted list gives each keyword`() {
        val text = "{1}{R}: This creature gains flying and haste until end of turn."

        assertEquals(setOf("creature", "type:bear", "kw:flying", "kw:haste"), facts(text = text))
        assertEquals(
            setOf("creature", "type:bear", "kw:first-strike", "kw:vigilance", "kw:menace"),
            facts(text = "This creature has first strike, vigilance, and menace."),
        )
    }

    @Test
    fun `only the words before until, as long as or if count`() {
        assertEquals(
            setOf("creature", "type:bear", "kw:lifelink"),
            facts(text = "This creature gains lifelink until a creature with flying dies."),
        )
        assertEquals(
            setOf("creature", "type:bear", "kw:deathtouch"),
            facts(
                text = "This creature has deathtouch as long as you control a creature with reach."
            ),
        )
        assertEquals(
            setOf("creature", "type:bear", "kw:hexproof"),
            facts(
                text = "This creature has hexproof if you control an artifact with indestructible."
            ),
        )
    }

    @Test
    fun `another creature gaining a keyword gives nothing`() {
        assertEquals(
            setOf("creature", "type:bear"),
            facts(text = "Whenever another creature gains flying, draw a card."),
        )
        assertEquals(
            setOf("creature", "type:bear"),
            facts(text = "Target creature gains double strike until end of turn."),
        )
    }

    @Test
    fun `cheap at mana value 2 and not 3, and never on a land`() {
        assertTrue("cheap" in facts(manaValue = 2.0))
        assertTrue("cheap" in facts(manaValue = 0.0))
        assertFalse("cheap" in facts(manaValue = 3.0))
        assertEquals(setOf("land"), facts(type = "Land", manaValue = 0.0))
    }

    @Test
    fun `an enters trigger by this creature, full name, short name and this land`() {
        val gowron = "Gowron, Eager for Battle"

        assertTrue("enters-trigger" in facts(text = "When this creature enters, draw a card."))
        assertTrue("enters-trigger" in facts(name = gowron, text = "When $gowron enters, scry 1."))
        assertTrue(
            "enters-trigger" in
                facts(name = gowron, text = "Whenever Gowron enters or attacks, scry 1.")
        )
        assertEquals(
            setOf("land", "enters-trigger"),
            facts(
                name = "Bajor",
                type = "Land — Planet",
                text = "This land enters tapped.\nWhen this land enters, you gain 1 life.",
                manaValue = 0.0,
            ),
        )
    }

    @Test
    fun `each of the other enters subjects`() {
        listOf("permanent", "artifact", "enchantment", "Spacecraft", "Vehicle").forEach {
            assertTrue(
                "enters-trigger" in facts(text = "When this $it enters, draw a card."),
                it,
            )
        }
    }

    @Test
    fun `another creature entering, or entering tapped, is no enters trigger`() {
        assertFalse(
            "enters-trigger" in
                facts(text = "Whenever another creature enters, put a +1/+1 counter on it.")
        )
        assertFalse("enters-trigger" in facts(text = "This creature enters tapped."))
        assertFalse(
            "enters-trigger" in facts(text = "Whenever this creature attacks, draw a card.")
        )
    }

    @Test
    fun `a dies trigger by this creature and by name`() {
        assertTrue("dies-trigger" in facts(text = "When this creature dies, draw a card."))
        assertTrue(
            "dies-trigger" in
                facts(name = "Bob, the Bear", text = "When Bob, the Bear dies, draw a card.")
        )
        assertTrue(
            "dies-trigger" in facts(name = "Bob, the Bear", text = "When Bob dies, draw a card.")
        )
        assertFalse("dies-trigger" in facts(text = "Whenever another creature dies, draw a card."))
    }

    // SPA-056: the shortened legendary name, first and last word of the name before its comma.
    private val legendary = "Legendary Creature — Human Soldier"
    private val kirkText =
        "When Captain Kirk enters, draw a card.\n" +
            "When Captain Kirk dies, scry 1.\n" +
            "Captain Kirk has flying as long as you control a Starship."

    @Test
    fun `a legendary three-word name shortened gives enters, dies and a self-granted keyword`() {
        val facts = facts(name = "Captain James T. Kirk", type = legendary, text = kirkText)

        assertTrue("enters-trigger" in facts)
        assertTrue("dies-trigger" in facts)
        assertTrue("kw:flying" in facts)
        assertTrue(
            "enters-trigger" in
                facts(
                    name = "Dr. Leonard \"Bones\" McCoy",
                    type = legendary,
                    text = "When Dr. McCoy enters, you gain 2 life.",
                )
        )
    }

    @Test
    fun `a non-legendary three-word name shortened gives nothing`() {
        assertEquals(
            setOf("creature", "type:human", "type:soldier"),
            facts(
                name = "Captain James T. Kirk",
                type = "Creature — Human Soldier",
                text = kirkText,
            ),
        )
    }

    @Test
    fun `the part after a comma is never in the shortened name`() {
        val miles = "Miles O'Brien, Transporter Ops"
        assertFalse(
            "enters-trigger" in
                facts(name = miles, type = legendary, text = "When Miles Ops enters, scry 1.")
        )
        assertTrue(
            "enters-trigger" in
                facts(name = miles, type = legendary, text = "When Miles O'Brien enters, scry 1.")
        )
        val bob = "Bob Q. Bear, the Mighty"
        assertTrue(
            "enters-trigger" in
                facts(name = bob, type = legendary, text = "When Bob Bear enters, scry 1.")
        )
        assertFalse(
            "enters-trigger" in
                facts(name = bob, type = legendary, text = "When Bob Mighty enters, scry 1.")
        )
    }

    @Test
    fun `a two-word legendary name is unchanged`() {
        val name = "Gowron Smith"
        assertTrue(
            "enters-trigger" in
                facts(name = name, type = legendary, text = "When Gowron Smith enters, scry 1.")
        )
        assertFalse(
            "enters-trigger" in
                facts(name = name, type = legendary, text = "When Gowron enters, scry 1.")
        )
        assertFalse(
            "enters-trigger" in
                facts(name = name, type = legendary, text = "When Smith enters, scry 1.")
        )
    }

    @Test
    fun `self or another creature you control entering is an enters trigger`() {
        assertTrue(
            "enters-trigger" in
                facts(
                    name = "Captain Kathryn Janeway",
                    type = legendary,
                    text =
                        "Whenever Captain Janeway or another creature you control enters, " +
                            "scry 1.",
                )
        )
        assertTrue(
            "enters-trigger" in
                facts(
                    text =
                        "Whenever this creature or another creature you control enters, " +
                            "you gain 1 life."
                )
        )
        assertFalse(
            "enters-trigger" in
                facts(text = "Whenever another creature you control enters, you gain 1 life.")
        )
        assertFalse(
            "enters-trigger" in
                facts(text = "Whenever another creature enters, put a +1/+1 counter on it.")
        )
    }

    @Test
    fun `makes-token when create is followed by token in the same sentence, on any face`() {
        assertTrue("makes-token" in facts(text = "Create a 1/1 white Soldier creature token."))
        assertTrue(
            "makes-token" in
                facts(text = "Whenever this creature attacks, it creates two Treasure tokens.")
        )
        assertFalse("makes-token" in facts(text = "Create nothing. Tokens you control get +1/+1."))
        assertFalse("makes-token" in facts(text = "Creature tokens you control get +1/+1."))
        val back = card(face("Front"), face("Back", "Sorcery", "Create a Food token."))
        assertTrue("makes-token" in CardFacts.of(FactCard(back)))
    }

    @Test
    fun `reminder text is ignored`() {
        val text =
            "Station (When this creature enters, create a 1/1 token. This creature gains " +
                "flying until end of turn. When this creature dies, scry 1.)"

        assertEquals(setOf("creature", "type:bear"), facts(text = text))
    }

    @Test
    fun `facts come back sorted`() {
        val facts = facts(type = "Legendary Creature — Bear", manaValue = 1.0)

        assertEquals(listOf("cheap", "creature", "legendary", "type:bear"), facts.toList())
    }

    @Test
    fun `slug lowercases and turns each other character into a hyphen`() {
        assertEquals("basic-landcycling", FactRules.slug("Basic landcycling"))
        assertEquals("o-l", FactRules.slug("O'L"))
        assertEquals("a--b", FactRules.slug("A, B"))
    }

    /** Criterion 5: the seam. A test-only rule, added to the list, gives a new fact. */
    @Test
    fun `a test-only rule added to the list gives its fact with no other change`() {
        val green = FactRule { if ("Test" in it.fullName) setOf("named-test") else emptySet() }
        val card = FactCard(card(face("Test Card")))

        assertEquals(setOf("creature", "type:bear"), CardFacts.of(card))
        assertEquals(
            setOf("creature", "named-test", "type:bear"),
            CardFacts.of(card, FactRules.ALL + green),
        )
    }

    @Test
    fun `the default rules are the nine families in order, then the otag facts`() {
        // SPA-054 adds OtagFactRule last.
        assertEquals(
            listOf(
                CardTypeRule,
                LegendaryRule,
                CreatureTypeRule,
                KeywordRule,
                SelfGrantedKeywordRule,
                CheapRule,
                EntersTriggerRule,
                DiesTriggerRule,
                MakesTokenRule,
                OtagFactRule,
            ),
            FactRules.ALL,
        )
    }
}
