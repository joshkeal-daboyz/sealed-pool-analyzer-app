package spa.cli

import java.io.PrintStream

/**
 * `spa grade-packet <code> --out <dir> [--data <dir>]`: writes the blind grading packet for the
 * set's sealed pool into `<dir>` (see [spa.agent.grader.GradingPackets]). Prints the card and file
 * counts and the directory, and exits 0. Makes no network or model call.
 */
object GradePacketCommand {
    fun run(args: List<String>, stdout: PrintStream): Int = TODO()
}
