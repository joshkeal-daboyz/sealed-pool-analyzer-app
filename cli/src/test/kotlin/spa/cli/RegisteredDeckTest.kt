package spa.cli

import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.build.DeckCard
import spa.agent.deckbacktest.BacktestBuild
import spa.agent.deckbacktest.BacktestSkip
import spa.agent.deckbacktest.score.RegisteredDeck
import spa.agent.deckbacktest.score.ScoreOutcome
import spa.agent.deckbacktest.score.ScoreSkip

/**
 * SPA-061 criterion 3: the deck a player registered, scored with the builder's parts, on set `syn`
 * and the hand-built builds of [ScoreSyn].
 */
class RegisteredDeckTest {
    @TempDir lateinit var temp: Path

    private val data by lazy { BacktestSyn.data(temp.resolve("data")) }

    private fun session(weights: String) =
        BuildSession.open(DataDir(data), "syn", data.resolve("weights/$weights"))

    private val builder by lazy { session("deck-default.json") }
    private val baseline by lazy { session("deck-baseline.json") }

    private fun selection(vararg rows: String) =
        DeckBacktestCommand.read(
            Path.of(writeFile(temp, "sealed.csv", BacktestSyn.csv(*rows))),
            builder,
            0.0,
        )

    private fun scored(build: BacktestBuild, vararg sessions: BuildSession): ScoreOutcome =
        RegisteredDeck.score(build, sessions.map { ScoreBacktestCommand.scorer(it, build) })

    private fun only(row: String): BacktestBuild = selection(row).builds.single()

    @Test
    fun `with the baseline weights the score is the sum of the grades of the registered spells`() {
        val builds = selection(*ScoreSyn.SCORED.toTypedArray()).builds

        val scores = builds.map { (scored(it, baseline) as ScoreOutcome.Scored).scores.single() }

        assertEquals(ScoreSyn.BASELINE_SCORES, scores)
    }

    @Test
    fun `with the default parts the score is the builder's evaluate on those same cards`() {
        val build = only(BacktestSyn.TWO)
        val setup = builder.setup(builder.candidates(build.pool()), build.config!!)
        val copies =
            mapOf(
                "Air Elemental" to 5,
                "Counterspell" to 4,
                "Delver of Secrets" to 3,
                "Doom Blade" to 5,
                "Gravedigger" to 4,
                "Murderous Rider" to 2,
            )
        val spells: List<DeckCard> = copies.flatMap { (name, n) ->
            val card = setup.spells.first { it.card.name.startsWith(name) }.card
            List(n) { card }
        }

        val expected = builder.builder.evaluate(setup, spells, 17).total
        val outcome = scored(build, builder) as ScoreOutcome.Scored

        assertEquals(listOf(expected), outcome.scores)
    }

    @Test
    fun `two scorers give two scores in their order`() {
        val build = only(ScoreSyn.D)

        val both = scored(build, baseline, builder) as ScoreOutcome.Scored

        assertEquals(92.0, both.scores[0])
        assertTrue(both.scores[1] != both.scores[0], both.scores.toString())
    }

    @Test
    fun `a splash colour that is also a main colour is no-config`() {
        assertEquals(
            ScoreOutcome.Skipped(ScoreSkip.NO_CONFIG),
            scored(only(ScoreSyn.NO_CONFIG), builder),
        )
    }

    @Test
    fun `a registered deck of 39 cards is not-40`() {
        assertEquals(ScoreOutcome.Skipped(ScoreSkip.NOT_40), scored(only(ScoreSyn.NOT_40), builder))
    }

    @Test
    fun `a registered spell outside the player's colours is off-colour`() {
        assertEquals(
            ScoreOutcome.Skipped(ScoreSkip.OFF_COLOUR),
            scored(only(ScoreSyn.OFF_COLOUR), builder),
        )
    }

    @Test
    fun `a pool with no card inside the colours is no-choice, from the reader`() {
        val selection = selection(ScoreSyn.NO_CHOICE)

        assertEquals(emptyList(), selection.builds)
        assertEquals(1, selection.skipped[BacktestSkip.NO_CHOICE])
    }
}
