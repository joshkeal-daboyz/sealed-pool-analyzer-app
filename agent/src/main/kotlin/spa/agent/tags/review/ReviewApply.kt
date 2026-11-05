package spa.agent.tags.review

import spa.agent.tags.CardFileIds

/**
 * What applying a review gives: the corrected [cards], the [applied] corrections in the order they
 * applied, and a line for each correction that could not apply ([problems]).
 */
data class ReviewApplied(
    val cards: List<ReviewCard>,
    val applied: List<Correction>,
    val problems: List<String>,
)

/**
 * Applies a review's corrections to a tag file's cards, each through the [ReviewAction] its
 * `action` names.
 *
 * The corrections apply in file order (the review files in their card files' order), then card
 * order (a card's place in its card file); a card's own corrections keep the order its review file
 * gives them, and each applies to the card as corrected so far. A correction must name a card of
 * the card file its review file sits beside; one that does not, or that its action rejects, is a
 * problem line, `<review file>: correction <n>: <why>`, and changes nothing.
 */
object ReviewApply {
    fun apply(
        cards: List<ReviewCard>,
        cardFiles: List<CardFileIds>,
        corrections: List<Correction>,
        actions: List<ReviewAction> = ReviewActions.ALL,
    ): ReviewApplied {
        val byName = actions.associateBy { it.name }
        val fileOf = cardFiles.associateBy { TagReviewPackets.reviewFileName(it.name) }
        val fileOrder = cardFiles.mapIndexed { i, file -> file.name to i }.toMap()
        val ordered =
            corrections.sortedWith(
                compareBy(
                    { fileOf[it.file]?.let { file -> fileOrder[file.name] } ?: Int.MAX_VALUE },
                    {
                        fileOf[it.file]?.ids?.indexOf(it.id)?.takeIf { i -> i >= 0 }
                            ?: Int.MAX_VALUE
                    },
                )
            )
        val working = cards.associateByTo(LinkedHashMap()) { it.id }
        val applied = mutableListOf<Correction>()
        val problems = mutableListOf<String>()
        ordered.forEach { correction ->
            val where = "${correction.file}: correction ${correction.number}"
            val cardFile = fileOf[correction.file]
            val card = working[correction.id]
            val action = byName[correction.action]
            val outcome =
                when {
                    cardFile == null || correction.id !in cardFile.ids || card == null ->
                        ReviewOutcome.Rejected(
                            "${correction.id} is not a card in " +
                                (cardFile?.name
                                    ?: correction.file.removeSuffix(REVIEW_SUFFIX) + ".json")
                        )
                    action == null ->
                        ReviewOutcome.Rejected("unknown action '${correction.action}'")
                    else -> action.apply(card, correction)
                }
            when (outcome) {
                is ReviewOutcome.Applied -> {
                    working[correction.id] = outcome.card
                    applied += correction
                }
                is ReviewOutcome.Rejected -> problems += "$where: ${outcome.problem}"
            }
        }
        return ReviewApplied(working.values.toList(), applied, problems)
    }

    private const val REVIEW_SUFFIX = ".review.json"
}
