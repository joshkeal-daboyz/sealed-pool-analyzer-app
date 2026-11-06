package spa.agent.model

/** A rough token count for a request, used to reserve budget before it is sent. */
object TokenEstimate {
    private const val CHARACTERS_PER_TOKEN = 4L

    /**
     * The characters of the cached context, the system prompt and the user message, divided by four
     * and rounded up. A rough estimate: real tokenization differs, and the budget replaces it with
     * the actual usage once the response arrives.
     */
    fun of(request: ModelRequest): Long {
        val characters =
            (request.cachedContext?.length ?: 0).toLong() +
                request.system.length +
                request.userMessage.length
        return (characters + CHARACTERS_PER_TOKEN - 1) / CHARACTERS_PER_TOKEN
    }
}
