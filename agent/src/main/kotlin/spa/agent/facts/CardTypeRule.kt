package spa.agent.facts

/** `land`, `creature`, `artifact` and the other card types, for each the front face has. */
object CardTypeRule : FactRule {
    override fun knows(fact: String): Boolean = fact in TYPES

    /** The card types that are facts, by their lowercase name. */
    val TYPES: Set<String> =
        setOf(
            "land",
            "creature",
            "artifact",
            "enchantment",
            "planeswalker",
            "instant",
            "sorcery",
            "battle",
        )

    override fun facts(card: FactCard): Set<String> = card.frontTypes.filter { it in TYPES }.toSet()
}
