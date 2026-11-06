package spa.agent.facts

import java.util.Locale
import spa.agent.fixing.ReminderText
import spa.core.cards.Card
import spa.core.cards.Face

/**
 * One card as the fact rules read it: the [card], its Scryfall [keywords], which `core`'s [Card]
 * does not keep, and its Scryfall Tagger [otags] (SPA-054; none when the set has no otags file).
 * The rules read the front face's type line and text, with reminder text removed.
 */
class FactCard(
    val card: Card,
    val keywords: List<String> = emptyList(),
    val otags: List<String> = emptyList(),
) {
    /** The front face. */
    val front: Face
        get() = card.faces.first()

    /** The front face's supertypes and card types, lowercased: the words before the dash. */
    val frontTypes: List<String>
        get() = words(front.typeLine.substringBefore(DASH)).map { it.lowercase(Locale.ROOT) }

    /** The front face's subtypes, as printed: the words after the dash. */
    val frontSubtypes: List<String>
        get() = words(front.typeLine.substringAfter(DASH, ""))

    /** The front face's rules text with reminder text removed. */
    val frontText: String
        get() = ReminderText.strip(front.oracleText)

    /** Every face's rules text with reminder text removed, front face first. */
    val faceTexts: List<String>
        get() = card.faces.map { ReminderText.strip(it.oracleText) }

    /** The front face's full name. */
    val fullName: String
        get() = front.name

    /** The front face's name before its first comma (the full name when it has none). */
    val shortName: String
        get() = fullName.substringBefore(',')

    /** True when the front face is a land. */
    val isLand: Boolean
        get() = "land" in frontTypes

    /**
     * A regex group matching any of [phrases], the full name or the short name, longest first, so
     * that a rule's "this creature, its full name or its short name" is one pattern.
     */
    fun subjects(phrases: List<String>): String =
        (phrases + fullName + shortName)
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending { it.length }
            .joinToString("|", "(?:", ")") { Regex.escape(it) }

    private fun words(text: String): List<String> = text.split(' ').filter { it.isNotBlank() }

    private companion object {
        const val DASH = '—'
    }
}
