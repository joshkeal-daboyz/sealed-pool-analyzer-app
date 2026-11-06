package spa.agent.otags.cares

/** No response adds a fact the card already cares about from Scryfall's tags. */
object CaresExistingCheck : CaresCheck {
    override fun problems(input: CaresCheckInput): List<String> =
        input.responses.flatMap { card ->
            val held = input.cares[card.id].orEmpty()
            card.add
                .filter { it.fact in held }
                .map {
                    "${card.file}: ${card.id}: the card already cares about '${it.fact}' " +
                        "from Scryfall's tags"
                }
        }
}
