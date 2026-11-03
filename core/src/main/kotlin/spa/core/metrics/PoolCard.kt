package spa.core.metrics

import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.cards.PoolEntry
import spa.core.cards.Rarity
import spa.core.text.Color
import spa.core.text.ManaCost
import spa.core.text.ManaProduction
import spa.core.text.RulesTagger
import spa.core.text.Tag

/** One pool entry with what [MetricEngine] reads from its card worked out once. */
internal class PoolCard(entry: PoolEntry) {
    val card: Card = entry.printing.card

    /** The printing's rarity, which is what the pool holds. */
    val rarity: Rarity = entry.printing.rarity

    val count: Int = entry.count

    /** [RulesTagger.tag] over every face, unioned. */
    val tags: Set<Tag> =
        card.faces.flatMapTo(mutableSetOf()) { RulesTagger.tag(it.typeLine, it.oracleText) }

    /** [ManaProduction.colors] over every face, unioned, in WUBRG order. */
    val produces: Set<Color> =
        card.faces
            .flatMapTo(mutableSetOf()) { ManaProduction.colors(it.typeLine, it.oracleText) }
            .let { found -> Color.entries.filter { it in found }.toSet() }

    /** The pips of one copy, from [pipFace]'s mana cost. */
    val pips: Map<Color, Double> = ManaCost.parse(pipFace().manaCost).pips

    val isRemoval: Boolean
        get() = Tag.REMOVAL in tags

    val isInteraction: Boolean
        get() = tags.any { it.isInteraction }

    /** Tagged [Tag.FIXING], or a land that produces two or more colors. */
    val isFixingSource: Boolean
        get() = Tag.FIXING in tags || (card.isLand && produces.size >= 2)

    /**
     * The face whose mana cost gives the pips: for `split`, the cheaper face (the first on a tie);
     * for `adventure`, the creature face; otherwise the front face.
     */
    private fun pipFace(): Face =
        when (card.layout) {
            SPLIT -> card.faces.minBy { it.manaValue }
            ADVENTURE -> card.faces.firstOrNull { isCreatureFace(it) } ?: card.faces.first()
            else -> card.faces.first()
        }

    private fun isCreatureFace(face: Face): Boolean =
        "Creature" in face.typeLine.substringBefore('—').split(' ')

    private companion object {
        const val SPLIT = "split"
        const val ADVENTURE = "adventure"
    }
}

/** The copies across these entries. */
internal fun List<PoolCard>.copies(): Int = sumOf { it.count }
