package spa.agent.poolgrade

import kotlin.math.floor
import kotlin.math.max
import spa.agent.themes.Theme
import spa.agent.themes.ThemeCard
import spa.agent.themes.ThemeRules
import spa.core.baseline.Baseline
import spa.core.cards.Card
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Printing
import spa.core.metrics.ColorBucket
import spa.core.metrics.ColorPair
import spa.core.metrics.Curve
import spa.core.metrics.MetricEngine
import spa.core.metrics.PoolMetrics
import spa.core.text.Color

/** One rule's move of a card's grade, in grade points. */
sealed interface Part {
    val value: Double
}

/**
 * The synergy part: [partners] candidate copies are partners of the card, against [expected] in a
 * typical pool, through the card's [themes] (names, in the set's theme order). No themes is 0.
 */
data class SynergyPart(
    override val value: Double,
    val partners: Int,
    val expected: Double,
    val themes: List<String>,
) : Part

/**
 * The curve part: the pair has [count] cards in the card's mana value bucket [manaValue] (0 to 6,
 * or 7 for 7 or more), against [typical] in the set's baseline.
 */
data class CurvePart(
    override val value: Double,
    val manaValue: Int,
    val count: Int,
    val typical: Double,
) : Part

/**
 * The colour part: the card's shallowest colour [colour] has [playables] playables in the pool,
 * against [typical] in the baseline. A colourless card has a null [colour] and value 0.
 */
data class ColourPart(
    override val value: Double,
    val colour: Color?,
    val playables: Int,
    val typical: Double,
) : Part

/**
 * One distinct candidate card of a pair: [quantity] copies of [printing], its [setGrade], its three
 * parts, and its [adjusted] grade, unrounded.
 */
