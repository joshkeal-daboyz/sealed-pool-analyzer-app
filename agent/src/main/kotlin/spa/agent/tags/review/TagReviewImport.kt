package spa.agent.tags.review

import java.nio.file.Path
import spa.agent.tags.TagCheck
import spa.agent.tags.TagChecks
import spa.agent.tags.TagFile

/**
 * What a review import gives: the corrected tag [file] and the [suggestions] when there are no
 * [problems], else no file.
 */
data class ReviewImportResult(
    val file: TagFile?,
    val suggestions: List<FactSuggestion>,
    val problems: List<String>,
)

/** Applies a review packet's corrections to the tag file it was built from. */
object TagReviewImport {
    @Suppress("LongParameterList")
    fun run(
        packetDir: Path,
        setCode: String,
        facts: Map<String, Set<String>>,
        provenance: String,
        actions: List<ReviewAction> = ReviewActions.ALL,
        checks: List<TagCheck> = TagChecks.ALL,
    ): ReviewImportResult = TODO()
}
