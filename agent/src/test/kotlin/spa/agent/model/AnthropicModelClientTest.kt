package spa.agent.model

import com.anthropic.models.messages.ContentBlock
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlock
import com.anthropic.models.messages.ThinkingBlock
import com.anthropic.models.messages.Usage
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import spa.agent.MissingApiKeyException

/** No test here sends a request: the SDK client is built but never called. */
class AnthropicModelClientTest {
    private val request =
        ModelRequest(
            model = "claude-opus-5-5",
            maxTokens = 2048,
            system = "You grade cards.",
            userMessage = "Grade Battle-Scarred Survivalist (trk 95).",
        )

    private fun text(value: String): ContentBlock =
        ContentBlock.ofText(TextBlock.builder().text(value).citations(Optional.empty()).build())

    private fun usage(input: Long, output: Long, cacheRead: Long?, cacheCreation: Long?): Usage =
        Usage.builder()
            .inputTokens(input)
            .outputTokens(output)
            .cacheReadInputTokens(Optional.ofNullable(cacheRead))
            .cacheCreationInputTokens(Optional.ofNullable(cacheCreation))
            .cacheCreation(Optional.empty())
            .inferenceGeo(Optional.empty())
            .outputTokensDetails(Optional.empty())
            .serverToolUse(Optional.empty())
            .serviceTier(Optional.empty())
            .speed(Optional.empty())
            .build()

    private fun message(
        content: List<ContentBlock>,
        stopReason: StopReason?,
        usage: Usage,
    ): Message =
        Message.builder()
            .id("msg_replay_0001")
            .model("claude-opus-5-5")
            .content(content)
            .stopReason(Optional.ofNullable(stopReason))
            .stopSequence(Optional.empty())
            .stopDetails(Optional.empty())
            .container(Optional.empty())
            .diagnostics(Optional.empty())
            .usage(usage)
            .build()

    @Test
    fun `a request becomes Messages API parameters with one system prompt and one user turn`() {
        val params = AnthropicModelClient.toParams(request)
        assertEquals("claude-opus-5-5", params.model().asString())
        assertEquals(2048L, params.maxTokens())
        assertEquals("You grade cards.", params.system().get().asString())
        val messages = params.messages()
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].role().asString())
        assertEquals("Grade Battle-Scarred Survivalist (trk 95).", messages[0].content().asString())
    }

    @Test
    fun `a response keeps its text blocks in order, its stop reason and its usage`() {
        val thinking =
            ContentBlock.ofThinking(ThinkingBlock.builder().thinking("").signature("sig").build())
        val response =
            message(
                listOf(thinking, text("{\"score\": 2.5,"), text(" \"confidence\": 0.6}")),
                StopReason.END_TURN,
                usage(120, 40, 96, 12),
            )
        assertEquals(
            ModelResponse(
                model = "claude-opus-5-5",
                text = "{\"score\": 2.5, \"confidence\": 0.6}",
                stopReason = "end_turn",
                usage = ModelUsage(120, 40, 96, 12),
            ),
            AnthropicModelClient.toResponse(response),
        )
    }

    @Test
    fun `absent cache counts are zero and an absent stop reason is null`() {
        val response = message(listOf(text("partial")), null, usage(10, 1, null, null))
        assertEquals(
            ModelResponse("claude-opus-5-5", "partial", null, ModelUsage(10, 1, 0, 0)),
            AnthropicModelClient.toResponse(response),
        )
    }

    @Test
    fun `building from an environment without the key fails with the one-line message`() {
        val e =
            assertFailsWith<MissingApiKeyException> {
                AnthropicModelClient.fromEnvironment(mapOf("HOME" to "/home/test"))
            }
        assertEquals("ANTHROPIC_API_KEY is not set", e.message)
    }

    @Test
    fun `building from an environment with a key makes a client without calling the API`() {
        AnthropicModelClient.fromEnvironment(mapOf("ANTHROPIC_API_KEY" to "test-not-a-real-key"))
            .use { client -> assertEquals(AnthropicModelClient::class, client::class) }
    }
}
