package spa.agent.deckbacktest.score

import spa.agent.build.BuildConfig
import spa.agent.build.BuildSetup
import spa.agent.build.DeckBuilder
import spa.agent.deckbacktest.BacktestBuild
import spa.core.math.LandMath

/** Why a read build is not scored, in the order the checks run (after the reader's own). */
enum class ScoreSkip(val label: String) {
    /** A splash colour that is a main colour too, or more than two splash colours. */
    NO_CONFIG("no-config"),

    /** The registered deck, lands included, is not exactly 40 cards. */
    NOT_40("not-40"),

    /** A registered nonland card is not a candidate for the player's configuration. */
    OFF_COLOUR("off-colour"),
}

/** A way to score a deck: the [builder]'s parts and the [setup] of its pool for a configuration. */
class DeckScorer(val builder: DeckBuilder, val setup: (BuildConfig) -> BuildSetup)

/** What scoring a build gives. */
sealed interface ScoreOutcome {
    /** The [scores] of the registered deck, one per scorer, in order. */
    data class Scored(val scores: List<Double>) : ScoreOutcome

    /** The build is not scored, for [reason]. */
    data class Skipped(val reason: ScoreSkip) : ScoreOutcome
}

/**
 * Scores the deck a player registered with the builder's parts, no search (SPA-061): the registered
 * nonland cards, one [spells][spa.agent.build.DeckCard] entry per copy as the search forms them,
 * taken from the candidates of the setup for the player's configuration; the land count is the
 * registered count of land cards, basics included. The builder's own mana split is used.
 */
object RegisteredDeck {
    /** The score of [build]'s registered deck with each of [scorers], or the reason it has none. */
    fun score(build: BacktestBuild, scorers: List<DeckScorer>): ScoreOutcome {
        val config = build.config
        val copies =
            build.cards
                .filter { !it.printing.card.isLand && it.deck > 0 }
                .groupBy { it.printing.card.oracleId }
                .mapValues { (_, cards) -> cards.sumOf { it.deck } }
        val landCount = build.cards.filter { it.printing.card.isLand }.sumOf { it.deck }
        return when {
            config == null -> ScoreOutcome.Skipped(ScoreSkip.NO_CONFIG)
            copies.values.sum() + landCount != LandMath.DEFAULT_DECK_SIZE ->
                ScoreOutcome.Skipped(ScoreSkip.NOT_40)
            else ->
                scores(config, copies, landCount, scorers)?.let { ScoreOutcome.Scored(it) }
                    ?: ScoreOutcome.Skipped(ScoreSkip.OFF_COLOUR)
        }
    }

    /** One score per scorer of [copies] (by oracle id) and [landCount]; null when off-colour. */
    private fun scores(
        config: BuildConfig,
        copies: Map<String, Int>,
        landCount: Int,
        scorers: List<DeckScorer>,
    ): List<Double>? = scorers.map { scorer ->
        val setup = scorer.setup(config)
        val candidates = setup.spells.associateBy { it.card.card.oracleId }
        val spells = copies.flatMap { (id, n) ->
            val candidate = candidates[id] ?: return null
            List(n) { candidate.card }
        }
        scorer.builder.evaluate(setup, spells, landCount).total
    }
}
