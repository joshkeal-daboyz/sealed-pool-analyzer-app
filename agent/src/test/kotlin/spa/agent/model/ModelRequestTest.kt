package spa.agent.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class ModelRequestTest {
    private val base = ModelRequest("claude-opus-5-5", 1024, "system", "user")

    @Test
    fun `cachedContext defaults to null and label to empty`() {
        assertNull(base.cachedContext)
        assertEquals("", base.label)
    }

    @Test
    fun `a request written before the two fields existed still decodes`() {
        val old =
            """{"model":"claude-opus-5-5","maxTokens":1024,"system":"system","userMessage":"user"}"""
        assertEquals(base, Json.decodeFromString(ModelRequest.serializer(), old))
    }

    @Test
    fun `requests differing only in cachedContext or only in label are not equal`() {
        assertNotEquals(base, base.copy(cachedContext = "pool"))
        assertNotEquals(base.copy(cachedContext = "a"), base.copy(cachedContext = "b"))
        assertNotEquals(base, base.copy(label = "curve"))
        assertNotEquals(base.hashCode(), base.copy(label = "curve").hashCode())
    }

    @Test
    fun `cachedContext and label survive a round trip`() {
        val full = base.copy(cachedContext = "pool", label = "curve-retry1")
        val json = Json.encodeToString(ModelRequest.serializer(), full)
        assertEquals(full, Json.decodeFromString(ModelRequest.serializer(), json))
    }
}
