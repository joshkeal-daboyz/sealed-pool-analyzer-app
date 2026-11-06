package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GradeLabelsTest {
    @Test
    fun `the eleven scores have the eleven labels`() {
        val expected = listOf("F", "D−", "D", "C−", "C", "C+", "B", "B+", "A−", "A", "A+")
        val actual = (0..10).map { GradeLabels.of(it / 2.0) }
        assertEquals(expected, actual)
    }

    @Test
    fun `a score off the scale throws`() {
        listOf(-0.5, 5.5, 0.25, 3.1, Double.NaN).forEach { score ->
            assertFailsWith<IllegalArgumentException>("score $score") { GradeLabels.of(score) }
        }
    }
}
