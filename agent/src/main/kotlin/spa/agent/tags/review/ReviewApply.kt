package spa.agent.tags.review

import spa.agent.tags.CardFileIds

/**
 * What applying a review gives: the corrected [cards], the [applied] corrections, the [problems].
 */
data class ReviewApplied(
    val cards: List<ReviewCard>,
    val applied: List<Correction>,
    val problems: List<String>,
)

/** Applies a review's corrections to a tag file's cards. */
object ReviewApply {
    fun apply(
        cards: List<ReviewCard>,
        cardFiles: List<CardFileIds>,
        corrections: List<Correction>,
        actions: List<ReviewAction> = ReviewActions.ALL,
    ): ReviewApplied = TODO()
}
