package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import spa.agent.poolgrade.Part

/**
 * SPA-043 criterion 4, the printer's side of the seam: `PoolGradeLines` prints each nonzero part's
 * own reason line in list order, so a part it has never seen explains itself.
 */
class PoolGradeLinesSeamTest {
    /** A part no main code knows. */
    private data class Probe(override val value: Double) : Part {
        override val line: String?
            get() = if (value == 0.0) null else "probe part, value $value"
    }

    @Test
    fun `a part unknown to the printer prints its own reason line, after the others`() {
        val err = ByteArrayOutputStream()
        val wu =
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
                .single()
        val card = wu.cards.first { it.printing.card.name == "Enterprise NX-01" }
        val before = PoolGradeLines.lines(wu.copy(cards = listOf(card)))
        val probed = card.copy(parts = card.parts + Probe(0.25) + Probe(0.0))

        val after = PoolGradeLines.lines(wu.copy(cards = listOf(probed)))

        assertEquals(before + "      probe part, value 0.25", after)
    }
}
