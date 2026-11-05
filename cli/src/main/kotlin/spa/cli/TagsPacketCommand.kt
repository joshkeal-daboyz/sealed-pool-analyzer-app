package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.name
import spa.agent.facts.CardFacts
import spa.agent.grader.GradingPackets
import spa.agent.grader.GradingPool
import spa.agent.tags.AxesFile
import spa.agent.tags.AxesFormat
import spa.agent.tags.Axis
import spa.agent.tags.TagCheckInput
import spa.agent.tags.TagChecks
import spa.agent.tags.TagPacketSet
import spa.agent.tags.TagPackets
import spa.agent.tags.TagPrompts

/**
 * `spa tags-packet <code> --out <dir> [--axes <file>] [--prompt <version>] [--data <dir>]`: writes
 * a blind synergy tagging packet for the set's sealed pool into `<dir>` (see
 * [spa.agent.tags.TagPackets]). Without `--axes` it is pass 1, the axes packet; with it, pass 2,
 * the roles packet for the axes in that file, which is checked first: on any problem it prints one
 * `spa: ` line per problem to stderr, writes nothing, and exits 1. `--prompt` names the prompt
 * version, `v2` by default; a version with no prompt file is one line and exit 1. From v2 the
 * packets carry the card facts ([CardFacts]), and the axes file is read in the supplied format and
 * its `suppliedBy` checked against the set's facts ([TagChecks.SUPPLY]). Makes no network or model
 * call.
 */
object TagsPacketCommand {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int =
        try {
            write(args, stdout)
        } catch (e: AxesRejected) {
            e.problems.forEach { stderr.println("spa: $it") }
            stderr.flush()
            ExitCode.ERROR
        }

    /** An axes file with [problems], each printed as its own line. */
    private class AxesRejected(val problems: List<String>) : Exception()

    private fun write(args: List<String>, stdout: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("out", "data", "axes", "prompt"))
        val set = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val out =
            Path.of(
                parsed.value("out") ?: throw CliFailure("--out <dir> is required", ExitCode.USAGE)
            )
        val axesPath = parsed.value("axes")?.let { Path.of(it) }
        val pass = if (axesPath == null) TagPrompts.Pass.AXES else TagPrompts.Pass.ROLES
        val version = parsed.value("prompt") ?: TagPrompts.DEFAULT_VERSION
        val prompt = loadPrompt(pass, version)
        val format = TagPrompts.format(version)
        val parsedAxes = axesPath?.let { readAxes(it, format) }
        val data = Commands.dataDir(parsed)
        val index = Commands.loadIndex(data, set)
        val facts = if (format.supplied) CardFacts.load(data.scryfall, index) else null
        val axes = parsedAxes?.also { checkSupply(it, facts.orEmpty(), axesPath?.name.orEmpty()) }
        val brief = GradePacketCommand.loadBrief(data.briefFile(set))
        val themes = GradePacketCommand.packetThemes(data, index, brief)
        val source = TagPacketSet(index, brief, themes, facts)
        val packet =
            if (axes == null) TagPackets.axesPacket(source, prompt)
            else TagPackets.rolesPacket(source, prompt, axes)
        try {
            TagPackets.write(packet, out)
        } catch (e: IOException) {
            throw CliFailure("cannot write the packet to $out: ${e.message}", ExitCode.ERROR, e)
        }
        val cardFiles = packet.files.count { GradingPackets.isCardFile(it.name) }
        val cards = GradingPool.printings(index).size
        val plural = if (cardFiles == 1) "" else "s"
        val axesNote = axes?.let { ", ${it.size} axes" }.orEmpty()
        stdout.println("$set: $cards cards in $cardFiles card file$plural$axesNote")
        stdout.println("wrote $out")
        stdout.flush()
        return ExitCode.OK
    }

    /** The axes in the file at [path], read in [format]; any problem rejects the file. */
    private fun readAxes(path: Path, format: AxesFormat): List<Axis> {
        val result = AxesFile.parse(Commands.readFile(path, "axes file"), path.name, format)
        return result.axes ?: throw AxesRejected(result.problems)
    }

    /**
     * Runs the supply checks on [axes], read from the file [fileName], against the set's [facts]
     * (card id to its facts); any problem rejects the file. A v1 file has no supply to check.
     */
    private fun checkSupply(axes: List<Axis>, facts: Map<String, Set<String>>, fileName: String) {
        val known = facts.values.flatten().toSet()
        val input = TagCheckInput(emptyList(), axes, emptyList(), known, fileName)
        val problems = TagChecks.SUPPLY.flatMap { it.problems(input) }
        if (problems.isNotEmpty()) throw AxesRejected(problems)
    }

    /** The [pass] prompt at [version]; a version with no prompt file is a [CliFailure]. */
    private fun loadPrompt(pass: TagPrompts.Pass, version: String): String =
        TagPrompts.load(pass, version)
            ?: throw CliFailure(
                "no tags prompt $version: ${TagPrompts.resource(pass, version)} is not " +
                    "on the classpath",
                ExitCode.ERROR,
            )
}
