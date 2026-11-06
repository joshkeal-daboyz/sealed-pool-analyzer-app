package spa.agent.deckbacktest.tune

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import spa.agent.build.DeckWeights

/**
 * SPA-060 criterion 2: the grid file and the checks run before any evaluation. Each rejection is
 * one line that names what is wrong.
 */
class DeckGridTest {
    private fun reject(grid: String, start: String = START): String {
        val failure = assertFailsWith<IllegalArgumentException> { DeckGrid.parse(grid, start) }
        val message = failure.message.orEmpty()
        assertTrue('\n' !in message, message)
        return message
    }

    @Test
    fun `a grid is its knobs in file order, each with its values`() {
        val grid =
            DeckGrid.parse(
                gridText(
                    "weights.synergy" to listOf(0, 0.05, 0.1),
                    "params.removal.cap" to listOf(4, 6, 8),
                    "lands.lowCurve" to listOf(2.4, 2.6),
                ),
                START,
            )

        assertEquals(
            listOf("weights.synergy", "params.removal.cap", "lands.lowCurve"),
            grid.knobs.map { it.path },
        )
        assertEquals(listOf(0.0, 0.05, 0.1), grid.knobs[0].values)
        assertEquals(listOf(4.0, 6.0, 8.0), grid.knobs[1].values)
    }

    @Test
    fun `a bad path is rejected naming it`() {
        assertTrue("weights.bogus" in reject(gridText("weights.bogus" to listOf(1))))
        assertTrue("params.creatures.nope" in reject(gridText("params.creatures.nope" to listOf(1))))
    }

    @Test
    fun `a value that does not parse is rejected naming the knob and the value`() {
        val negative = reject(gridText("weights.curve" to listOf(0, -1)))
        assertTrue("weights.curve" in negative && "-1" in negative, negative)

        val fraction = reject(gridText("lands.normal" to listOf(17.5)))
        assertTrue("lands.normal" in fraction && "17.5" in fraction, fraction)

        val order = reject(gridText("lands.low" to listOf(20)))
        assertTrue("lands.low" in order, order)
    }

    @Test
    fun `an unknown top-level key is rejected naming it`() {
        val message = reject(gridText("bogus.curve" to listOf(1)))
        assertTrue("top-level" in message && "'bogus'" in message, message)

        assertTrue("'version'" in reject(gridText("version" to listOf(1))))
        assertTrue("'bare'" in reject(gridText("bare" to listOf(1))))
    }

    @Test
    fun `a grid that is not knobs of numbers is rejected`() {
        assertTrue("object" in reject("[1, 2]"))
        assertTrue("not JSON" in reject("{"))
        assertTrue("weights.curve" in reject("""{"weights.curve": []}"""))
        assertTrue("weights.curve" in reject("""{"weights.curve": 1}"""))
        assertTrue("weights.curve" in reject("""{"weights.curve": ["a"]}"""))
        assertTrue("weights.curve" in reject("""{"weights.curve": [1, "2"]}"""))
    }

    @Test
    fun `a start file that is not deck weights is rejected`() {
        val message = reject(gridText("weights.curve" to listOf(1)), start = """{"version": 1}""")
        assertTrue("start" in message, message)
    }

    @Test
    fun `a value is put at its path in the start file and the result parses as deck weights`() {
        val text =
            WeightsEdit.apply(
                START,
                mapOf("weights.curve" to 0.5, "params.creatures.minCreatures" to 12.0),
            )

        assertEquals(0.5, DeckWeights.parse(text).weight("curve"))
        assertEquals(0.5, WeightsEdit.read(text, "weights.curve"))
        assertEquals(12.0, WeightsEdit.read(text, "params.creatures.minCreatures"))
        assertEquals(1.0, WeightsEdit.read(text, "weights.creatures"))
        assertEquals(16.0, WeightsEdit.read(text, "lands.low"))
    }

    @Test
    fun `reading a path the file lacks gives nothing, and a path through a number is rejected`() {
        assertNull(WeightsEdit.read(START, "configs.fourColour.minFixing"))
        assertNull(WeightsEdit.read(START, "weights.curve.deeper"))
        assertFailsWith<IllegalArgumentException> {
            WeightsEdit.apply(START, mapOf("weights.curve.deeper" to 1.0))
        }
    }

    @Test
    fun `applying the same choice always gives the same text, whatever the map order`() {
        val a = linkedMapOf("lands.lowCurve" to 2.4, "configs.fourColour.minFixing" to 2.0)
        val b = linkedMapOf("configs.fourColour.minFixing" to 2.0, "lands.lowCurve" to 2.4)

        assertEquals(WeightsEdit.apply(START, a), WeightsEdit.apply(START, b))
    }
}
