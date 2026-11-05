package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Path
import java.util.Locale
import spa.agent.grader.GradingPackets
import spa.agent.grader.GradingPool
import spa.core.brief.BriefLoader
import spa.core.brief.BriefValidationException
import spa.core.brief.FormatBrief

/**
 * `spa grade-packet <code> --out <dir> [--data <dir>]`: writes the blind grading packet for the
 * set's sealed pool into `<dir>` (see [spa.agent.grader.GradingPackets]). Prints the card and file
 * counts and the directory, and exits 0. Makes no network or model call.
 */
object GradePacketCommand {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("out", "data"))
        val set = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val out =
            Path.of(
                parsed.value("out") ?: throw CliFailure("--out <dir> is required", ExitCode.USAGE)
            )
        val data = Commands.dataDir(parsed)
        val index = Commands.loadIndex(data, set)
        val brief = loadBrief(data.briefFile(set))
        val packet = GradingPackets.build(index, brief)
        try {
            GradingPackets.write(packet, out)
        } catch (e: IOException) {
            throw CliFailure("cannot write the packet to $out: ${e.message}", ExitCode.ERROR, e)
        }
        val cardFiles = packet.files.count { GradingPackets.isCardFile(it.name) }
        val cards = GradingPool.printings(index).size
        val plural = if (cardFiles == 1) "" else "s"
        stdout.println("$set: $cards cards in $cardFiles card file$plural")
        stdout.println("wrote $out")
        stdout.flush()
        return ExitCode.OK
    }

    private fun loadBrief(path: Path): FormatBrief {
        val text = Commands.readFile(path, "brief")
        return try {
            BriefLoader.parse(text)
        } catch (e: BriefValidationException) {
            throw CliFailure(
                "brief $path is invalid (${e.errors.size} error(s)); " +
                    "run 'spa validate-brief' for the list",
                ExitCode.ERROR,
                e,
            )
        }
    }
}
