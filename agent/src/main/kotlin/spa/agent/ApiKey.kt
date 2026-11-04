package spa.agent

/** Thrown when the Anthropic API key is missing; its message is one line the CLI prints as is. */
class MissingApiKeyException(message: String) : IllegalStateException(message)

/** The Anthropic API key, read only from the environment and never stored anywhere else. */
object ApiKey {
    /** The environment variable that holds the key. */
    const val VARIABLE = "ANTHROPIC_API_KEY"

    /**
     * The key from [environment], or a [MissingApiKeyException] saying `ANTHROPIC_API_KEY is not
     * set` when it is absent or blank. The caller passes the environment (`System.getenv()` in
     * production), so tests never read the real one.
     */
    fun require(environment: Map<String, String>): String {
        val key = environment[VARIABLE]
        if (key.isNullOrBlank()) throw MissingApiKeyException("$VARIABLE is not set")
        return key
    }
}
