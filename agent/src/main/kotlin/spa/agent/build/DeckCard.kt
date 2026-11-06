package spa.agent.build

import spa.agent.fixing.CardFixing
import spa.agent.grader.GradingPool
import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.text.Color
import spa.core.text.ManaCost
import spa.core.text.RulesTagger
import spa.core.text.Tag

/**
 * One distinct card as the deck builder sees it: its [id] (`<set>/<collector number>`, the order
 * ties break in), its [card], its set [grade] and its [fixing], if the classifier found any.
 */
class DeckCard(val id: String, val card: Card, val grade: Double, val fixing: CardFixing? = null) {
    val name: String
        get() = card.name

    /** The mana value, rounded down. */
    val manaValue: Int = card.manaValue.toInt()

    /** The curve bucket: the mana value, 6 standing for 6 or more. */
    val bucket: Int = minOf(manaValue, TOP_BUCKET)

    /** The coloured pips of one copy. */
    val pips: Map<Color, Double> = ManaCost.parse(pipFace().manaCost).pips

    /** True when any face's rules text is tagged as removal. */
    val isRemoval: Boolean =
        card.faces.any { Tag.REMOVAL in RulesTagger.tag(it.typeLine, it.oracleText) }

    /** True when the card has any of [config]'s splash colours. */
    fun isSplash(config: BuildConfig): Boolean = card.colors.any { it in config.splash }

    /** The pips of [config]'s splash colours in one copy. */
    fun splashPips(config: BuildConfig): Double =
        pips.filterKeys { it in config.splash }.values.sum()

    private fun pipFace(): Face =
        when (card.layout) {
            "split" -> card.faces.minBy { it.manaValue }
            "adventure" ->
                card.faces.firstOrNull { "Creature" in it.typeLine } ?: card.faces.first()
            else -> card.faces.first()
        }

    override fun toString(): String = id

    companion object {
        /** The highest curve bucket. */
        const val TOP_BUCKET = 6

        /** Cards in collector order: by set, then by number as a number, then as text. */
        val ORDER: Comparator<DeckCard> =
            compareBy<DeckCard> { it.id.substringBefore('/') }
                .thenComparing(
                    { it.id.substringAfter('/') },
                    GradingPool.collectorOrder,
                )
    }
}

/** [quantity] copies of a [card] in the pool. */
class Candidate(val card: DeckCard, val quantity: Int) {
    init {
        require(quantity > 0) { "a candidate has at least one copy" }
    }
}
