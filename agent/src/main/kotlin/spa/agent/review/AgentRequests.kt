package spa.agent.review

import spa.agent.model.ModelRequest

/** Builds the request an analysis agent sends. */
object AgentRequests {
    private const val SEPARATOR = "\n\n"
    private const val RESOURCE_DIR = "/spa/agent/review/"

    /**
     * The request of [agent] for [input]: the config's model and output cap, the agent id as label,
     * the pool bundle as cached context, and as system text the preamble, the agent's prompt file
     * and the schema. The user message is the number table, the facts if any, and a closing line.
     */
    fun of(agent: AnalysisAgent, input: ReviewInput, config: ReviewConfig): ModelRequest {
        val setting =
            requireNotNull(config.setting(agent.id)) {
                "the review config lists no agent '${agent.id}'"
            }
        val format = "v${config.version}"
        val system =
            listOf(
                    resource("preamble-$format.md"),
                    resource("${agent.id}-${setting.prompt}.md"),
                    resource("schema-$format.md"),
                )
                .joinToString(SEPARATOR)
        val facts = agent.facts(input)
        val factsPart = if (facts.isEmpty()) "" else "${SEPARATOR}## Facts\n$facts"
        val userMessage =
            "## Number table\n${agent.numbers(input).text()}$factsPart" +
                "${SEPARATOR}Answer with the JSON object only."
        return ModelRequest(
            model = config.model,
            maxTokens = setting.maxTokens,
            system = system,
            userMessage = userMessage,
            cachedContext = PoolBundle.text(input),
            label = agent.id,
        )
    }

    internal fun resource(name: String): String {
        val stream =
            checkNotNull(AgentRequests::class.java.getResourceAsStream(RESOURCE_DIR + name)) {
                "missing resource $name"
            }
        return stream.use { it.readBytes() }.toString(Charsets.UTF_8).removeSuffix("\n")
    }
}
