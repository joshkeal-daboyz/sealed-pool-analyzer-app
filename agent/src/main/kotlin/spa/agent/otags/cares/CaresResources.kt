package spa.agent.otags.cares

/**
 * The text resources of the cares-gaps packet (SPA-055), under `spa/agent/otags/`: the prompt and
 * the two response schemas. A new prompt version is a new resource file.
 */
object CaresResources {
    /** The prompt's resource name. */
    const val PROMPT = "cares-gaps-v1.md"

    /** The schema of one `cards-NN.cares.json`. */
    const val GAPS_SCHEMA = "cares-gaps.schema.json"

    /** The schema of `cares-table-suggestions.json`. */
    const val SUGGESTIONS_SCHEMA = "cares-table-suggestions.schema.json"

    /** The text of the resource [name], with line feeds only. */
    fun text(name: String): String {
        val resource = "/spa/agent/otags/$name"
        val stream =
            requireNotNull(CaresResources::class.java.getResourceAsStream(resource)) {
                "Missing $resource on the classpath."
            }
        return stream.use { it.readBytes().decodeToString() }.replace("\r\n", "\n")
    }

    /** The cares-gaps prompt. */
    val prompt: String by lazy { text(PROMPT) }
}
