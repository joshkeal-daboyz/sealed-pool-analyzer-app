package spa.agent.model

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams

/** The [ModelClient] that calls the Messages API through the Anthropic Java SDK. */
class AnthropicModelClient(private val client: AnthropicClient) : ModelClient, AutoCloseable {
    override fun send(request: ModelRequest): ModelResponse = TODO("SPA-029")

    override fun close() = TODO("SPA-029")

    companion object {
        /**
         * A client whose key comes from [environment]; throws [spa.agent.MissingApiKeyException]
         * when `ANTHROPIC_API_KEY` is not set.
         */
        fun fromEnvironment(
            environment: Map<String, String> = System.getenv()
        ): AnthropicModelClient = TODO("SPA-029")

        /** The SDK parameters for [request]. */
        internal fun toParams(request: ModelRequest): MessageCreateParams = TODO("SPA-029")

        /** [message] as a [ModelResponse]: its text blocks joined, its stop reason and usage. */
        internal fun toResponse(message: Message): ModelResponse = TODO("SPA-029")
    }
}
