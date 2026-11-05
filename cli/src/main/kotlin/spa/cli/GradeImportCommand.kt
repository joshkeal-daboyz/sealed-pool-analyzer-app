package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import spa.agent.grade.GradeFiles
import spa.agent.grader.GradeImport
import spa.agent.grader.GradingPackets

/**
 * `spa grade-import <code> --responses <dir> --provenance <text> [--data <dir>]`: checks a grader's
 * response files in the packet directory `<dir>` and writes `<data>/grades/<code>.json` with source
 * `MODEL` (see [spa.agent.grader.GradeImport]). On any problem it prints one `spa: ` line per
 * problem to stderr, writes nothing, and exits 1. Makes no network or model call.
 */
class GradeImportCommand(private val now: () -> Instant = Instant::now) {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("responses", "provenance", "data"))
        val set = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val responses = Path.of(required(parsed, "responses", "<dir>"))
        val provenance = required(parsed, "provenance", "<text>")
        val data = Commands.dataDir(parsed)
        if (!Files.isDirectory(responses)) {
            throw CliFailure("responses directory not found: $responses", ExitCode.ERROR)
        }
        val index = Commands.loadIndex(data, set)
        val createdAt = now().truncatedTo(ChronoUnit.SECONDS).toString()
        val result =
            try {
                GradeImport.run(responses, index, set, provenance, createdAt)
            } catch (e: IOException) {
                throw CliFailure("cannot read $responses: ${e.message}", ExitCode.ERROR, e)
            }
        val file = result.file
        if (file == null) {
            result.problems.forEach { stderr.println("spa: $it") }
            stderr.flush()
            return ExitCode.ERROR
        }
        val out = data.root.resolve("grades").resolve("$set.json")
        try {
            Files.createDirectories(out.parent)
            Files.writeString(out, GradeFiles.write(file))
        } catch (e: IOException) {
            throw CliFailure("cannot write $out: ${e.message}", ExitCode.ERROR, e)
        }
        val responseFiles =
            Files.list(responses).use { paths ->
                paths.filter { GradingPackets.isCardFile(it.fileName.toString()) }.count()
            }
        val plural = if (responseFiles == 1L) "" else "s"
        stdout.println("$set: ${file.grades.size} grades from $responseFiles response file$plural")
        stdout.println("wrote $out")
        stdout.flush()
        return ExitCode.OK
    }

    private fun required(args: Args, name: String, what: String): String =
        args.value(name) ?: throw CliFailure("--$name $what is required", ExitCode.USAGE)
}
