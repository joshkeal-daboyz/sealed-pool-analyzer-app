package spa.agent.tags.review

/** `remove-partner`: takes a named partner off the card; the card must name it. */
object RemovePartnerAction : ReviewAction {
    override val name = "remove-partner"
    override val fields = setOf("partner")

    override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome {
        val partner = correction.partner!!
        val kept = card.partners.filterNot { it.id == partner }
        return if (kept.size == card.partners.size) {
            ReviewOutcome.Rejected("${card.id} has no partner $partner to remove")
        } else {
            ReviewOutcome.Applied(card.copy(partners = kept))
        }
    }
}
