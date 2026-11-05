package spa.agent.deckpick

import java.util.Locale
import spa.agent.poolgrade.Weights

/** Writes tuned weights in the four-key format of `fixtures/weights/default.json`. */
object TunedWeights {
    /**
     * [weights] as one JSON object line with the keys synergy, curve, colour and cap, in that
     * order, and tagSynergy before cap only when it is above 0 (SPA-043's ruling), which
     * [Weights.parse] reads back.
     */
    fun encode(weights: Weights): String {
        val tagSynergy =
            if (weights.tagSynergy > 0.0) {
                String.format(Locale.ROOT, "\"tagSynergy\": %s, ", weights.tagSynergy)
            } else ""
        return String.format(
            Locale.ROOT,
            "{ \"synergy\": %s, \"curve\": %s, \"colour\": %s, %s\"cap\": %s }\n",
            weights.synergy,
            weights.curve,
            weights.colour,
            tagSynergy,
            weights.cap,
        )
    }
}
