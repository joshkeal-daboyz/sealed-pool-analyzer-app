package spa.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiKeyTest {
    @Test
    fun `a set key is returned`() {
        assertEquals("test-key-1", ApiKey.require(mapOf("ANTHROPIC_API_KEY" to "test-key-1")))
    }

    @Test
    fun `a missing key throws one exact line`() {
        val e = assertFailsWith<MissingApiKeyException> { ApiKey.require(mapOf("PATH" to "/bin")) }
        assertEquals("ANTHROPIC_API_KEY is not set", e.message)
    }

    @Test
    fun `an empty or blank key counts as not set`() {
        for (value in listOf("", "   ", "\t\n")) {
            val e =
                assertFailsWith<MissingApiKeyException> {
                    ApiKey.require(mapOf("ANTHROPIC_API_KEY" to value))
                }
            assertEquals("ANTHROPIC_API_KEY is not set", e.message)
        }
    }
}
