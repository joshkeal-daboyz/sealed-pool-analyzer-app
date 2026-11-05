package spa.cli

import java.io.PrintStream
import spa.agent.tags.review.ReviewAction
import spa.agent.tags.review.ReviewActions

/** `spa tags-review-import`. */
class TagsReviewImportCommand(private val actions: List<ReviewAction> = ReviewActions.ALL) {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int =
        throw CliFailure(
            "tags-review-import is not written yet: $actions $args $stdout $stderr",
            ExitCode.ERROR,
        )
}
