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
// The grid's values are the ticket's.
@Suppress("MagicNumber")
object Tuning {
    val SYNERGY = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
    val CURVE = listOf(0.0, 0.25, 0.5)
    val COLOUR = listOf(0.0, 0.25, 0.5)
    const val CAP = 1.0

    /** Every combination of [SYNERGY], [CURVE] and [COLOUR], with [CAP]; synergy varies slowest. */
    val GRID: List<Weights> = SYNERGY.flatMap { synergy ->
        CURVE.flatMap { curve -> COLOUR.map { colour -> Weights(synergy, curve, colour, CAP) } }
    }

    /**
     * Each [GRID] combination's mean adjusted overlap over every build of [sets] pooled, in grid
     * order.
     */
    fun run(sets: List<TuningSet>): List<GridPoint> {
        val builds = sets.sumOf { it.builds.size }
        require(builds > 0) { "no build to tune on" }
        return GRID.map { weights ->
            val total = sets.sumOf { set ->
                val grader = set.grader(weights)
                set.builds.sumOf { DeckPick.adjustedOverlap(it, grader, set.setGrade) }
            }
            GridPoint(weights, total / builds)
        }
    }

    /** The point with the highest overlap; ties go to the smaller total weight, then grid order. */
    fun best(points: List<GridPoint>): GridPoint {
        require(points.isNotEmpty()) { "no grid point" }
        val order =
            compareByDescending<IndexedValue<GridPoint>> { it.value.overlap }
                .thenBy { it.value.weights.let { w -> w.synergy + w.curve + w.colour } }
                .thenBy { it.index }
        return points.withIndex().sortedWith(order).first().value
    }
}
