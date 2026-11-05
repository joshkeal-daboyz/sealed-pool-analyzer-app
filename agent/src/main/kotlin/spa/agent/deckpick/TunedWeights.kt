package spa.agent.deckpick

import spa.agent.poolgrade.Weights

/**
 * A weights file with an optional `comment` string beside [Weights]' four numbers, as
 * `fixtures/weights/tuned.json` is written.
 */
object TunedWeights {
    /** The comment key. */
    const val COMMENT = "comment"

    /** The weights in [text]: [Weights.parse] after an optional string [COMMENT] is removed. */
    fun parse(text: String): Weights = TODO()

    /** [weights] and [comment] as a JSON object, keys in the order synergy, curve, colour, cap. */
    fun encode(weights: Weights, comment: String): String = TODO()
}
