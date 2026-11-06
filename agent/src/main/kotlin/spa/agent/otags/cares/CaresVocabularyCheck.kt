package spa.agent.otags.cares

/** Every fact a response adds is a fact name of `vocabulary.json`. */
object CaresVocabularyCheck : CaresCheck {
    override fun problems(input: CaresCheckInput): List<String> =
        input.responses.flatMap { card ->
            card.add
                .filter { it.fact !in input.vocabulary }
                .map {
                    "${card.file}: ${card.id}: fact '${it.fact}' is not in " +
                        CaresPackets.VOCABULARY
                }
        }
}
