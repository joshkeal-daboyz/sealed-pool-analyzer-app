package spa.agent.tags.review

/** `remove-role`: takes a role on an axis off the card; the card must have it. */
object RemoveRoleAction : ReviewAction {
    override val name = "remove-role"
    override val fields = setOf("axis", "role")

    override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome {
        val axis = correction.axis!!
        val role = correction.role!!
        val kept = card.roles.filterNot { it.axis == axis && it.role == role }
        return if (kept.size == card.roles.size) {
            ReviewOutcome.Rejected("${card.id} has no $role role on $axis to remove")
        } else {
            ReviewOutcome.Applied(card.copy(roles = kept))
        }
    }
}
