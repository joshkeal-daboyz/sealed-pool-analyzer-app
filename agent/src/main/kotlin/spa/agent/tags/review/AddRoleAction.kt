package spa.agent.tags.review

/** `add-role`: gives the card a role on an axis, with a strength. */
object AddRoleAction : ReviewAction {
    override val name = "add-role"
    override val fields = setOf("axis", "role", "strength")

    override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome = TODO()
}
