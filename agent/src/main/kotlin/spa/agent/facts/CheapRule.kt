package spa.agent.facts

/** `cheap`: not a land, and mana value 2 or less. */
object CheapRule : FactRule {
    override fun knows(fact: String): Boolean = fact == "cheap"

    /** The highest mana value that is cheap. */
    const val MAX_MANA_VALUE = 2.0

    override fun facts(card: FactCard): Set<String> =
        if (!card.isLand && card.card.manaValue <= MAX_MANA_VALUE) setOf("cheap") else emptySet()
}
