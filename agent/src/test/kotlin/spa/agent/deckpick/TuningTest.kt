package spa.agent.deckpick

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.deckpick.DeckPickTestCards.dawnCleric
import spa.agent.deckpick.DeckPickTestCards.duskPaladin
import spa.agent.deckpick.DeckPickTestCards.graveRat
import spa.agent.deckpick.DeckPickTestCards.ironGolem
import spa.agent.deckpick.DeckPickTestCards.resolve
import spa.agent.deckpick.DeckPickTestCards.skyMarshal
import spa.agent.deckpick.DeckPickTestCards.sunKnight
import spa.agent.deckpick.DeckPickTestCards.tideCaller
import spa.agent.poolgrade.PoolGradeTestCards
import spa.agent.poolgrade.PoolGradeTestCards.baseline
import spa.agent.poolgrade.PoolGradeTestCards.fillerOne
import spa.agent.poolgrade.PoolGradeTestCards.fillerThree
import spa.agent.poolgrade.PoolGradeTestCards.flat
import spa.agent.poolgrade.Weights
import spa.core.cards.Printing
import spa.core.metrics.ColorPair

/** The tuning grid, its pooled mean, the tie rule, and the tuned weights file. */
class TuningTest {
    private val eps = 1e-12

    private fun w(synergy: Double, curve: Double, colour: Double) =
        Weights(synergy, curve, colour, cap = 1.0)

    @Test
    fun `the grid is every combination of the ticket's values with cap 1`() {
        val grid = Tuning.GRID

        assertEquals(45, grid.size)
        assertEquals(45, grid.toSet().size)
        assertEquals(w(0.0, 0.0, 0.0), grid.first())
        assertEquals(w(0.0, 0.0, 0.25), grid[1])
        assertEquals(w(0.0, 0.25, 0.0), grid[3])
        assertEquals(w(0.25, 0.0, 0.0), grid[9])
        assertEquals(w(1.0, 0.5, 0.5), grid.last())
        assertEquals(setOf(0.0, 0.25, 0.5, 0.75, 1.0), grid.map { it.synergy }.toSet())
        assertEquals(setOf(0.0, 0.25, 0.5), grid.map { it.curve }.toSet())
        assertEquals(setOf(0.0, 0.25, 0.5), grid.map { it.colour }.toSet())
        assertEquals(setOf(1.0), grid.map { it.cap }.toSet())
    }

    @Test
    fun `the best point has the highest overlap`() {
        val points =
            listOf(
                GridPoint(w(0.0, 0.0, 0.0), 0.70),
                GridPoint(w(0.5, 0.25, 0.0), 0.72),
                GridPoint(w(1.0, 0.0, 0.0), 0.71),
            )

        assertEquals(points[1], Tuning.best(points))
    }

    @Test
    fun `a tie goes to the smaller total weight, then to grid order`() {
        val points =
            listOf(
                GridPoint(w(0.5, 0.5, 0.0), 0.72),
                GridPoint(w(0.25, 0.25, 0.25), 0.72),
                GridPoint(w(0.5, 0.0, 0.25), 0.72),
                GridPoint(w(0.0, 0.0, 0.0), 0.70),
            )

        assertEquals(points[1], Tuning.best(points))
        assertEquals(points[1], Tuning.best(points.take(2).reversed() + points.drop(2)))
        val same = listOf(GridPoint(w(0.5, 0.0, 0.0), 0.7), GridPoint(w(0.0, 0.5, 0.0), 0.7))
        assertEquals(same[0], Tuning.best(same))
    }

    @Test
    fun `run pools every set's builds and asks each set for a grader per grid point`() {
        // Set a: small-sealed.csv's two builds, set grades as in DeckPickTest: 0.7 and 0.5. Set
        // b: one xyz build, every grade 2.0, N = 1 of 2 tied copies: 0.5. The graders ignore the
        // weights (every weight 0), so every point is the pooled mean (0.7 + 0.5 + 0.5) / 3, not
        // the mean of the sets' means, 0.55.
        val scores =
            mapOf(
                skyMarshal to 4.0,
                sunKnight to 3.0,
                dawnCleric to 3.0,
                tideCaller to 2.5,
                ironGolem to 2.5,
                graveRat to 2.0,
                duskPaladin to 1.0,
            )
        val requested = mutableListOf<Pair<String, Weights>>()
        val zero = PoolGradeTestCards.grader(baseline(), w(0.0, 0.0, 0.0))
        val a =
            TuningSet("a", small().builds, { p: Printing -> scores[p.card] }) {
                requested += "a" to it
                zero
            }
        val p = PoolGradeTestCards::printing
        val single =
            Build(
                "y",
                "0",
                ColorPair.WU,
                listOf(BuildCard(p(fillerOne), 1, 0), BuildCard(p(fillerThree), 0, 1)),
            )
        val b =
            TuningSet("b", listOf(single), flat(2.0)) {
                requested += "b" to it
                zero
            }

        val points = Tuning.run(listOf(a, b))

        assertEquals(Tuning.GRID, points.map { it.weights })
        points.forEach { assertEquals((0.7 + 0.5 + 0.5) / 3, it.overlap, eps) }
        assertEquals(Tuning.GRID.flatMap { listOf("a" to it, "b" to it) }, requested)
    }

    @Test
    fun `tuned weights are written in the four-key format of the default weights file`() {
        val weights = w(0.75, 0.25, 0.0)
        val text = TunedWeights.encode(weights)

        assertEquals(weights, Weights.parse(text))
        assertEquals(
            "{ \"synergy\": 0.75, \"curve\": 0.25, \"colour\": 0.0, \"cap\": 1.0 }\n",
            text,
        )
        assertEquals(
            "{ \"synergy\": 0.5, \"curve\": 0.25, \"colour\": 0.25, \"cap\": 1.0 }\n",
            TunedWeights.encode(Weights.DEFAULT),
        )
    }

    private fun small(): BuildSelection {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/spa/agent/deckpick/small-sealed.csv"))
        return stream.use { SealedBuilds.read(ByteArrayInputStream(it.readBytes()), resolve) }
    }
}
