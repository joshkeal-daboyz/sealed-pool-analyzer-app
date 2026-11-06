package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import spa.agent.grader.GradingPool
import spa.agent.otags.cares.CaresCheck
import spa.agent.otags.cares.CaresChecks
import spa.agent.otags.cares.CaresImport
import spa.agent.otags.cares.CaresImportSet

/**
 * `spa cares-import <code> --packet <dir> --provenance <text> [--data <dir>]` (SPA-055): checks
 * Fable's `cards-NN.cares.json` responses (and `cares-table-suggestions.json`, if any) in the cares
 * packet `<dir>` against [checks], and writes `<data>/otags/<code>.fable.json` (see [CaresImport]).
 * The cares table is read from `<data>/otags/cares.json`, to refuse a suggestion for a tag already
 * in it. On any problem it prints one `spa: ` line per problem to stderr, writes nothing, and
 * exits 1. On success it prints the count of additions and each table suggestion, which are never
 * applied. Makes no network or model call.
 */
class CaresImportCommand(private val checks: List<CaresCheck> = CaresChecks.ALL) {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("packet", "provenance", "data"))
        val set = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val packet = Path.of(required(parsed, "packet", "<dir>"))
        val provenance = required(parsed, "provenance", "<text>")
        val data = Commands.dataDir(parsed)
        if (!Files.isDirectory(packet)) {
            throw CliFailure("packet directory not found: $packet", ExitCode.ERROR)
        }
        val pool = GradingPool.printings(Commands.loadIndex(data, set)).map(GradingPool::id)
        val table = Otags.caresTable(data).cares.keys
        val result =
            io("cannot read $packet") {
                CaresImport.run(packet, CaresImportSet(set, pool, table), provenance, checks)
            }
        val file = result.file
        if (file == null) {
            result.problems.forEach { stderr.println("spa: $it") }
            stderr.flush()
            return ExitCode.ERROR
        }
        val out = Otags.fableFile(data, set)
        io("cannot write $out") {
            Files.createDirectories(out.parent)
            Files.writeString(out, file.encode())
        }
        stdout.println("$set: additions ${result.additions}")
        result.suggestions.forEach { suggestion ->
            val facts = suggestion.facts.joinToString(", ") { "${it.fact} ${it.strength}" }
            stdout.println("suggestion: ${suggestion.tag}: $facts: ${suggestion.reason}")
        }
        stdout.println("wrote $out")
        stdout.flush()
        return ExitCode.OK
    }

    /** Runs [block], reporting an I/O error as `<what>: <its message>`. */
    private fun <T> io(what: String, block: () -> T): T =
        try {
            block()
        } catch (e: IOException) {
            throw CliFailure("$what: ${e.message}", ExitCode.ERROR, e)
        }

    private fun required(args: Args, name: String, what: String): String =
        args.value(name) ?: throw CliFailure("--$name $what is required", ExitCode.USAGE)
}
