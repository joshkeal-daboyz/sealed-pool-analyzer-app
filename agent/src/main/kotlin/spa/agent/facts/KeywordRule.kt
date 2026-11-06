package spa.agent.facts

/** `kw:<keyword>` for each of the card's Scryfall keywords, slugged ([FactRules.slug]). */
object KeywordRule : FactRule {
    override fun knows(fact: String): Boolean = FactRules.prefixed(fact, "kw:")

    override fun facts(card: FactCard): Set<String> =
        card.keywords.map { "kw:${FactRules.slug(it)}" }.toSet()
}
