package spa.cli

import java.io.PrintStream

/** `spa grades <pool-file> --set <code> [--data <dir>]`: a pool's cards with their grades. */
object GradesCommand {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int = TODO()

    /** The letter label of [score]. */
    fun label(score: Double): String = TODO()
}
