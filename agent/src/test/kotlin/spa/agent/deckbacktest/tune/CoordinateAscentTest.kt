package spa.agent.deckbacktest.tune

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.build.DeckWeights

/**
 * SPA-060 criterion 1: the search, with a fake evaluator in place of the backtest. A fake reads the
 * knobs it cares about from the weights text it is given.
 */
class CoordinateAscentTest {
    private fun read(text: String, path: String): Double = checkNotNull(WeightsEdit.read(text, path))

    private fun grid(vararg knobs: Pair<String, List<Number>>) =
        DeckGrid.parse(gridText(*knobs), START)

    private class Run(
        val result: TuneResult,
        val lines: List<TuneEvaluation>,
        val files: List<String>,
    )

    private fun search(grid: DeckGrid, rounds: Int = 3, evaluator: TuneEvaluator): Run {
        val lines = mutableListOf<TuneEvaluation>()
        val files = mutableListOf<String>()
        val result =
            CoordinateAscent(grid, START, rounds, evaluator, { lines += it }, { files += it }).run()
        return Run(result, lines, files)
    }

    private fun curve(run: Run): Double = read(run.result.weights, "weights.curve")

    @Test
    fun `a knob moves to a strictly better value`() {
        val run =
            search(grid("weights.curve" to listOf(0, 1, 2, 3))) {
                -abs(read(it, "weights.curve") - 2.0)
            }

        assertEquals(2.0, curve(run))
        assertEquals(-1.0, run.result.startObjective)
        assertEquals(0.0, run.result.finalObjective)
    }

    @Test
    fun `a tie keeps the current value`() {
        val run = search(grid("weights.curve" to listOf(0, 1, 2))) { 5.0 }

        assertEquals(1.0, curve(run))
        assertEquals(run.result.startWeights, run.result.weights)
    }

    @Test
    fun `a gain of less than 1e-9 is a tie`() {
        val run =
            search(grid("weights.curve" to listOf(0, 1, 2))) {
                if (read(it, "weights.curve") == 2.0) 5.0 + 1e-12 else 5.0
            }

        assertEquals(1.0, curve(run))
    }

    @Test
    fun `among equal better values the first in grid order wins`() {
        fun objective(text: String) = if (read(text, "weights.curve") == 1.0) 1.0 else 5.0

        val up = search(grid("weights.curve" to listOf(0, 1, 2)), evaluator = ::objective)
        val down = search(grid("weights.curve" to listOf(2, 1, 0)), evaluator = ::objective)

        assertEquals(0.0, curve(up))
        assertEquals(2.0, curve(down))
    }

    @Test
    fun `a better value later in the grid beats an earlier better one`() {
        val run =
            search(grid("weights.curve" to listOf(0, 1, 2, 5))) { read(it, "weights.curve") }

        assertEquals(5.0, curve(run))
    }

    @Test
    fun `it tries every value but the current one, knobs in grid order, others as they stand`() {
        val run =
            search(
                grid("weights.creatures" to listOf(0, 1, 2), "weights.removal" to listOf(1, 3)),
                rounds = 1,
            ) {
                read(it, "weights.creatures") * 10 + read(it, "weights.removal")
            }

        assertEquals(
            listOf(
                Triple(0, null, null),
                Triple(1, "weights.creatures", 0.0),
                Triple(1, "weights.creatures", 2.0),
                Triple(1, "weights.removal", 3.0),
            ),
            run.lines.map { Triple(it.round, it.path, it.value) },
        )
        assertEquals(4, run.result.evaluations)
        assertEquals(2.0, read(run.result.weights, "weights.creatures"))
        assertEquals(3.0, read(run.result.weights, "weights.removal"))
        assertEquals(23.0, run.lines.last().objective)
        assertEquals(23.0, run.result.finalObjective)
    }

    @Test
    fun `it stops after a round with no change`() {
        // Round 1 moves the knob; round 2 changes nothing; round 3 is never run.
        val run =
            search(grid("weights.curve" to listOf(0, 1, 2)), rounds = 5) {
                -abs(read(it, "weights.curve") - 2.0)
            }

        assertEquals(2, run.lines.maxOf { it.round })
        assertEquals(1 + 2 + 2, run.result.evaluations)
        assertEquals(2, run.result.rounds)
    }

    @Test
    fun `it stops after the given number of rounds`() {
        // Every evaluation beats the one before it, so every round moves.
        var counter = 0.0
        val run = search(grid("weights.curve" to listOf(0, 1, 2)), rounds = 3) { counter++ }

        assertEquals(3, run.lines.maxOf { it.round })
        assertEquals(3, run.result.rounds)
        assertEquals(1 + 3 * 2, run.result.evaluations)
    }

    @Test
    fun `the same inputs always give the same path`() {
        fun once(): Run =
            search(
                grid(
                    "weights.curve" to listOf(0, 0.5, 1, 2),
                    "params.creatures.minCreatures" to listOf(12, 13, 14, 15),
                    "lands.lowCurve" to listOf(2.4, 2.6, 2.8),
                )
            ) {
                -abs(read(it, "weights.curve") - 0.5) -
                    abs(read(it, "params.creatures.minCreatures") - 13.0) -
                    abs(read(it, "lands.lowCurve") - 2.8)
            }

        val first = once()
        val second = once()

        assertEquals(first.lines, second.lines)
        assertEquals(first.files, second.files)
        assertEquals(first.result, second.result)
    }

    @Test
    fun `the weights are written for the start and again after each move, and each parses`() {
        val run =
            search(
                grid("weights.creatures" to listOf(0, 1, 2), "weights.removal" to listOf(1, 3)),
                rounds = 1,
            ) {
                read(it, "weights.creatures") * 10 + read(it, "weights.removal")
            }

        // The start, then the creatures move, then the removal move.
        assertEquals(3, run.files.size)
        run.files.forEach { DeckWeights.parse(it) }
        assertEquals(1.0, read(run.files[0], "weights.creatures"))
        assertEquals(2.0, read(run.files[1], "weights.creatures"))
        assertEquals(1.0, read(run.files[1], "weights.removal"))
        assertEquals(3.0, read(run.files[2], "weights.removal"))
        assertEquals(run.result.weights, run.files.last())
    }

    @Test
    fun `a run with no move still writes the start weights once`() {
        val run = search(grid("weights.curve" to listOf(0, 1, 2))) { 1.0 }

        assertEquals(listOf(run.result.startWeights), run.files)
        DeckWeights.parse(run.files.single())
    }

    @Test
    fun `a knob the start file lacks is created and tried at every value`() {
        val run =
            search(grid("configs.fourColour.minFixing" to listOf(2, 4)), rounds = 1) { 1.0 }

        assertEquals(3, run.result.evaluations)
    }
}
