package spa.cli

import java.io.PrintStream
import java.nio.file.Path
import spa.agent.poolgrade.PairGrades

/**
 * `spa pool-grades <pool-file> --set <code> [--pair <XY>] [--grades <file>] [--weights <file>]
 * [--data <dir>]`: each pair's candidate cards with their set grades adjusted for this pool, and
 * one reason line per nonzero part ([spa.agent.poolgrade.PoolGrader]). No network or model call.
 */
object PoolGradesCommand {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int = TODO("SPA-038")

    /** The pairs [run] prints for [args], in print order. Unresolved pool lines go to [stderr]. */
    fun compute(args: List<String>, stderr: PrintStream): List<PairGrades> = TODO("SPA-038")

    /** The lines [run] prints for one pair. */
    fun lines(grades: PairGrades): List<String> = TODO("SPA-038")

    /**
     * The grade file used without `--grades`: `<code>.v2.json` if it exists, else `<code>.json`.
     */
    fun defaultGradesFile(data: DataDir, set: String): Path = TODO("SPA-038")
}
