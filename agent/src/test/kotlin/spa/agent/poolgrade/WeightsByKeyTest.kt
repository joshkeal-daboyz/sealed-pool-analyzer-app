package spa.agent.poolgrade

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** SPA-043 criterion 2: weights read by key, one key per part of [CardParts.ALL], plus `cap`. */
class WeightsByKeyTest {
    @Test
    fun `the parts are synergy, curve, colour and tagSynergy, in that order`() {
        assertEquals(
            listOf("synergy", "curve", "colour", "tagSynergy"),
            CardParts.ALL.map { it.key },
        )
    }

    @Test
    fun `the four-key format parses as before, with tagSynergy 0`() {
        val weights =
            Weights.parse("""{ "synergy": 0.5, "curve": 0.25, "colour": 0.25, "cap": 1.0 }""")

        assertEquals(Weights.DEFAULT, weights)
        assertEquals(0.0, weights["tagSynergy"])
        assertEquals(0.5, weights["synergy"])
        assertEquals(1.0, weights.cap)
    }

    @Test
    fun `a file with tagSynergy parses, and a missing part has weight 0`() {
        val weights = Weights.parse("""{ "tagSynergy": 0.5, "cap": 1.0 }""")

        assertEquals(0.5, weights["tagSynergy"])
        assertEquals(0.5, weights.tagSynergy)
        assertEquals(0.0, weights.synergy)
        assertEquals(0.0, weights.curve)
        assertEquals(0.0, weights.colour)
        assertEquals(1.0, weights.cap)
        assertEquals(
            Weights(synergy = 0.0, curve = 0.0, colour = 0.0, cap = 1.0, tagSynergy = 0.5),
            weights,
        )
    }

    @Test
    fun `a weight of 0 and a missing weight are the same weights`() {
        assertEquals(
            Weights.parse("""{ "synergy": 0.5, "curve": 0, "colour": 0.25, "cap": 1 }"""),
            Weights.parse("""{ "synergy": 0.5, "colour": 0.25, "cap": 1 }"""),
        )
    }

    @Test
    fun `an unknown key is an error naming it`() {
        val e =
            assertThrows<IllegalArgumentException> {
                Weights.parse("""{ "synergy": 0.5, "tagsynergy": 0.5, "cap": 1 }""")
            }
        assertTrue("'tagsynergy'" in e.message.orEmpty(), e.message)
    }

    @Test
    fun `a negative number or a non-number is an error naming its key`() {
        listOf(
                """{ "tagSynergy": -0.5, "cap": 1 }""",
                """{ "tagSynergy": "0.5", "cap": 1 }""",
                """{ "tagSynergy": null, "cap": 1 }""",
                """{ "tagSynergy": [0.5], "cap": 1 }""",
            )
            .forEach { text ->
                val e = assertThrows<IllegalArgumentException>(text) { Weights.parse(text) }
                assertTrue("'tagSynergy'" in e.message.orEmpty(), "$text: ${e.message}")
            }
    }

    @Test
    fun `cap is required`() {
        val e =
            assertThrows<IllegalArgumentException> { Weights.parse("""{ "tagSynergy": 0.5 }""") }
        assertTrue("'cap'" in e.message.orEmpty(), e.message)
    }

    @Test
    fun `a part added to the list is read by its key`() {
        val parts = CardParts.ALL + SeamPart
        val weights = Weights.parse("""{ "seam": 0.3, "cap": 1 }""", parts)

        assertEquals(0.3, weights["seam"])
        assertThrows<IllegalArgumentException> { Weights.parse("""{ "seam": 0.3, "cap": 1 }""") }
    }
}
