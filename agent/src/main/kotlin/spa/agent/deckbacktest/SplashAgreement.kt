package spa.agent.deckbacktest

/** The top deck splashes exactly when the player did. */
object SplashAgreement : RateMeasure("splash") {
    override fun hit(outcome: BuildOutcome): Boolean {
        val top = outcome.ranked.firstOrNull() ?: return false
        return top.splash.isNotEmpty() == outcome.player.splash.isNotEmpty()
    }
}
