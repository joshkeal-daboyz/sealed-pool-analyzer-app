package spa.cli

import java.io.PrintStream
import java.time.Instant

/**
 * `spa grade-import <code> --responses <dir> --provenance <text> [--data <dir>]`: checks a grader's
 * response files in the packet directory `<dir>` and writes `<data>/grades/<code>.json` with source
 * `MODEL` (see [spa.agent.grader.GradeImport]). On any problem it prints one `spa: ` line per
 * problem to stderr, writes nothing, and exits 1. Makes no network or model call.
 */
class GradeImportCommand(private val now: () -> Instant = Instant::now) {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int = TODO()
}
