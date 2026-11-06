package spa.agent.deckbacktest.tune

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import spa.agent.deckbacktest.BuildOutcome
import spa.agent.deckbacktest.BuiltDeck
import spa.agent.deckbacktest.Player
import spa.agent.deckbacktest.RankedColours
import spa.core.text.Color

/**
 * SPA-060 criterion 3: the objective on hand-built backtest results: overlap share plus colour
 * agreement, averaged over the sets.
 */
class TuneObjectiveTest {
    private val blue = setOf(Color.U, Color.B)
    private val white = setOf(Color.W, Color.B)

    private fun outcome(
        player: Set<Color>,
        top: Set<Color>,
        spells: Map<String, Int>,
        built: Map<String, Int>?,
    ) =
        BuildOutcome(
            Player(player, emptySet(), spells, 17),
            listOf(RankedColours(top)),
            built?.let { BuiltDeck(it, 17) },
        )

    // Colour hit; overlap 2 of 3 registered.
    private val a1 = outcome(blue, blue, mapOf("x" to 2, "y" to 1), mapOf("x" to 2))

    // Colour miss; overlap min(1,1) + min(2,1) = 2 of 4 registered.
    private val a2 =
        outcome(white, blue, mapOf("x" to 1, "y" to 2, "z" to 1), mapOf("x" to 1, "y" to 1))

    // Colour hit; overlap 3 of 4.
    private val b1 = outcome(blue, blue, mapOf("p" to 4), mapOf("p" to 3, "q" to 1))

    @Test
    fun `a set is its mean overlap over the mean registered cards plus its colour agreement`() {
        // Overlap (2 + 2) / 2 = 2 of (3 + 4) / 2 = 3.5, colour 1 of 2.
        assertEquals(2.0 / 3.5 + 0.5, OverlapAndColour.forSet("a", listOf(a1, a2)), 1e-12)
        assertEquals(0.75 + 1.0, OverlapAndColour.forSet("b", listOf(b1)), 1e-12)
    }

    @Test
    fun `the objective is the mean over the sets`() {
        val value = TuneObjectives.DEFAULT.value(linkedMapOf("a" to listOf(a1, a2), "b" to listOf(b1)))

        assertEquals(((2.0 / 3.5 + 0.5) + 1.75) / 2, value, 1e-12)
    }

    @Test
    fun `an unbuildable build counts in the colour rate and not in the overlap`() {
        val unbuilt = outcome(blue, white, mapOf("m" to 9), null)

        // Colour 1 of 3; overlap and registered cards over the two built ones only.
        assertEquals(2.0 / 3.5 + 1.0 / 3, OverlapAndColour.forSet("a", listOf(a1, a2, unbuilt)), 1e-12)
    }

    @Test
    fun `a set where no build could be built has overlap share 0`() {
        val unbuilt = outcome(blue, blue, mapOf("m" to 9), null)

        assertEquals(1.0, OverlapAndColour.forSet("a", listOf(unbuilt)), 1e-12)
    }

    @Test
    fun `a set with no used builds is an error naming it`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                TuneObjectives.DEFAULT.value(linkedMapOf("msh" to listOf(a1), "hob" to emptyList()))
            }

        val message = failure.message.orEmpty()
        assertTrue("hob" in message && '\n' !in message, message)
    }

    @Test
    fun `no sets is an error`() {
        assertFailsWith<IllegalArgumentException> { TuneObjectives.DEFAULT.value(emptyMap()) }
    }

    @Test
    fun `the objective list has the one objective, and it is the default`() {
        assertEquals(listOf(OverlapAndColour), TuneObjectives.ALL)
        assertEquals(OverlapAndColour, TuneObjectives.DEFAULT)
    }
}
