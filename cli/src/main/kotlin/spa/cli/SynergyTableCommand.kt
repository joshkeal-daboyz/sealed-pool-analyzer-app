package spa.cli

import java.io.PrintStream
import spa.agent.synergy.SynergyTable
import spa.agent.tags.TagFile

/**
 * `spa synergy-table --set <code> [--min <score>] [--data <dir>]`: every pair of the set's tag
 * file, each card with itself included, whose synergy score is at least `--min` (default 1),
 * highest first ([SynergyTable]), then a summary line. No network or model call.
 */
object SynergyTableCommand {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int = TODO()

    /** The tag file of [set] under [data], `<data>/tags/<code>.json`, checked. */
    internal fun tagFile(data: DataDir, set: String): TagFile = TODO()

    /** The lines [run] prints for [table] and [min]. */
    fun lines(table: SynergyTable, min: Double): List<String> = TODO()
}
