package spa.cli

import java.io.PrintStream

/** `spa tags-review-packet`. */
object TagsReviewPacketCommand {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int =
        throw CliFailure(
            "tags-review-packet is not written yet: $args $stdout $stderr",
            ExitCode.ERROR,
        )
}
