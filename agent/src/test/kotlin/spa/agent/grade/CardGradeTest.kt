package spa.agent.grade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CardGradeTest {
    private fun grade(
        score: Double = 3.0,
        confidence: Double = 0.5,
        source: GradeSource = GradeSource.MODEL,
        provenance: String = "claude-opus-5-5 prompt v1 2026-11-04",
    ) = CardGrade(score, confidence, source, provenance)

    @Test
    fun `every step of the scale from 0 to 5 is accepted`() {
        val scale = (0..10).map { it / 2.0 }
        for (score in scale) assertEquals(score, grade(score = score).score)
        assertEquals(11, scale.size)
    }

    @Test
    fun `confidence accepts both ends of the unit interval`() {
        assertEquals(0.0, grade(confidence = 0.0).confidence)
        assertEquals(1.0, grade(confidence = 1.0).confidence)
    }

    @Test
    fun `both sources are allowed`() {
        assertEquals(GradeSource.WIN_RATE, grade(source = GradeSource.WIN_RATE).source)
        assertEquals(GradeSource.MODEL, grade(source = GradeSource.MODEL).source)
    }

    @Test
    fun `a score off the scale throws naming score`() {
        for (score in listOf(5.5, 2.3, -0.5, 0.25, Double.NaN)) {
            val e = assertFailsWith<IllegalArgumentException> { grade(score = score) }
            assertTrue("score" in e.message.orEmpty(), "$score: ${e.message}")
        }
    }

    @Test
    fun `a confidence outside 0 to 1 throws naming confidence`() {
        for (confidence in listOf(-0.01, 1.01, 2.0, Double.NaN)) {
            val e = assertFailsWith<IllegalArgumentException> { grade(confidence = confidence) }
            assertTrue("confidence" in e.message.orEmpty(), "$confidence: ${e.message}")
        }
    }

    @Test
    fun `a blank provenance throws naming provenance`() {
        for (provenance in listOf("", "  ", "\t")) {
            val e = assertFailsWith<IllegalArgumentException> { grade(provenance = provenance) }
            assertTrue("provenance" in e.message.orEmpty(), "'$provenance': ${e.message}")
        }
    }
}
