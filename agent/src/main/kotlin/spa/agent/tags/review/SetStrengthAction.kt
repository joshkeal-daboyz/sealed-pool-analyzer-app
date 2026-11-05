package spa.agent.tags.review

/** `set-strength`: changes the strength of a role the card has, in place. */
object SetStrengthAction : ReviewAction {
    override val name = "set-strength"
    override val fields = setOf("axis", "role", "strength")

    override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome {
        val axis = correction.axis!!
        val role = correction.role!!
        return if (card.roles.none { it.axis == axis && it.role == role }) {
            ReviewOutcome.Rejected("${card.id} has no $role role on $axis to set the strength of")
        } else {
            val roles =
                card.roles.map {
                    if (it.axis == axis && it.role == role)
                        it.copy(strength = correction.strength!!)
                    else it
                }
            ReviewOutcome.Applied(card.copy(roles = roles))
        }
    }
}
