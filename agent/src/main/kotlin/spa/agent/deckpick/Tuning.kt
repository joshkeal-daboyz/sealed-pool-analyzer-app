package spa.agent.deckpick

import spa.agent.poolgrade.PoolGrader
import spa.agent.poolgrade.Weights
import spa.core.cards.Printing

/**
 * One set's part of a tuning run: its [code], its kept [builds], its [setGrade]s, and a [grader]
 * for any weights.
 */
class TuningSet(
    val code: String,
    val builds: List<Build>,
    val setGrade: (Printing) -> Double?,
    val grader: (Weights) -> PoolGrader,
)

/** One grid combination's [weights] and its mean adjusted [overlap] over the pooled builds. */
data class GridPoint(val weights: Weights, val overlap: Double)

/** The search for the adjustment's weights (SPA-039). */
object Tuning {
    val SYNERGY = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
    val CURVE = listOf(0.0, 0.25, 0.5)
    val COLOUR = listOf(0.0, 0.25, 0.5)
    const val CAP = 1.0

    /** Every combination of [SYNERGY], [CURVE] and [COLOUR], with [CAP]; synergy varies slowest. */
    val GRID: List<Weights> = TODO()

    /**
     * Each [GRID] combination's mean adjusted overlap over every build of [sets] pooled, in grid
     * order.
     */
    fun run(sets: List<TuningSet>): List<GridPoint> = TODO()

    /** The point with the highest overlap; ties go to the smaller total weight, then grid order. */
    fun best(points: List<GridPoint>): GridPoint = TODO()
}
