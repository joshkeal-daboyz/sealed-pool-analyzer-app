package spa.agent.tags

/** Every role's and every partner's strength is 1, 2 or 3, written as a whole number. */
object StrengthCheck : TagCheck {
    private val strengths = setOf("1", "2", "3")

    /** True when [strength], as written, is one of the allowed strengths. */
    fun isStrength(strength: String): Boolean = strength in strengths

    override fun problems(input: TagCheckInput): List<String> =
        input.responses.flatMap { card ->
            val roles =
                card.roles
                    .filter { !isStrength(it.strength) }
                    .map {
                        "${card.file}: ${card.id} strength ${it.strength} for ${it.role} on " +
                            "${it.axis} is not 1, 2 or 3"
                    }
            val partners =
                card.partners
                    .filter { !isStrength(it.strength) }
                    .map {
                        "${card.file}: ${card.id} partner ${it.id} strength ${it.strength} is " +
                            "not 1, 2 or 3"
                    }
            roles + partners
        }
}
