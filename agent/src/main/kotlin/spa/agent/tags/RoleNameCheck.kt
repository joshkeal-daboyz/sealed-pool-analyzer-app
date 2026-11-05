package spa.agent.tags

/** Every role is [ENABLER] or [PAYOFF]. */
object RoleNameCheck : TagCheck {
    const val ENABLER = "enabler"
    const val PAYOFF = "payoff"

    private val roles = setOf(ENABLER, PAYOFF)

    override fun problems(input: TagCheckInput): List<String> =
        input.responses.flatMap { card ->
            card.roles
                .filter { it.role !in roles }
                .map {
                    "${card.file}: ${card.id} role '${it.role}' on ${it.axis} is not " +
                        "$ENABLER or $PAYOFF"
                }
        }
}
