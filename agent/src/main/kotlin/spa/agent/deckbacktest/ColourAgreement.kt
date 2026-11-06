package spa.agent.deckbacktest

/** The top deck main colours are the player main colours, as sets. */
object ColourAgreement : RateMeasure("colour") {
    override fun hit(outcome: BuildOutcome): Boolean =
        outcome.ranked.firstOrNull()?.main == outcome.player.main
}
