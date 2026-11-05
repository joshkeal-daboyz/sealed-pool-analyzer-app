package spa.agent.tags.review

/** `set-strength`: changes the strength of a role the card has. */
object SetStrengthAction : ReviewAction {
    override val name = "set-strength"
    override val fields = setOf("axis", "role", "strength")

    override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome = TODO()
}
