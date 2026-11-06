package spa.agent.facts

/**
 * `otag:<slug>` for each of the card's Scryfall Tagger tags (SPA-054), ancestors included, as the
 * set's otags file gives them; a set with no otags file gets none. Which `otag:` names exist
 * depends on the bulk file, so this rule [knows] none: `fetch-tags` checks them against the file.
 */
object OtagFactRule : FactRule {
    /** The prefix of an otag fact. */
    const val PREFIX = "otag:"

    override fun facts(card: FactCard): Set<String> = card.otags.map { "$PREFIX$it" }.toSet()
}
