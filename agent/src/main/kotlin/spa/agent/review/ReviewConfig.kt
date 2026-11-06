package spa.agent.review

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** One analyst's settings: [enabled] false sends no call; [prompt] is its prompt file's version. */
data class AgentSetting(
    val id: String,
    val enabled: Boolean,
    val maxTokens: Long,
    val prompt: String,
)

/** The Explainer's settings, as [AgentSetting] without an id. */
data class ExplainerSetting(val enabled: Boolean, val maxTokens: Long, val prompt: String)

/**
 * The settings of the pool review, read from `fixtures/agents/review.json`: the model, the token
 * caps, the timeout, the parallelism, the retries, the findings limit, which agents run and the
 * Explainer.
 */
data class ReviewConfig(
    val version: Int,
    val model: String,
    val maxInputTokens: Long,
    val maxOutputTokens: Long,
    val callTimeoutSeconds: Int,
    val parallel: Int,
    val retries: Int,
    val maxFindings: Int,
    val agents: List<AgentSetting>,
    val explainer: ExplainerSetting,
) {
    /** The settings of agent [id]; null when the config does not list it, so it does not run. */
    fun setting(id: String): AgentSetting? = agents.firstOrNull { it.id == id }

    companion object {
        private const val MAX_RETRIES = 3L
        private val TOP_KEYS =
            setOf(
                "version",
                "model",
                "maxInputTokens",
                "maxOutputTokens",
                "callTimeoutSeconds",
                "parallel",
                "retries",
                "maxFindings",
                "agents",
                "explainer",
            )
        private val AGENT_KEYS = setOf("id", "enabled", "maxTokens", "prompt")
        private val EXPLAINER_KEYS = setOf("enabled", "maxTokens", "prompt")

        private fun fail(message: String): Nothing = throw IllegalArgumentException(message)

        /**
         * Reads [text] strictly. Every key must be present and known, every count above 0,
         * `retries` from 0 to 3, and every agent id unique and one of [knownAgentIds]; otherwise an
         * [IllegalArgumentException] names the key.
         */
        fun parse(text: String, knownAgentIds: Collection<String>): ReviewConfig {
            val parsed =
                try {
                    Json.parseToJsonElement(text)
                } catch (error: SerializationException) {
                    fail("the config is not JSON: ${error.message.orEmpty().lines().first()}")
                }
            val root = parsed as? JsonObject ?: fail("the config is not a JSON object")
            val top = StrictObject(root, "", TOP_KEYS, ::fail)
            val explainer = top.child("explainer", EXPLAINER_KEYS)
            return ReviewConfig(
                version = top.smallCount("version"),
                model = top.string("model"),
                maxInputTokens = top.count("maxInputTokens"),
                maxOutputTokens = top.count("maxOutputTokens"),
                callTimeoutSeconds = top.smallCount("callTimeoutSeconds"),
                parallel = top.smallCount("parallel"),
                retries = top.retries(),
                maxFindings = top.smallCount("maxFindings"),
                agents = agents(top, knownAgentIds),
                explainer =
                    ExplainerSetting(
                        explainer.boolean("enabled"),
                        explainer.count("maxTokens"),
                        explainer.string("prompt"),
                    ),
            )
        }

        /** A whole number above 0. */
        private fun StrictObject.count(name: String): Long {
            val value = whole(name)
            if (value < 1) fail("'$name' must be a whole number above 0")
            return value
        }

        /** A whole number above 0 that fits an Int. */
        private fun StrictObject.smallCount(name: String): Int {
            val value = count(name)
            if (value > Int.MAX_VALUE) fail("'$name' must be at most ${Int.MAX_VALUE}")
            return value.toInt()
        }

        private fun StrictObject.retries(): Int {
            val value = whole("retries")
            if (value < 0 || value > MAX_RETRIES) fail("'retries' must be from 0 to $MAX_RETRIES")
            return value.toInt()
        }

        private fun agents(top: StrictObject, known: Collection<String>): List<AgentSetting> {
            val settings =
                top.objects("agents", AGENT_KEYS).mapIndexed { index, entry ->
                    val id = entry.string("id")
                    if (id !in known) fail("'agents[$index].id' names an unknown agent '$id'")
                    AgentSetting(
                        id,
                        entry.boolean("enabled"),
                        entry.count("maxTokens"),
                        entry.string("prompt"),
                    )
                }
            settings
                .groupBy { it.id }
                .entries
                .firstOrNull { it.value.size > 1 }
                ?.let { fail("'agents' lists '${it.key}' twice") }
            return settings
        }
    }
}
