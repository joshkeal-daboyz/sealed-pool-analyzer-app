package spa.agent.synergy

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The weights file `fixtures/weights/tag-synergy.json`: exactly one number, `weight`, 0 or more.
 */
class SynergyWeightTest {
    @Test
    fun `a weights file with one number reads`() {
        assertEquals(SynergyWeight(0.5), SynergyWeight.parse("""{ "weight": 0.5 }"""))
        assertEquals(SynergyWeight(0.0), SynergyWeight.parse("""{"weight":0}"""))
        assertEquals(SynergyWeight(0.5), SynergyWeight.DEFAULT)
    }

    @Test
    fun `anything else is rejected`() {
        listOf(
                "not json",
                "[0.5]",
                "{}",
                """{ "weight": 0.5, "cap": 1 }""",
                """{ "weight": -0.1 }""",
                """{ "weight": "0.5" }""",
                """{ "weight": null }""",
                """{ "synergy": 0.5 }""",
            )
            .forEach { text ->
                assertThrows<IllegalArgumentException>(text) { SynergyWeight.parse(text) }
            }
    }
}
