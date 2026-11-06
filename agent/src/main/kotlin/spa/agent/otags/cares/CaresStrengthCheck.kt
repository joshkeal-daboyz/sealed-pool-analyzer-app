package spa.agent.otags.cares

/** Every strength is 1, 2 or 3. */
object CaresStrengthCheck : CaresCheck {
    private val allowed = 1..3

    override fun problems(input: CaresCheckInput): List<String> =
        input.responses.flatMap { card ->
            card.add
                .filter { it.strength.toIntOrNull() !in allowed }
                .map {
                    "${card.file}: ${card.id}: fact '${it.fact}' has strength ${it.strength}, " +
                        "not 1 to 3"
                }
        }
}
