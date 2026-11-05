package spa.cli

import java.io.PrintStream
import spa.agent.themes.ThemeCount

/**
 * `spa themes <code> [--data <dir>]`: one line per kept theme of the set's sealed pool (see
 * [spa.agent.themes.SetThemes]), most cards first. Makes no network or model call.
 */
object ThemesCommand {
    fun run(args: List<String>, stdout: PrintStream): Int = TODO()

    /** The line printed for [count]. */
    fun line(count: ThemeCount): String = TODO()
}
