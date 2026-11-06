package spa.agent.deckbacktest.score

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.build.BuildTestCards
import spa.agent.build.DeckBuilder
import spa.agent.build.DeckCard
import spa.agent.deckbacktest.BacktestBuild
import spa.agent.deckbacktest.BacktestSelection
import spa.agent.deckbacktest.BacktestSkip
import spa.agent.deckbacktest.DeckShape
import spa.agent.deckpick.BuildCard
import spa.agent.poolgrade.PoolGradeTestCards
import spa.core.text.Color

/**
 * SPA-061 criterion 3 without the CLI: the registered deck of a hand-built build scored by the
 * grades part alone, and the skip reasons, on [BuildTestCards].
 */
class RegisteredDeckTest {
    private val strong = BuildTestCards.spell("Strong", BuildTestCards.W, "{1}{W}", 2, grade = 3.0)
    private val weak = BuildTestCards.spell("Weak", BuildTestCards.W, "{1}{W}", 2, grade = 2.0)
    private val bolt = BuildTestCards.spell("Bolt", BuildTestCards.R, "{R}", 1, grade = 4.0)
    private val plains = BuildTestCards.land("Plains", setOf(Color.W))

    private val setup =
        BuildTestCards.setup(BuildTestCards.candidates(listOf(strong, weak), quantity = 20))

    private fun scorer(grades: Double) =
        DeckScorer(DeckBuilder(BuildTestCards.weights("grades" to grades))) { setup }

    private fun entry(card: DeckCard, deck: Int) =
        BuildCard(PoolGradeTestCards.printing(card.card), deck, 0)

    private fun build(
        vararg cards: BuildCard,
        main: Set<Color> = setOf(Color.W),
        splash: Set<Color> = emptySet(),
    ) = BacktestBuild("d", "0", main, splash, cards.toList(), 0.5)

    private fun registered(lands: Int = 17) =
        build(entry(strong, 12), entry(weak, 11), entry(plains, lands))

    @Test
    fun `the score is the builder's evaluate of the registered spells, one per scorer`() {
        val outcome = RegisteredDeck.score(registered(), listOf(scorer(1.0), scorer(2.0)))

        assertEquals(ScoreOutcome.Scored(listOf(58.0, 116.0)), outcome)
    }

    @Test
    fun `a splash colour that is also a main colour is no-config`() {
        val outcome =
            RegisteredDeck.score(
                build(entry(strong, 40), splash = setOf(Color.W)),
                listOf(scorer(1.0)),
            )

        assertEquals(ScoreOutcome.Skipped(ScoreSkip.NO_CONFIG), outcome)
    }

    @Test
    fun `a deck that is not 40 cards is not-40`() {
        val outcome = RegisteredDeck.score(registered(lands = 16), listOf(scorer(1.0)))

        assertEquals(ScoreOutcome.Skipped(ScoreSkip.NOT_40), outcome)
    }

    @Test
    fun `a registered spell that is no candidate is off-colour`() {
        val outcome =
            RegisteredDeck.score(
                build(entry(strong, 12), entry(bolt, 11), entry(plains, 17)),
                listOf(scorer(1.0)),
            )

        assertEquals(ScoreOutcome.Skipped(ScoreSkip.OFF_COLOUR), outcome)
    }

    @Test
    fun `the read line counts the reader's skips and the scorer's in the order they run`() {
        val selection =
            BacktestSelection(
                3,
                DeckShape.entries.associateWith { 0 },
                mapOf(BacktestSkip.ONE_COLOUR to 1),
                emptyList(),
            )

        assertEquals(
            "read 3 builds with a bucket; skipped one-colour 1, five-colour 0, unresolved 0, " +
                "no-choice 0, no-config 2, not-40 0, off-colour 0",
            ScoreReport.readLine(selection, mapOf(ScoreSkip.NO_CONFIG to 2)),
        )
    }
}
