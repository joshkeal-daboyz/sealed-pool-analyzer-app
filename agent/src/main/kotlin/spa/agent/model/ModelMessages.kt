package spa.agent.model

import kotlinx.serialization.Serializable

/**
 * One Messages API request as the agent layer sends it: one system prompt and one user turn. It is
 * also the key a [ReplayModelClient] looks its recordings up by, so it holds only plain data.
 */
@Serializable
data class ModelRequest(
    val model: String,
    val maxTokens: Long,
    val system: String,
    val userMessage: String,
)

/** Token counts for one response, as the API reports them. */
@Serializable
data class ModelUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadInputTokens: Long = 0,
    val cacheCreationInputTokens: Long = 0,
)

/** One Messages API response: the model that answered, its text, why it stopped, and its usage. */
@Serializable
data class ModelResponse(
    val model: String,
    val text: String,
    val stopReason: String?,
    val usage: ModelUsage,
)
