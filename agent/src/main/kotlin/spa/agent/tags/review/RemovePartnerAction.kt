package spa.agent.tags.review

/** `remove-partner`: takes a named partner off the card. */
object RemovePartnerAction : ReviewAction {
    override val name = "remove-partner"
    override val fields = setOf("partner")

    override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome = TODO()
}
