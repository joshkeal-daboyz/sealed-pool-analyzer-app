package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class ReviewConfigTest {
    // A copy of fixtures/agents/review.json as committed by this ticket.
    private val committed =
        """
        {
          "version": 1,
          "model": "claude-sonnet-5-5",
          "maxInputTokens": 300000,
          "maxOutputTokens": 60000,
          "callTimeoutSeconds": 120,
          "parallel": 6,
          "retries": 1,
          "maxFindings": 8,
          "agents": [],
          "explainer": { "enabled": true, "maxTokens": 6000, "prompt": "v1" }
        }
        """
            .trimIndent()

    private val base: JsonObject = Json.parseToJsonElement(committed).jsonObject
    private val known = listOf("fake-a", "fake-b")

    private fun parse(text: String) = ReviewConfig.parse(text, known)

    private fun with(key: String, value: String): String =
        JsonObject(base + (key to Json.parseToJsonElement(value))).toString()

    private fun without(key: String): String = JsonObject(base - key).toString()

    private fun assertRejected(key: String, text: String) {
        val message = assertFailsWith<IllegalArgumentException> { parse(text) }.message.orEmpty()
        assertTrue(message.contains(key), "'$message' should name '$key'")
    }

    private fun agentEntry(
        id: String = "fake-a",
        extra: String = "",
        enabled: String = "true",
        maxTokens: String = "3000",
        prompt: String = "\"v1\"",
    ) = """{"id": "$id", "enabled": $enabled, "maxTokens": $maxTokens, "prompt": $prompt$extra}"""

    @Test
    fun `the committed shape parses`() {
        val config = parse(committed)
        assertEquals(1, config.version)
        assertEquals("claude-sonnet-5-5", config.model)
        assertEquals(300_000L, config.maxInputTokens)
        assertEquals(60_000L, config.maxOutputTokens)
        assertEquals(120, config.callTimeoutSeconds)
        assertEquals(6, config.parallel)
        assertEquals(1, config.retries)
        assertEquals(8, config.maxFindings)
        assertEquals(emptyList(), config.agents)
        assertEquals(ExplainerSetting(true, 6000, "v1"), config.explainer)
    }

    @Test
    fun `agent entries parse in order and can be switched off`() {
        val entries =
            "[${agentEntry("fake-b", enabled = "false", maxTokens = "2000")}, ${agentEntry()}]"
        val config = parse(with("agents", entries))
        assertEquals(
            listOf(
                AgentSetting("fake-b", false, 2000, "v1"),
                AgentSetting("fake-a", true, 3000, "v1"),
            ),
            config.agents,
        )
        assertEquals(AgentSetting("fake-a", true, 3000, "v1"), config.setting("fake-a"))
        assertNull(config.setting("fake-c"))
    }

    @Test
    fun `an unknown key is rejected naming it`() {
        assertRejected("colour", with("colour", "1"))
        val explainer = """{"enabled": true, "maxTokens": 1, "prompt": "v1", "zeta": 1}"""
        assertRejected("zeta", with("explainer", explainer))
        assertRejected("mood", with("agents", "[${agentEntry(extra = ", \"mood\": 1")}]"))
    }

    @Test
    fun `a missing key is rejected naming it`() {
        for (key in base.keys) {
            assertRejected(key, without(key))
        }
        assertRejected("maxTokens", with("explainer", """{"enabled": true, "prompt": "v1"}"""))
        val noPrompt = """[{"id": "fake-a", "enabled": true, "maxTokens": 1}]"""
        assertRejected("prompt", with("agents", noPrompt))
    }

    @Test
    fun `a wrong type is rejected naming the key`() {
        val wrong =
            mapOf(
                "version" to "\"1\"",
                "model" to "5",
                "maxInputTokens" to "\"300000\"",
                "maxOutputTokens" to "true",
                "callTimeoutSeconds" to "1.5",
                "parallel" to "\"6\"",
                "retries" to "[]",
                "maxFindings" to "null",
                "agents" to "{}",
                "explainer" to "[]",
            )
        for ((key, value) in wrong) {
            assertRejected(key, with(key, value))
        }
        assertRejected("enabled", with("agents", "[${agentEntry(enabled = "\"yes\"")}]"))
        assertRejected("prompt", with("agents", "[${agentEntry(prompt = "3")}]"))
    }

    @Test
    fun `a zero or negative count and retries over 3 are rejected naming the key`() {
        val counts =
            listOf(
                "maxInputTokens",
                "maxOutputTokens",
                "callTimeoutSeconds",
                "parallel",
                "maxFindings",
            )
        for (key in counts) {
            assertRejected(key, with(key, "0"))
            assertRejected(key, with(key, "-1"))
        }
        assertRejected("retries", with("retries", "4"))
        assertRejected("retries", with("retries", "-1"))
        assertEquals(3, parse(with("retries", "3")).retries)
        assertEquals(0, parse(with("retries", "0")).retries)
        assertRejected("maxTokens", with("agents", "[${agentEntry(maxTokens = "0")}]"))
        assertRejected(
            "maxTokens",
            with("explainer", """{"enabled": true, "maxTokens": 0, "prompt": "v1"}"""),
        )
    }

    @Test
    fun `a duplicate or unknown agent id is rejected naming it`() {
        assertRejected("fake-a", with("agents", "[${agentEntry()}, ${agentEntry()}]"))
        assertRejected("mystery", with("agents", "[${agentEntry("mystery")}]"))
    }

    @Test
    fun `text that is not a JSON object is rejected`() {
        assertRejected("JSON", "nope")
        assertRejected("JSON", "[]")
    }
}
