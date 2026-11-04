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
)