data class CardAdjustment(
    val printing: Printing,
    val quantity: Int,
    val setGrade: Double,
    val synergy: SynergyPart,
    val curve: CurvePart,
    val colour: ColourPart,
    val adjusted: Double,
) {
    /** The adjusted grade minus the set grade. */
    val change: Double
        get() = adjusted - setGrade

    /** The three parts in the order synergy, curve, colour. */
    val parts: List<Part>
        get() = listOf(synergy, curve, colour)
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
 * Adjusts set grades for one pool by deterministic rules (G-004 layer 3; SPA-038's Background). No
 * model or network call.
 *
 * @param setCards the set's sealed-pool cards ([spa.agent.grader.GradingPool]), with keywords.
 * @param themes the set's kept themes ([spa.agent.themes.SetThemes]), in their order.
 * @param keywords Scryfall keywords by oracle id, for the pool's cards.
 * @param baseline the set's baseline.
 * @param weights the rules' weights.
 */
class PoolGrader(
    private val setCards: List<ThemeCard>,
    private val themes: List<Theme>,
    private val keywords: Map<String, List<String>>,
    private val baseline: Baseline,
    private val weights: Weights,
) {
    private val roles = mutableMapOf<String, Roles>()

    /** [pool]'s grades in [pair], with each card's set grade from [setGrade] (null: not listed). */
    fun grade(pool: Pool, pair: ColorPair, setGrade: (Printing) -> Double?): PairGrades {
        val metrics = MetricEngine.compute(pool)
        val candidates = pool.entries.filter { it.count > 0 && fits(it.printing.card, pair) }
        val copies = candidates.sumOf { it.count }
        val fitting = setCards.filter { fits(it.card, pair) }
        val cards =
            candidates
                .groupBy { it.printing.card.oracleId }
                .values
                .mapNotNull { same ->
                    val printing = same.first().printing
                    setGrade(printing)?.let { grade ->
                        val card = printing.card
                        val parts =
                            Triple(
                                synergy(card, candidates, copies, fitting),
                                curve(card, pair, metrics),
                                colour(card, metrics),
                            )
                        adjust(printing, same.sumOf { it.count }, grade, parts)
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
        return PairGrades(pair, copies, if (best.isEmpty()) 0.0 else best.average(), cards)
    }

    /** [grade] for every pair, highest mean first; ties keep [ColorPair] order. */
    fun gradeAll(pool: Pool, setGrade: (Printing) -> Double?): List<PairGrades> =
        ColorPair.entries.map { grade(pool, it, setGrade) }.sortedByDescending { it.mean }

    /** [setGrade] moved by the [parts]' sum, the sum capped and the result kept within 0 to 5. */
    private fun adjust(
        printing: Printing,
        quantity: Int,
        setGrade: Double,
        parts: Triple<SynergyPart, CurvePart, ColourPart>,
    ): CardAdjustment {
        val (synergy, curve, colour) = parts
        val move = (synergy.value + curve.value + colour.value).coerceIn(-weights.cap, weights.cap)
        val adjusted = (setGrade + move).coerceIn(MIN_GRADE, MAX_GRADE)
        return CardAdjustment(printing, quantity, setGrade, synergy, curve, colour, adjusted)
    }

    /**
     * The synergy part of [card]: its partners among the [copies] [candidates] other than one copy
     * of itself, against the share of partners among the [fitting] set cards other than itself.
     */
    private fun synergy(
        card: Card,
        candidates: List<PoolEntry>,
        copies: Int,
        fitting: List<ThemeCard>,
    ): SynergyPart {
        val own = rolesOf(card)
        if (own.isEmpty) return SynergyPart(0.0, 0, 0.0, emptyList())
        val partners = candidates.sumOf { entry ->
            val other = entry.printing.card
            val count = if (other.oracleId == card.oracleId) entry.count - 1 else entry.count
            if (own.partners(rolesOf(other))) count else 0
        }
        val others = fitting.filter { it.card.oracleId != card.oracleId }
        val share =
            if (others.isEmpty()) 0.0
            else others.count { own.partners(rolesOf(it.card)) }.toDouble() / others.size
        val expected = share * (copies - 1)
        val value = weights.synergy * ratio(partners - expected, expected)
        val names = themes.filter { it in own.has || it in own.refers }.map { it.name }
        return SynergyPart(value, partners, expected, names)
    }

    private fun curve(card: Card, pair: ColorPair, metrics: PoolMetrics): CurvePart {
        val bucket = floor(card.manaValue).toInt().coerceIn(0, Curve.TOP)
        val count = metrics.pairs.getValue(pair).curve.buckets[bucket].total
        val prefix = "/pairs/${pair.name}/curve/buckets/$bucket"
        val typical = mean("$prefix/creatures") + mean("$prefix/noncreatures")
        val value = weights.curve * ratio(typical - count, typical)
        return CurvePart(value, bucket, count, typical)
    }

    private fun colour(card: Card, metrics: PoolMetrics): ColourPart {
        val depths =
            Color.entries
                .filter { it in card.colors }
                .map { colour ->
                    val bucket = metrics.byColor.getValue(ColorBucket.valueOf(colour.name))
                    val typical = mean("/byColor/${colour.name}/playables/total")
                    ColourPart(
                        weights.colour * ratio(bucket.playables.total - typical, typical),
                        colour,
                        bucket.playables.total,
                        typical,
                    )
                }
        // The weight is not negative, so the smallest part is the smallest r(x).
        return depths.minByOrNull { it.value } ?: ColourPart(0.0, null, 0, 0.0)
    }

    private fun mean(pointer: String): Double =
        requireNotNull(baseline.stats[pointer]) { "The baseline has no $pointer." }.mean

    private fun rolesOf(card: Card): Roles =
        roles.getOrPut(card.oracleId) {
            val themed = ThemeCard(card, card.rarity, keywords[card.oracleId].orEmpty())
            Roles(
                themes.filter { ThemeRules.has(themed, it) }.toSet(),
                themes.filter { ThemeRules.refersTo(themed, it) }.toSet(),
            )
        }

    /** The kept themes a card has, and those it refers to. */
    private class Roles(val has: Set<Theme>, val refers: Set<Theme>) {
        val isEmpty: Boolean
            get() = has.isEmpty() && refers.isEmpty()

        /**
         * Whether a card with [other] roles is a partner of this one: this refers to a theme it
         * has, or this has a theme it has or refers to.
         */
        fun partners(other: Roles): Boolean =
            refers.any { it in other.has } || has.any { it in other.has || it in other.refers }
    }

    private companion object {
        const val MIN_GRADE = 0.0
        const val MAX_GRADE = 5.0

        /** A nonland card castable in [pair]: its colours are a subset of the pair's. */
        fun fits(card: Card, pair: ColorPair): Boolean =
            !card.isLand && pair.colors.containsAll(card.colors)

        /** [difference] / max([base], 1), clamped to -1 and 1. */
        fun ratio(difference: Double, base: Double): Double =
            (difference / max(base, 1.0)).coerceIn(-1.0, 1.0)
    }
}
