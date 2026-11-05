package spa.agent.tags.review

import spa.agent.tags.ResponsePartner

/** `add-partner`: names a partner the card lacks, with a strength, after its partners. */
object AddPartnerAction : ReviewAction {
    override val name = "add-partner"
    override val fields = setOf("partner", "strength")

    override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome {
        val partner = correction.partner!!
        return if (card.partners.any { it.id == partner }) {
            ReviewOutcome.Rejected("${card.id} already has the partner $partner")
        } else {
            val added = ResponsePartner(partner, correction.strength!!)
            ReviewOutcome.Applied(card.copy(partners = card.partners + added))
        }
    }
}
