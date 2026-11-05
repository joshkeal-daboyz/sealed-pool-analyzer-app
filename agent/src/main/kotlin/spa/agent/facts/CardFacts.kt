package spa.agent.facts

import java.nio.file.Path
import spa.core.cards.CardIndex

/** A fact and the number of [cards] that have it. */
data class FactCount(val fact: String, val cards: Int)

/** Stub. */
object CardFacts {
    fun of(card: FactCard, rules: List<FactRule> = FactRules.ALL): Set<String> = emptySet()

    fun pool(
        index: CardIndex,
        keywords: Map<String, List<String>>,
        rules: List<FactRule> = FactRules.ALL,
    ): Map<String, Set<String>> = emptyMap()

    fun load(
        scryfallDir: Path,
        index: CardIndex,
        rules: List<FactRule> = FactRules.ALL,
    ): Map<String, Set<String>> = emptyMap()

    fun counts(facts: Map<String, Set<String>>): List<FactCount> = emptyList()
}
