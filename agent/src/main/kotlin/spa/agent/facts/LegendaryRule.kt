package spa.agent.facts

/** `legendary`: the front face is Legendary. */
object LegendaryRule : FactRule {
    override fun knows(fact: String): Boolean = fact == "legendary"

    override fun facts(card: FactCard): Set<String> =
        if ("legendary" in card.frontTypes) setOf("legendary") else emptySet()
}
