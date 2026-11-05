package spa.agent.deckpick

import java.util.Locale
import spa.agent.poolgrade.Weights

/** Writes tuned weights in the four-key format of `fixtures/weights/default.json`. */
object TunedWeights {
    /**
     * [weights] as one JSON object line with exactly the keys synergy, curve, colour and cap, in
     * that order, which [Weights.parse] reads back.
     */
    fun encode(weights: Weights): String =
        String.format(
            Locale.ROOT,
            "{ \"synergy\": %s, \"curve\": %s, \"colour\": %s, \"cap\": %s }\n",
            weights.synergy,
            weights.curve,
            weights.colour,
            weights.cap,
        )
}
