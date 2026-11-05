package spa.agent.fixing

import spa.agent.grader.GradingPool
import spa.core.cards.Card
import spa.core.cards.CardIndex
import spa.core.text.Color

/**
 * A fixing card: the [kinds] of fixing it has and the [colours] they can supply, their union, in
 * WUBRG order.
 */
data class CardFixing(val card: Card, val kinds: List<FixingKind>, val colours: List<Color>) {
    /** True when the card's front face is a land. */
    val isLand: Boolean
        get() = card.isLand
}

/**
 * Classes cards as fixing by [rules]. A card gets a rule's kind when the rule matches any of its
 * faces (with reminder text removed), and its colours are the union over its kinds. The classifier
 * knows nothing of any one rule: adding, removing or changing a rule touches only [rules].
 */
class FixingClassifier(val rules: List<FixingRule> = DEFAULT_RULES) {
    /** The card's fixing, or null when no rule matches it. */
    fun classify(card: Card): CardFixing? {
        val texts = card.faces.map { ReminderText.strip(it.oracleText) }
        val byKind = sortedMapOf<FixingKind, MutableSet<Color>>()
        rules.forEach { rule ->
            val colours = texts.flatMap { rule.colours(it) }
            if (colours.isNotEmpty()) byKind.getOrPut(rule.kind) { mutableSetOf() } += colours
        }
        if (byKind.isEmpty()) return null
        val union = byKind.values.flatten().toSet()
        return CardFixing(card, byKind.keys.toList(), Color.entries.filter { it in union })
    }

    /** Every fixing card in [index]'s sealed pool ([GradingPool]), sorted by name. */
    fun setFixing(index: CardIndex): List<CardFixing> =
        GradingPool.printings(index).mapNotNull { classify(it.card) }.sortedBy { it.card.name }

    /** The distinct kinds [rules] can give, sorted by name. */
    val kinds: List<FixingKind>
        get() = rules.map { it.kind }.distinct().sorted()

    companion object {
        /** The rules G-005 deliverable 1 names. */
        val DEFAULT_RULES: List<FixingRule> =
            listOf(CyclingRule, ManaRule, SearchRule, TreasureRule)
    }
}
