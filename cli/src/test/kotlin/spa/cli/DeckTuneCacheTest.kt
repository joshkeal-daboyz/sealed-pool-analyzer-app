package spa.cli

import java.nio.file.Path
import java.util.concurrent.ForkJoinPool
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import spa.agent.deckbacktest.tune.WeightsEdit

/**
 * SPA-060 criterion 5: the evaluator gives the same objective, and the same report lines, with and
 * without its caches, and a change that cannot affect a deck does not build it again.
 */
class DeckTuneCacheTest {
    @TempDir lateinit var temp: Path

    private val pool = ForkJoinPool(2)

    @AfterTest fun shutDown() = pool.shutdown()

    private val sets by lazy {
        val data = BacktestSyn.data(temp.resolve("data"))
        val session = BuildSession.open(DataDir(data), "syn")
        val file =
            writeFile(
                temp,
                "sealed.csv",
                BacktestSyn.csv(*(BacktestSyn.KEPT + BacktestSyn.SKIPPED).toTypedArray()),
            )
        listOf(TuneSet("syn", session, DeckBacktestCommand.read(Path.of(file), session).builds))
    }

    private fun evaluator(cache: Boolean) = DeckTuneEvaluator(sets, pool, cache = cache)

    private val base = BacktestSyn.WEIGHTS

    /** The base weights with `weights.curve` changed. */
    private val curve = WeightsEdit.apply(base, mapOf("weights.curve" to 0.5))

    /** The base weights with `params.creatures.minCreatures` changed. */
    private val creatures = WeightsEdit.apply(base, mapOf("params.creatures.minCreatures" to 10.0))

    /** The base weights with the three-colour rule on: only the configurations change. */
    private val triples =
        base.replace(
            "\"configs\": { \"pairs\": { \"enabled\": true } }",
            "\"configs\": { \"pairs\": { \"enabled\": true }, " +
                "\"triples\": { \"enabled\": true, \"tripleSplash\": false } }",
        )

    @Test
    fun `the objective and the report lines are the same with and without caching`() {
        val cached = evaluator(cache = true)
        val plain = evaluator(cache = false)

        // The cached evaluator sees the base, then each weights file that differs in one knob,
        // then the base again, so every cache is warm for the later ones.
        for (weights in listOf(base, curve, creatures, triples, base, triples)) {
            assertEquals(plain.evaluate(weights), cached.evaluate(weights), weights)
            assertEquals(plain.report(weights), cached.report(weights), weights)
        }
    }

    @Test
    fun `a change to the configurations builds only the decks of the new configurations`() {
        val cached = evaluator(cache = true)
        val plain = evaluator(cache = false)
        cached.evaluate(base)
        val before = cached.searches
        plain.evaluate(triples)

        cached.evaluate(triples)

        val rebuilt = cached.searches - before
        assertTrue(rebuilt > 0, "the three-colour decks are new")
        assertTrue(rebuilt < plain.searches, "$rebuilt of ${plain.searches}")
    }

    @Test
    fun `weights already evaluated are answered without building a deck`() {
        val cached = evaluator(cache = true)
        val first = cached.evaluate(curve)
        val searches = cached.searches

        assertEquals(first, cached.evaluate(curve))
        assertEquals(searches, cached.searches)
    }

    @Test
    fun `without caching every evaluation builds every deck again`() {
        val plain = evaluator(cache = false)
        plain.evaluate(base)
        val once = plain.searches

        plain.evaluate(base)

        assertEquals(2 * once, plain.searches)
    }
}
