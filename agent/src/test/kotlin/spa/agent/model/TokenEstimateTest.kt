package spa.agent.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenEstimateTest {
    private fun request(context: String?, system: String, user: String) =
        ModelRequest("m", 100, system, user, cachedContext = context)

    @Test
    fun `the estimate is the characters of context, system and user message over four, rounded up`() {
        // 400 + 100 + 41 = 541 characters, 541 / 4 = 135.25, rounded up to 136.
        assertEquals(
            136L,
            TokenEstimate.of(request("c".repeat(400), "s".repeat(100), "u".repeat(41))),
        )
    }

    @Test
    fun `without a cached context only system and user message count`() {
        assertEquals(25L, TokenEstimate.of(request(null, "s".repeat(60), "u".repeat(40))))
        assertEquals(26L, TokenEstimate.of(request(null, "s".repeat(60), "u".repeat(41))))
    }

    @Test
    fun `an exact multiple of four is not rounded up and an empty request is zero`() {
        assertEquals(10L, TokenEstimate.of(request(null, "s".repeat(40), "")))
        assertEquals(0L, TokenEstimate.of(request(null, "", "")))
    }

    @Test
    fun `the label and the model do not count`() {
        val plain = request(null, "s".repeat(8), "u".repeat(8))
        assertEquals(TokenEstimate.of(plain), TokenEstimate.of(plain.copy(label = "x".repeat(400))))
    }
}
