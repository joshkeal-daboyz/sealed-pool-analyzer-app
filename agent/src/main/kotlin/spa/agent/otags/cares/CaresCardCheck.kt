package spa.agent.otags.cares

/** Every id in a response is a card of the card file the response belongs to. */
object CaresCardCheck : CaresCheck {
    override fun problems(input: CaresCheckInput): List<String> {
        val ids = input.cardFiles.associate { it.name to it.ids.toSet() }
        return input.responses
            .filter { it.id !in ids[CaresPackets.cardFileName(it.file)].orEmpty() }
            .map { "${it.file}: ${it.id}: not a card of ${CaresPackets.cardFileName(it.file)}" }
    }
}
