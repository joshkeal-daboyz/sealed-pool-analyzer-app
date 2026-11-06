package spa.agent.model

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import spa.agent.ApiKey

/**
 * The [ModelClient] that calls the Messages API through the Anthropic Java SDK. Nothing in the gate
 * calls [send]: tests use [ReplayModelClient], and live runs are manual.
 */
class AnthropicModelClient(private val client: AnthropicClient) : ModelClient, AutoCloseable {
    override fun send(request: ModelRequest): ModelResponse =
        toResponse(client.messages().create(toParams(request)))

    override fun close() = client.close()

    companion object {
        /**
         * A client whose key comes from [environment]; throws [spa.agent.MissingApiKeyException]
         * when `ANTHROPIC_API_KEY` is not set.
         */
        fun fromEnvironment(
            environment: Map<String, String> = System.getenv()
        ): AnthropicModelClient =
            AnthropicModelClient(
                AnthropicOkHttpClient.builder().apiKey(ApiKey.require(environment)).build()
            )

        /**
         * The SDK parameters for [request]. With a cached context, the system prompt is two text
         * blocks: the context with ephemeral cache control, then the request's own system text.
         */
        internal fun toParams(request: ModelRequest): MessageCreateParams {
            val builder =
                MessageCreateParams.builder()
                    .model(request.model)
                    .maxTokens(request.maxTokens)
                    .addUserMessage(request.userMessage)
            val context = request.cachedContext
            if (context == null) {
                builder.system(request.system)
            } else {
                builder.systemOfTextBlockParams(
                    listOf(
                        TextBlockParam.builder()
                            .text(context)
                            .cacheControl(CacheControlEphemeral.builder().build())
                            .build(),
                        TextBlockParam.builder().text(request.system).build(),
                    )
                )
            }
            return builder.build()
        }

        /** [message] as a [ModelResponse]: its text blocks joined, its stop reason and usage. */
        internal fun toResponse(message: Message): ModelResponse {
            val usage = message.usage()
            return ModelResponse(
                model = message.model().asString(),
                text =
                    message
                        .content()
                        .mapNotNull { block -> block.text().orElse(null)?.text() }
                        .joinToString(""),
                stopReason = message.stopReason().orElse(null)?.asString(),
                usage =
                    ModelUsage(
                        inputTokens = usage.inputTokens(),
                        outputTokens = usage.outputTokens(),
                        cacheReadInputTokens = usage.cacheReadInputTokens().orElse(0L),
                        cacheCreationInputTokens = usage.cacheCreationInputTokens().orElse(0L),
                    ),
            )
        }
    }
}
