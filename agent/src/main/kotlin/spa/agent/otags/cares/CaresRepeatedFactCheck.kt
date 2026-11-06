package spa.agent.otags.cares

/** No card adds the same fact twice. */
object CaresRepeatedFactCheck : CaresCheck {
    override fun problems(input: CaresCheckInput): List<String> =
        input.responses
            .groupBy { it.file to it.id }
            .flatMap { (key, entries) ->
                entries
                    .flatMap { it.add }
                    .groupingBy { it.fact }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
                    .map { "${key.first}: ${key.second}: fact '$it' is added twice" }
            }
}
