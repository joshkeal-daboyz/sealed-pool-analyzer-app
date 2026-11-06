package spa.agent.deckbacktest.tune

import java.util.Locale

/** The objective of a deck weights file, from the weights text. */
fun interface TuneEvaluator {
    fun evaluate(weights: String): Double
}

/**
 * One evaluation of the search: in [round] (0 for the start), the knob [path] set to [value] (both
 * null for the start) gave [objective].
 */
data class TuneEvaluation(
    val round: Int,
    val path: String?,
    val value: Double?,
    val objective: Double,
) {
    /**
     * The report line: `round <r> <path> <value> objective <x>`, or `round 0 start objective <x>`.
     */
    fun line(): String {
        val what =
            if (path == null || value == null) "start" else "$path ${WeightsEdit.text(value)}"
        return String.format(Locale.ROOT, "round %d %s objective %.6f", round, what, objective)
    }
}

/** What a search found: both objectives, both weights texts, and the cost. */
data class TuneResult(
    val startObjective: Double,
    val finalObjective: Double,
    val startWeights: String,
    val weights: String,
    val evaluations: Int,
    val rounds: Int,
)

/**
 * Coordinate ascent over a [grid] (SPA-060). From the [start] weights and their objective, a round
 * visits the knobs in grid order; for each it tries every other value with the other knobs as they
 * stand, and moves to the value with the best objective if that beats the current one by more than
 * a billionth (a tie keeps the current value; among equal better values the first in grid order
 * wins). It stops when a round changes nothing or after [rounds] rounds. It is deterministic.
 *
 * [onEvaluation] hears every evaluation, in order, the start's included. [onBest] gets the weights
 * text for the start and again after every move, so a caller that writes it always holds the best
 * so far.
 */
class CoordinateAscent(
    private val grid: DeckGrid,
    private val start: String,
    private val rounds: Int,
    private val evaluator: TuneEvaluator,
    private val onEvaluation: (TuneEvaluation) -> Unit = {},
    private val onBest: (String) -> Unit = {},
) {
    init {
        require(rounds >= 1) { "rounds must be 1 or more" }
    }

    /** Runs the search. */
    fun run(): TuneResult {
        val climb = Climb()
        var round = 0
        var changed = true
        while (changed && round < rounds) {
            round++
            changed = climb.round(round)
        }
        return TuneResult(
            climb.startObjective,
            climb.objective,
            climb.startText,
            climb.best,
            climb.evaluations,
            round,
        )
    }

    /** The state of one search: the knobs moved so far and the best weights found. */
    private inner class Climb {
        private val choice = linkedMapOf<String, Double>()
        val startText = WeightsEdit.apply(start, choice)
        val startObjective = evaluator.evaluate(startText)
        var objective = startObjective
        var best = startText
        var evaluations = 1

        init {
            onEvaluation(TuneEvaluation(0, null, null, startObjective))
            onBest(startText)
        }

        /** Visits every knob once; true when one of them moved. */
        fun round(round: Int): Boolean = grid.knobs.map { visit(round, it) }.any { it }

        private fun visit(round: Int, knob: Knob): Boolean {
            val current = choice[knob.path] ?: WeightsEdit.read(start, knob.path)
            var bestValue: Double? = null
            var bestObjective = objective
            for (value in knob.values.filter { it != current }) {
                val result =
                    evaluator.evaluate(WeightsEdit.apply(start, choice + (knob.path to value)))
                evaluations++
                onEvaluation(TuneEvaluation(round, knob.path, value, result))
                if (result > bestObjective + EPSILON) {
                    bestValue = value
                    bestObjective = result
                }
            }
            if (bestValue == null) return false
            choice[knob.path] = bestValue
            objective = bestObjective
            best = WeightsEdit.apply(start, choice)
            onBest(best)
            return true
        }
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
