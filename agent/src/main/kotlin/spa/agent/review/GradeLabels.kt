package spa.agent.review

import spa.agent.grade.CardGrade

/** The letter labels of the shared grade scale, 0.0 to 5.0 in half steps. */
object GradeLabels {
    /** The label of each step; the index is the score times two. The minus sign is U+2212. */
    private val LABELS = listOf("F", "D−", "D", "C−", "C", "C+", "B", "B+", "A−", "A", "A+")

    private const val STEPS_PER_POINT = 2

    /** The label of [score]; throws [IllegalArgumentException] when it is not on the scale. */
    fun of(score: Double): String {
        require(CardGrade.isOnScale(score)) {
            "score must be on the 0.0 to 5.0 half-step scale: $score"
        }
        return LABELS[(score * STEPS_PER_POINT).toInt()]
    }
}
