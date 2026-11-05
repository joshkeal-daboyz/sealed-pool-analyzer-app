package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Path
import java.util.Locale
import spa.agent.grader.GradingPackets
import spa.agent.grader.GradingPool
import spa.agent.grader.PacketThemes
import spa.agent.themes.PoolThemes
import spa.core.brief.BriefLoader
import spa.core.brief.BriefValidationException
import spa.core.brief.FormatBrief
import spa.core.cards.CardIndex

/**
 * `spa grade-packet <code> --out <dir> [--data <dir>] [--prompt <file>] [--themes]`: writes the
 * blind grading packet for the set's sealed pool into `<dir>` (see
 * [spa.agent.grader.GradingPackets]). `--prompt` puts that file's text in place of prompt v1;
 * `--themes` adds the set's theme counts to the instructions and each card's themes to its entry
 * (see [spa.agent.grader.PacketThemes]). Prints the card and file counts and the directory, and
 * exits 0. Makes no network or model call.
 */
object GradePacketCommand {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed =
            Args.parse(
                args,
                valueOptions = setOf("out", "data", "prompt"),
                flagOptions = setOf("themes"),
            )
        val set = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val out =
            Path.of(
                parsed.value("out") ?: throw CliFailure("--out <dir> is required", ExitCode.USAGE)
            )
        val data = Commands.dataDir(parsed)
        val index = Commands.loadIndex(data, set)
        val brief = loadBrief(data.briefFile(set))
        val prompt = parsed.value("prompt")?.let { loadPrompt(Path.of(it)) }
        val themes = if (parsed.has("themes")) packetThemes(data, index, brief) else null
        val packet =
            GradingPackets.build(
                index,
                brief,
                prompt = prompt ?: GradingPackets.promptText,
                themes = themes,
            )
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

    /** The prompt file's text; a missing, unreadable or blank file is a [CliFailure]. */
    private fun loadPrompt(path: Path): String {
        val text = Commands.readFile(path, "prompt")
        if (text.isBlank()) throw CliFailure("prompt $path is empty", ExitCode.ERROR)
        return text
    }

    /** The packet themes of [index]'s sealed pool with [brief]'s mechanics, as `--themes` adds. */
    internal fun packetThemes(data: DataDir, index: CardIndex, brief: FormatBrief): PacketThemes {
        // loadIndex has just read the same cache files, so they exist and parse.
        val keywords = PoolThemes.keywords(data.scryfall, index.setCodes)
        return PacketThemes.of(PoolThemes.cards(index, keywords), brief.mechanics.map { it.name })
    }

    /** The brief at [path]; a missing, unreadable or invalid brief is a [CliFailure]. */
    internal fun loadBrief(path: Path): FormatBrief {
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
