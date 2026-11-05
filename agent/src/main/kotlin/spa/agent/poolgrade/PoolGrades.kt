package spa.agent.poolgrade

import spa.agent.themes.Theme
import spa.agent.themes.ThemeCard
import spa.core.baseline.Baseline
import spa.core.cards.Pool
import spa.core.cards.Printing
import spa.core.metrics.ColorPair
import spa.core.metrics.MetricEngine

/**
 * One distinct candidate card of a pair: [quantity] copies of [printing], its [setGrade], its
 * [parts] in the grader's list order, and its [adjusted] grade, unrounded.
 */
data class CardAdjustment(
    val printing: Printing,
    val quantity: Int,
    val setGrade: Double,
    val parts: List<Part>,
    val adjusted: Double,
) {
    /** The adjusted grade minus the set grade. */
    val change: Double
        get() = adjusted - setGrade

    /** The synergy part ([SynergyCardPart]); the grader's list must hold it. */
    val synergy: SynergyPart
        get() = parts.filterIsInstance<SynergyPart>().first()

    /** The curve part ([CurveCardPart]); the grader's list must hold it. */
    val curve: CurvePart
        get() = parts.filterIsInstance<CurvePart>().first()

    /** The colour part ([ColourCardPart]); the grader's list must hold it. */
    val colour: ColourPart
        get() = parts.filterIsInstance<ColourPart>().first()
}

/**
 * A pool's grades in one [pair]: [copies] candidate copies, the [mean] adjusted grade of the top
 * [TOP_COPIES] of them, and the distinct graded candidates, highest adjusted first, ties by name.
 */
data class PairGrades(
    val pair: ColorPair,
    val copies: Int,
    val mean: Double,
    val cards: List<CardAdjustment>,
) {
    companion object {
        /** How many of a pair's best copies its mean is taken over. */
        const val TOP_COPIES = 23
    }
}

/**
 * Adjusts set grades for one pool by deterministic rules (G-004 layer 3; SPA-038's Background),
 * each a [CardPart] of [parts], weighted by key from [weights]. No model or network call.
 *
 * @param setCards the set's sealed-pool cards ([spa.agent.grader.GradingPool]), with keywords.
 * @param themes the set's kept themes ([spa.agent.themes.SetThemes]), in their order.
 * @param keywords Scryfall keywords by oracle id, for the pool's cards.
 * @param baseline the set's baseline.
 * @param weights each part's weight, and the cap on their sum.
 * @param tags the set's tags, for [TagSynergyCardPart]; read only when its weight is above 0.
 * @param parts the parts, summed and printed in this order.
 */
// SPA-038's five inputs, then the two optional seams (tags, parts), each defaulted.
@Suppress("LongParameterList")
class PoolGrader(
    setCards: List<ThemeCard>,
    themes: List<Theme>,
    keywords: Map<String, List<String>>,
    baseline: Baseline,
    private val weights: Weights,
    tags: Lazy<TagInputs>? = null,
    private val parts: List<CardPart> = CardParts.ALL,
) {
    private val inputs = GradeInputs(setCards, themes, keywords, baseline, tags)

    /** [pool]'s grades in [pair], with each card's set grade from [setGrade] (null: not listed). */
    fun grade(pool: Pool, pair: ColorPair, setGrade: (Printing) -> Double?): PairGrades {
        val metrics = MetricEngine.compute(pool)
        val candidates =
            pool.entries.filter { it.count > 0 && PairContext.fits(it.printing.card, pair) }
        val context = PairContext(inputs, pool, pair, candidates, metrics)
        val cards =
            candidates
                .groupBy { it.printing.card.oracleId }
                .values
                .mapNotNull { same ->
                    val printing = same.first().printing
                    setGrade(printing)?.let { grade ->
                        val scored = parts.map { it.score(printing.card, context, weights[it.key]) }
                        adjust(printing, same.sumOf { it.count }, grade, scored)
                    }
                }
                .sortedWith(
                    compareByDescending<CardAdjustment> { it.adjusted }
                        .thenBy { it.printing.card.name }
                )
        val best =
            cards
                .flatMap { card -> List(card.quantity) { card.adjusted } }
                .take(PairGrades.TOP_COPIES)
        return PairGrades(pair, context.copies, if (best.isEmpty()) 0.0 else best.average(), cards)
    }

    /** [grade] for every pair, highest mean first; ties keep [ColorPair] order. */
    fun gradeAll(pool: Pool, setGrade: (Printing) -> Double?): List<PairGrades> =
        ColorPair.entries.map { grade(pool, it, setGrade) }.sortedByDescending { it.mean }

    /** [setGrade] moved by the [parts]' sum, the sum capped and the result kept within 0 to 5. */
    private fun adjust(
        printing: Printing,
        quantity: Int,
        setGrade: Double,
        parts: List<Part>,
    ): CardAdjustment {
        val move = parts.sumOf { it.value }.coerceIn(-weights.cap, weights.cap)
        val adjusted = (setGrade + move).coerceIn(MIN_GRADE, MAX_GRADE)
        return CardAdjustment(printing, quantity, setGrade, parts, adjusted)
    }

    private companion object {
        const val MIN_GRADE = 0.0
        const val MAX_GRADE = 5.0
    }
}
