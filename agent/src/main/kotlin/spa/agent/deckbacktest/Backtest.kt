package spa.agent.deckbacktest

import spa.agent.build.RankedDeck
import spa.agent.build.ScoredDeck

/** Turns the builder decks for one build into the [BuildOutcome] the measures read. */
object Backtest {
    /**
     * The outcome for [player]: the colours of the [ranked] decks, and the copies and land count of
     * [deck], the deck to measure overlap and land count on (null when none could be built).
     */
    fun outcome(player: Player, ranked: List<RankedDeck>, deck: ScoredDeck?): BuildOutcome =
        BuildOutcome(
            player,
            ranked.map { RankedColours(it.config.main, it.config.splash) },
            deck?.let { BuiltDeck(copies(it), it.deck.landCount) },
        )

    private fun copies(scored: ScoredDeck): Map<String, Int> =
        scored.deck.spells.groupingBy { it.card.oracleId }.eachCount()
}
