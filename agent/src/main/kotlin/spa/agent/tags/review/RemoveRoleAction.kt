package spa.agent.tags.review

/** `remove-role`: takes a role on an axis off the card. */
object RemoveRoleAction : ReviewAction {
    override val name = "remove-role"
    override val fields = setOf("axis", "role")

    override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome = TODO()
}
