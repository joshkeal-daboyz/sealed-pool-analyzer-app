package spa.agent.otags.cares

/** Every reason has some text. */
object CaresReasonCheck : CaresCheck {
    override fun problems(input: CaresCheckInput): List<String> =
        input.responses.flatMap { card ->
            card.add
                .filter { it.reason.isBlank() }
                .map { "${card.file}: ${card.id}: fact '${it.fact}' has an empty reason" }
        }
}
