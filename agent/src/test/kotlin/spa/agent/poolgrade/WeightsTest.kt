package spa.agent.poolgrade

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Criterion 4: the weights file. */
class WeightsTest {
    @Test
    fun `a weights file with the four numbers parses`() {
        val weights =
            Weights.parse("""{ "synergy": 0.5, "curve": 0.25, "colour": 0.25, "cap": 1 }""")

        assertEquals(Weights.DEFAULT, weights)
    }

    @Test
    fun `a bad weights file is refused`() {
        listOf(
                "not json",
                "[0.5]",
                """{ "synergy": 0.5, "curve": 0.25, "colour": 0.25 }""",
                """{ "synergy": 0.5, "curve": 0.25, "colour": 0.25, "cap": 1, "extra": 1 }""",
                """{ "synergy": "0.5", "curve": 0.25, "colour": 0.25, "cap": 1 }""",
                """{ "synergy": -0.5, "curve": 0.25, "colour": 0.25, "cap": 1 }""",
                """{ "synergy": null, "curve": 0.25, "colour": 0.25, "cap": 1 }""",
                """{ "synergy": {}, "curve": 0.25, "colour": 0.25, "cap": 1 }""",
            )
            .forEach { text ->
                assertThrows<IllegalArgumentException>(text) { Weights.parse(text) }
            }
    }
}
