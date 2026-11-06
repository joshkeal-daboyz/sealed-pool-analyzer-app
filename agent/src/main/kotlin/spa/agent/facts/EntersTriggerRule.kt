package spa.agent.facts

/**
 * `enters-trigger`: the front face has its own enters trigger, `When` or `Whenever`, then "this
 * creature" (or permanent, artifact, enchantment, land, Spacecraft, Vehicle), its full name or its
 * short name, then "enters".
 */
object EntersTriggerRule : FactRule {
    override fun knows(fact: String): Boolean = fact == "enters-trigger"

    /** The "this ..." subjects of an own enters trigger, besides the card's names. */
    val SUBJECTS =
        listOf(
            "this creature",
            "this permanent",
            "this artifact",
            "this enchantment",
            "this land",
            "this Spacecraft",
            "this Vehicle",
        )

    override fun facts(card: FactCard): Set<String> =
        if (Triggers.has(card, SUBJECTS, "enters")) setOf("enters-trigger") else emptySet()
}
