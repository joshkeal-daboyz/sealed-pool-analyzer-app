package spa.agent.deckbacktest

/** The player main colours are among the main colours of the first three ranked decks. */
object TopThree : RateMeasure("top3") {
    private const val DECKS = 3

    override fun hit(outcome: BuildOutcome): Boolean =
        outcome.ranked.take(DECKS).any { it.main == outcome.player.main }
}
