package spa.agent.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import spa.core.text.Color

/**
 * SPA-044 criterion 5: the seam. A test-only [DeckPart] joins the list and changes the score, with
 * no other code change; so does removing or reordering a part.
 */
class DeckSeamTest {
    /** A test-only part: [weight] per red spell. */
    private object RedBonus : DeckPart {
        override val key = "redBonus"

        override fun score(deck: Deck, weight: Double, params: PartParams): DeckPartScore {
            if (weight == 0.0) return DeckPartScore.off(key)
            val red = deck.spells.count { Color.R in it.card.colors }
            return DeckPartScore(key, weight * red, "$red red spells")
        }
    }

    private val cards =
        BuildTestCards.whites("White", 24, grade = 3.0) +
            (1..4).map {
                BuildTestCards.spell("Red $it", BuildTestCards.R, "{2}{R}", 3, grade = 2.0)
            }
    private val setup = BuildTestCards.setup(BuildTestCards.candidates(cards), "W/R")
    private val base = BuildTestCards.weights("grades" to 1.0, "curve" to 1.0)

    private fun spells() = cards.take(23)

    @Test
    fun `a new part changes the score by its own value and writes its own reason`() {
        val plain = DeckBuilder(base).evaluate(setup, spells(), 17)
        val weights = base.copy(weights = base.weights + (RedBonus.key to 2.0))
        val extended = DeckBuilder(weights, DeckParts.ALL + RedBonus)

        val withRed = extended.evaluate(setup, cards.take(21) + cards.takeLast(2), 17)
        val noRed = extended.evaluate(setup, spells(), 17)

        assertEquals(plain.total, noRed.total, 1e-9)
        val part = withRed.parts.single { it.key == "redBonus" }
        assertEquals(4.0, part.value, 1e-9)
        assertEquals("2 red spells", part.reason)
        assertEquals(DeckParts.ALL.size + 1, withRed.parts.size)
        assertEquals(withRed.parts.sumOf { it.value }, withRed.total, 1e-9)
    }

    @Test
    fun `the search follows the new part`() {
        val weights = base.copy(weights = base.weights + (RedBonus.key to 5.0))

        val plain = assertNotNull(DeckBuilder(base).build(setup))
        val red = assertNotNull(DeckBuilder(weights, DeckParts.ALL + RedBonus).build(setup))

        assertEquals(0, plain.deck.spells.count { Color.R in it.card.colors })
        assertEquals(3, red.deck.spells.count { Color.R in it.card.colors }) // the splash limit
    }

    @Test
    fun `removing or reordering a part touches only the list`() {
        val all = DeckBuilder(base).evaluate(setup, spells(), 17)
        val reversed = DeckBuilder(base, DeckParts.ALL.reversed()).evaluate(setup, spells(), 17)
        val noCurve = DeckBuilder(base, DeckParts.ALL - CurveDeckPart).evaluate(setup, spells(), 17)

        assertEquals(all.parts.map { it.key }.reversed(), reversed.parts.map { it.key })
        assertEquals(all.total, reversed.total, 1e-9)
        val curve = all.parts.single { it.key == CurveDeckPart.KEY }.value
        assertTrue(curve < 0)
        assertEquals(all.total - curve, noCurve.total, 1e-9)
    }
}
