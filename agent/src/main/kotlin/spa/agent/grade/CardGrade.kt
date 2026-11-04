package spa.agent.grade

/** Where a grade comes from. */
enum class GradeSource {
    /** Mapped from 17Lands game-in-hand win rate within the card's set. */
    WIN_RATE,

    /** Given by a model from the card's own data and the set's format brief. */
    MODEL,
}

/**
 * One card's limited grade, relative to its set. [score] is on the shared scale, 0.0 to 5.0 in
 * steps of 0.5 (G-003 Decided 2); [confidence] is 0 to 1; [provenance] names the dataset, or the
 * model id, prompt version and date.
 */
data class CardGrade(
    val score: Double,
    val confidence: Double,
    val source: GradeSource,
    val provenance: String,
) {
    init {
        require(isOnScale(score)) {
            "score must be $MIN_SCORE to $MAX_SCORE in steps of $SCORE_STEP, got $score"
        }
        require(confidence in 0.0..1.0) { "confidence must be 0.0 to 1.0, got $confidence" }
        require(provenance.isNotBlank()) { "provenance must not be blank" }
    }

    companion object {
        const val MIN_SCORE = 0.0
        const val MAX_SCORE = 5.0
        const val SCORE_STEP = 0.5

        /** True when [score] is one of the scale's eleven steps. NaN is not. */
        fun isOnScale(score: Double): Boolean {
            val steps = score / SCORE_STEP
            return score in MIN_SCORE..MAX_SCORE && steps == Math.rint(steps)
        }
    }
}
