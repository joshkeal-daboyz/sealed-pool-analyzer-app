package spa.agent.tags.review

import spa.agent.tags.ResponseRole

/** `add-role`: gives the card a role on an axis that it lacks, with a strength, after its roles. */
object AddRoleAction : ReviewAction {
    override val name = "add-role"
    override val fields = setOf("axis", "role", "strength")

    override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome {
        val axis = correction.axis!!
        val role = correction.role!!
        return if (card.roles.any { it.axis == axis && it.role == role }) {
            ReviewOutcome.Rejected("${card.id} already has the $role role on $axis")
        } else {
            val added = ResponseRole(axis, role, correction.strength!!)
            ReviewOutcome.Applied(card.copy(roles = card.roles + added))
        }
    }
}
