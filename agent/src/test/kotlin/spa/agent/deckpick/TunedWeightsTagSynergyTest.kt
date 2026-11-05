package spa.agent.deckpick

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import spa.agent.poolgrade.Weights

/**
 * SPA-043 criterion 2: tuned weights keep the four-key format, and add `tagSynergy` before `cap`
 * only when it is above 0 (the Architect's ruling); either way they read back.
 */
class TunedWeightsTagSynergyTest {
    @Test
    fun `tagSynergy above 0 is written before cap and reads back`() {
        val weights = Weights(0.5, 0.25, 0.25, cap = 1.0, tagSynergy = 0.5)
        val text = TunedWeights.encode(weights)

        assertEquals(
            "{ \"synergy\": 0.5, \"curve\": 0.25, \"colour\": 0.25, \"tagSynergy\": 0.5, " +
                "\"cap\": 1.0 }\n",
            text,
        )
        assertEquals(weights, Weights.parse(text))
    }

    @Test
    fun `tagSynergy 0 is not written`() {
        val weights = Weights.parse("""{ "synergy": 0, "curve": 0.25, "colour": 0.5, "cap": 1 }""")

        assertEquals(
            "{ \"synergy\": 0.0, \"curve\": 0.25, \"colour\": 0.5, \"cap\": 1.0 }\n",
            TunedWeights.encode(weights),
        )
        assertEquals(weights, Weights.parse(TunedWeights.encode(weights)))
    }
}
