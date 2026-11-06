package spa.agent.otags.cares

import spa.agent.facts.OtagFactRule

/**
 * Every table suggestion names a Scryfall tag of the set that is not already in the cares table,
 * and facts that are in `vocabulary.json`.
 */
object CaresSuggestionCheck : CaresCheck {
    override fun problems(input: CaresCheckInput): List<String> =
        input.suggestions.flatMap { suggestion ->
            val where = "${CaresPackets.SUGGESTIONS}: '${suggestion.tag}'"
            val tag =
                when {
                    suggestion.tag in input.tableTags -> listOf("$where: already in cares.json")
                    OtagFactRule.PREFIX + suggestion.tag !in input.vocabulary ->
                        listOf("$where: not a Scryfall tag of this set")
                    else -> emptyList()
                }
            tag +
                suggestion.facts
                    .filter { it.fact !in input.vocabulary }
                    .map { "$where: fact '${it.fact}' is not in ${CaresPackets.VOCABULARY}" }
        }
}
