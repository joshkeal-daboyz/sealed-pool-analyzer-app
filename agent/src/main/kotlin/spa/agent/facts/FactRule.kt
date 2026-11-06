package spa.agent.facts

import java.util.Locale

/**
 * One family of card facts (SPA-051): the facts [facts] gives a card, made by code from its card
 * data. A card's facts are the union over the rules in a list.
 */
@JvmDefaultWithoutCompatibility
fun interface FactRule {
    fun facts(card: FactCard): Set<String>

    /**
     * True when [fact] is a name this rule's family gives (SPA-054: a cares table may name it). By
     * default a rule names no fact.
     */
    fun knows(fact: String): Boolean = false
}

/** The fact rules, in order. A new fact is one file and one entry here. */
object FactRules {
    val ALL: List<FactRule> =
        listOf(
            CardTypeRule,
            LegendaryRule,
            CreatureTypeRule,
            KeywordRule,
            SelfGrantedKeywordRule,
            CheapRule,
            EntersTriggerRule,
            DiesTriggerRule,
            MakesTokenRule,
            OtagFactRule,
        )

    /** True when a rule of [rules] knows [fact] ([FactRule.knows]). */
    fun knows(fact: String, rules: List<FactRule> = ALL): Boolean = false

    /** [text] lowercased, with every character that is not a letter or digit turned into `-`. */
    fun slug(text: String): String =
        text.lowercase(Locale.ROOT).map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
}
