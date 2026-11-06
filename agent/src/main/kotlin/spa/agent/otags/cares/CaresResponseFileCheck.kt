package spa.agent.otags.cares

/** Every card file has a response file beside it, even an empty one. */
object CaresResponseFileCheck : CaresCheck {
    override fun problems(input: CaresCheckInput): List<String> =
        input.cardFiles
            .map { it.name to CaresPackets.responseFileName(it.name) }
            .filter { (_, response) -> response !in input.responseFiles }
            .map { (cards, response) ->
                "$response: missing; write it for every card in $cards, even when it is empty"
            }
}
