package spa.agent.synergy

/**
 * The tag synergy part's weight, `fixtures/weights/tag-synergy.json`: the most the part moves a
 * card, up or down. Weight 0 switches the part off.
 */
data class SynergyWeight(val weight: Double) {
    companion object {
        /** `fixtures/weights/tag-synergy.json`. */
        val DEFAULT = SynergyWeight(0.5)

        /**
         * The weight in [text], a JSON object with exactly the number `weight`, finite and not
         * negative. Anything else is an [IllegalArgumentException].
         */
        fun parse(text: String): SynergyWeight = TODO()
    }
}
