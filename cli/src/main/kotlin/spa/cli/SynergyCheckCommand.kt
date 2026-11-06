package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import spa.agent.synergy.check.SynergyCheckFile
import spa.agent.synergy.check.SynergyChecks

/**
 * `spa synergy-check --set <code> [--tags <file>] [--otags <file>] [--data <dir>]`: runs the
 * owner's held-out synergy checks, `<data>/synergy-checks/<code>.json`, on the pair table of the
 * set's tag file (`--tags`, else `<data>/tags/<code>.json`) and otags (`--otags`, [Otags]), and
 * prints one line per check and a summary ([SynergyChecks.lines]). The check file is read only
 * here, when the command runs. A failing check is a result and exits 0; a missing or invalid check
 * or tag file, an unknown kind or an unknown card id is one line and exit 1, and no check runs. No
 * network or model call.
 */
object SynergyCheckCommand {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("set", "data", "tags", Otags.OPTION))
        if (parsed.positionals.isNotEmpty()) {
            throw CliFailure("unexpected argument '${parsed.positionals.first()}'", ExitCode.USAGE)
        }
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val tags = SynergyTableCommand.tagFile(data, set, SynergyTableCommand.tagsOption(parsed))
        val otags = SynergyTableCommand.otags(data, set, parsed)
        val table = SynergyTableCommand.table(data, set, tags, otags)
        val path = data.root.resolve("synergy-checks").resolve("$set.json")
        val file = checkFile(path, set)
        val lines = Commands.guard("synergy check file $path") { SynergyChecks.lines(file, table) }
        lines.forEach(stdout::println)
        stdout.flush()
        stderr.flush()
        return ExitCode.OK
    }

    /** The check file of [set] at [path], checked. */
    private fun checkFile(path: Path, set: String): SynergyCheckFile {
        if (!Files.isRegularFile(path)) {
            throw CliFailure("no synergy check file for set '$set': $path", ExitCode.ERROR)
        }
        val text = Commands.readFile(path, "synergy check file")
        val file = Commands.guard("invalid synergy check file $path") { SynergyChecks.parse(text) }
        if (file.set != set) {
            throw CliFailure(
                "synergy check file $path is for set '${file.set}', not '$set'",
                ExitCode.ERROR,
            )
        }
        return file
    }
}
