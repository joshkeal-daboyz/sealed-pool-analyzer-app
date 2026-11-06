package spa.agent.facts

import java.nio.file.Path
import spa.agent.grader.GradingPool
import spa.agent.themes.PoolThemes
import spa.core.cards.CardIndex

/** A [fact] and the number of [cards] that have it. */
data class FactCount(val fact: String, val cards: Int)

/**
 * The card facts of a set's sealed pool (SPA-051): a deterministic tag layer made by code from card
 * data, as the union of the [FactRule]s in a list. Model-free.
 */
object CardFacts {
    /** [card]'s facts under [rules], sorted. */
    fun of(card: FactCard, rules: List<FactRule> = FactRules.ALL): Set<String> =
        rules.flatMap { it.facts(card) }.toSortedSet()

    /**
     * Each card of [index]'s sealed pool ([GradingPool]) by its packet id, in pool order, with its
     * facts, its Scryfall keywords taken from [keywords] by oracle id (none when absent), and its
     * Scryfall Tagger tags taken from [otags] by packet id (none when absent; SPA-054).
     */
    fun pool(
        index: CardIndex,
        keywords: Map<String, List<String>>,
        rules: List<FactRule> = FactRules.ALL,
        otags: Map<String, List<String>> = emptyMap(),
    ): Map<String, Set<String>> =
        GradingPool.printings(index).associate {
            val id = GradingPool.id(it)
            id to of(FactCard(it.card, keywords[it.card.oracleId].orEmpty()), rules)
        }

    /** [pool], with the keywords read from the Scryfall caches under [scryfallDir]. */
    fun load(
        scryfallDir: Path,
        index: CardIndex,
        rules: List<FactRule> = FactRules.ALL,
        otags: Map<String, List<String>> = emptyMap(),
    ): Map<String, Set<String>> =
        pool(index, PoolThemes.keywords(scryfallDir, index.setCodes), rules, otags)

    /** Each fact of [facts] with the number of cards that have it, most first, then by name. */
    fun counts(facts: Map<String, Set<String>>): List<FactCount> =
        facts.values
            .flatten()
            .groupingBy { it }
            .eachCount()
            .map { (fact, n) -> FactCount(fact, n) }
            .sortedWith(compareByDescending<FactCount> { it.cards }.thenBy { it.fact })
}
