package spa.agent.tags.review

import java.nio.file.Path

/** One suggested new card fact: its [fact] name, the [rule] that would give it, example cards. */
data class FactSuggestion(
    val fact: String,
    val rule: String,
    val examples: List<String>,
    val reason: String,
)

/** The well-formed corrections of one review file, and a line for each problem. */
data class ReviewFileResult(val corrections: List<Correction>, val problems: List<String>)

/** The fact suggestions of a review packet, and a line for each problem. */
data class SuggestionsResult(val suggestions: List<FactSuggestion>, val problems: List<String>)

/** Reads the files a reviewer writes. */
object ReviewFiles {
    fun read(cardFile: Path, actions: List<ReviewAction> = ReviewActions.ALL): ReviewFileResult =
        TODO()

    fun parse(
        name: String,
        text: String,
        actions: List<ReviewAction> = ReviewActions.ALL,
    ): ReviewFileResult = TODO()

    fun suggestions(dir: Path): SuggestionsResult = TODO()
}
