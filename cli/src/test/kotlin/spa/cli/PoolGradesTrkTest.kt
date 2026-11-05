package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.agent.poolgrade.CardAdjustment
import spa.agent.poolgrade.PairGrades
import spa.core.metrics.ColorPair

/**
 * SPA-038 criterion 2: trk-manabox-01, pair WU, `trk.v2.json` and the default weights, against the
 * values an independent script computed (G-004 Architect notes, scratchpad `tools/poolgrades.js`),
 * to 3 decimals.
 */
class PoolGradesTrkTest {
    private val wu: PairGrades by lazy {
        val err = ByteArrayOutputStream()
        val pairs =
            PoolGradesCommand.compute(
                listOf(
                    Fixtures.pool("trk-manabox-01.csv"),
                    "--set",
                    "trk",
                    "--pair",
                    "WU",
                    "--data",
                    Fixtures.data,
                ),
                PrintStream(err, true, Charsets.UTF_8),
            )
        pairs.single()
    }

    private fun card(number: String): CardAdjustment =
        wu.cards.single { it.printing.setCode == "trk" && it.printing.collectorNumber == number }

    private fun assertNear(expected: Double, actual: Double, what: String) =
        assertTrue(abs(expected - actual) < 0.0005, "$what: expected $expected, got $actual")

    @Test
    fun `WU has 24 distinct candidates from 33 copies`() {
        assertEquals(ColorPair.WU, wu.pair)
        assertEquals(33, wu.copies)
        assertEquals(24, wu.cards.size)
        assertEquals(33, wu.cards.sumOf { it.quantity })
    }

    @Test
    fun `Enterprise NX-01 matches the script`() {
        val card = card("58")

        assertEquals("Enterprise NX-01", card.printing.card.name)
        assertEquals(3.5, card.setGrade)
        assertNear(0.159, card.synergy.value, "synergy")
        assertEquals(8, card.synergy.partners)
        assertNear(6.069, card.synergy.expected, "e")
        assertNear(-0.141, card.curve.value, "curve")
        assertEquals(7, card.curve.count)
        assertNear(4.478, card.curve.typical, "m")
        assertNear(0.117, card.colour.value, "colour")
        assertNear(3.635, card.adjusted, "adjusted")
    }

    @Test
    fun `Type-2 Phaser matches the script`() {
        val card = card("271")

        assertEquals("Type-2 Phaser", card.printing.card.name)
        assertEquals(2.5, card.setGrade)
        assertNear(-0.5, card.synergy.value, "synergy")
        assertEquals(0, card.synergy.partners)
        assertNear(1.655, card.synergy.expected, "e")
        assertNear(-0.170, card.curve.value, "curve")
        assertEquals(16, card.curve.count)
        assertNear(9.517, card.curve.typical, "m")
        assertEquals(0.0, card.colour.value)
        assertNear(1.830, card.adjusted, "adjusted")
    }

    @Test
    fun `Command Decision matches the script`() {
        val card = card("8")

        assertEquals("Command Decision", card.printing.card.name)
        assertEquals(3.0, card.setGrade)
        assertEquals(0.0, card.synergy.value)
        assertEquals(emptyList(), card.synergy.themes)
        assertNear(-0.170, card.curve.value, "curve")
        assertNear(-0.108, card.colour.value, "colour")
        assertNear(2.722, card.adjusted, "adjusted")
    }
}
