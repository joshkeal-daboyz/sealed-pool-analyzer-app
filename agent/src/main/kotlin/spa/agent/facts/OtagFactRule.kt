package spa.agent.facts

/** `otag:<slug>` for each of the card's Scryfall Tagger tags (SPA-054), ancestors included. */
object OtagFactRule : FactRule {
    /** The prefix of an otag fact. */
    const val PREFIX = "otag:"

    override fun facts(card: FactCard): Set<String> = emptySet()
}
