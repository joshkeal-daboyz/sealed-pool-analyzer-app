package spa.agent.poolgrade

import spa.agent.themes.Theme
import spa.agent.themes.ThemeCard
import spa.core.baseline.Baseline
import spa.core.cards.Card
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.metrics.ColorPair
import spa.core.metrics.PoolMetrics

/** One part's move of a card's grade, in grade points, and the reason it gives for it. */
interface Part {
    /** The move, already weighted. */
    val value: Double

    /** The part's one reason line, or null when [value] is 0. */
    val line: String?
}

/**
 * One rule that adjusts a candidate card's set grade for its pool and pair (G-005 Constraints: each
 * rule its own class behind this interface). [PoolGrader] sums the parts of its list in order, and
 * the printer prints each part's own [Part.line].
 */
interface CardPart {
    /** The part's key in a weights file ([Weights]). */
    val key: String

    /** [card]'s part in [context]'s pool and pair, at [weight] (0: the part is off). */
    fun score(card: Card, context: PairContext, weight: Double): Part
}

/** The card parts [PoolGrader] uses by default, in the order they are summed and printed. */
object CardParts {
    val ALL: List<CardPart> =
        listOf(SynergyCardPart, CurveCardPart, ColourCardPart, TagSynergyCardPart)
}

/**
 * What every part may read for one grader: the set's cards, kept [themes], [keywords] and
 * [baseline], and the set's tags, loaded only when a part asks for them.
 */
class GradeInputs(
    val setCards: List<ThemeCard>,
    val themes: List<Theme>,
    val keywords: Map<String, List<String>>,
    val baseline: Baseline,
    private val tags: Lazy<TagInputs>?,
) {
    private val memo = mutableMapOf<Any, Any?>()

    /** The set's tags; an [IllegalStateException] when the grader was given none. */
    val tagInputs: TagInputs
        get() = checkNotNull(tags) { "the tagSynergy part needs a tag file" }.value

    /** The baseline mean at [pointer]. */
    fun mean(pointer: String): Double =
        requireNotNull(baseline.stats[pointer]) { "The baseline has no $pointer." }.mean

    /** [compute]'s value for [key], computed once per grader. */
    @Suppress("UNCHECKED_CAST")
    fun <T> remember(key: Any, compute: () -> T): T = memo.getOrPut(key) { compute() } as T
}

/**
 * One pool in one [pair], as the parts see it: the pool's [candidates] (its nonland copies castable
 * in the pair), their [copies], the pool's [metrics], and the set cards [fitting] the pair.
 */
class PairContext(
    val inputs: GradeInputs,
    val pool: Pool,
    val pair: ColorPair,
    val candidates: List<PoolEntry>,
    val metrics: PoolMetrics,
) {
    private val memo = mutableMapOf<Any, Any?>()

    /** The candidates' copies, N. */
    val copies: Int = candidates.sumOf { it.count }

    /** The set's cards that fit [pair]. */
    val fitting: List<ThemeCard> by lazy { inputs.setCards.filter { fits(it.card, pair) } }

    /** [compute]'s value for [key], computed once for this pool and pair. */
    @Suppress("UNCHECKED_CAST")
    fun <T> remember(key: Any, compute: () -> T): T = memo.getOrPut(key) { compute() } as T

    companion object {
        /** A nonland card castable in [pair]: its colours are a subset of the pair's. */
        fun fits(card: Card, pair: ColorPair): Boolean =
            !card.isLand && pair.colors.containsAll(card.colors)
    }
}
