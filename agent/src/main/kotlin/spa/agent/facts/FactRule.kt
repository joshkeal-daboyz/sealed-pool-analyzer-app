package spa.agent.facts

import java.util.Locale

/**
 * One family of card facts (SPA-051): the facts [facts] gives a card, made by code from its card
 * data. A card's facts are the union over the rules in a list.
 */
fun interface FactRule {
    fun facts(card: FactCard): Set<String>
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
        )

    /** [text] lowercased, with every character that is not a letter or digit turned into `-`. */
    fun slug(text: String): String =
        text.lowercase(Locale.ROOT).map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
}
