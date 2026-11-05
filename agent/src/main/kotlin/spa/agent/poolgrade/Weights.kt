package spa.agent.poolgrade

/**
 * The pool adjustment's weights: the most each rule can move a grade ([synergy], [curve],
 * [colour]), and the [cap] on their sum, in grade points.
 */
data class Weights(val synergy: Double, val curve: Double, val colour: Double, val cap: Double) {
    companion object {
        /** `fixtures/weights/default.json`. */
        val DEFAULT = Weights(synergy = 0.5, curve = 0.25, colour = 0.25, cap = 1.0)

        /**
         * The weights in [text], a JSON object with exactly the numbers `synergy`, `curve`,
         * `colour` and `cap`, each finite and not negative. Anything else is an
         * [IllegalArgumentException].
         */
        fun parse(text: String): Weights = TODO("SPA-038")
    }
}
