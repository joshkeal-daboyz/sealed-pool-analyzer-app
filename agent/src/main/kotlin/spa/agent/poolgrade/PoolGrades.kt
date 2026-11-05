package spa.agent.poolgrade

import spa.agent.themes.Theme
import spa.agent.themes.ThemeCard
import spa.core.baseline.Baseline
import spa.core.cards.Pool
import spa.core.cards.Printing
import spa.core.metrics.ColorPair
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
    /** [pool]'s grades in [pair], with each card's set grade from [setGrade] (null: not listed). */
    fun grade(pool: Pool, pair: ColorPair, setGrade: (Printing) -> Double?): PairGrades =
        TODO("SPA-038")

    /** [grade] for every pair, highest mean first; ties keep [ColorPair] order. */
    fun gradeAll(pool: Pool, setGrade: (Printing) -> Double?): List<PairGrades> = TODO("SPA-038")
}
