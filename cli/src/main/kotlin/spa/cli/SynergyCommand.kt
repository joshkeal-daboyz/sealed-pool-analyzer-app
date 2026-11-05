package spa.cli

import java.io.PrintStream
import spa.agent.synergy.PoolSynergy

/**
 * `spa synergy <pool-file> --set <code> --pair <XY> [--weights <file>] [--data <dir>]`: each
 * distinct candidate of the pool in the pair with its tag synergy part, syn, e, quantity and name,
 * highest part first, and under each a reason line naming its partners
 * ([spa.agent.synergy.TagSynergy]). No network or model call.
 */
object SynergyCommand {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int = TODO()

    /** The result [run] prints for [args]. Unresolved pool lines go to [stderr]. */
    fun compute(args: List<String>, stderr: PrintStream): PoolSynergy = TODO()
}
