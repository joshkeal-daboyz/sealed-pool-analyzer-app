package spa.agent.fixing

import spa.core.text.Color

/**
 * A kind of colour fixing, named by the rule that finds it. Kinds print and sort by [name], so a
 * new rule may bring a new kind with no change to the classifier or the command.
 */
@JvmInline
value class FixingKind(val name: String) : Comparable<FixingKind> {
    override fun compareTo(other: FixingKind): Int = name.compareTo(other.name)

    override fun toString(): String = name

    companion object {
        val CYCLING = FixingKind("CYCLING")
        val MANA = FixingKind("MANA")
        val SEARCH = FixingKind("SEARCH")
        val TREASURE = FixingKind("TREASURE")
    }
}

/**
 * One way a card can fix a deck's colours (G-005 deliverable 1). Each rule is its own class in its
 * own file; [FixingClassifier] holds a list of them. A new wording is a new rule, or a change to
 * one rule, and nothing else.
 */
interface FixingRule {
    /** The kind a card gets when this rule matches any of its faces. */
    val kind: FixingKind

    /**
     * The colours [faceText] can supply by this rule, empty when the rule does not match. The text
     * is one face's oracle text with its reminder text already removed ([ReminderText.strip]).
     */
    fun colours(faceText: String): Set<Color>
}

/** All five colours, in WUBRG order. */
internal val ALL_COLOURS: Set<Color> = Color.entries.toSet()
