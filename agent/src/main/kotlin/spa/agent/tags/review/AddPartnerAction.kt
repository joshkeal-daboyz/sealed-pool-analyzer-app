package spa.agent.tags.review

/** `add-partner`: names a partner for the card, with a strength. */
object AddPartnerAction : ReviewAction {
    override val name = "add-partner"
    override val fields = setOf("partner", "strength")

    override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome = TODO()
}
