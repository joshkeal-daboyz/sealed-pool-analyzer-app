package spa.agent.facts

/** `type:<subtype>` for each subtype of a creature front face, slugged ([FactRules.slug]). */
object CreatureTypeRule : FactRule {
    override fun facts(card: FactCard): Set<String> =
        if ("creature" !in card.frontTypes) emptySet()
        else card.frontSubtypes.map { "type:${FactRules.slug(it)}" }.toSet()
}
