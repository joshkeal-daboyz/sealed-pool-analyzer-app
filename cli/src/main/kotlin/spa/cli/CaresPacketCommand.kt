package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Path
import java.util.Locale
import spa.agent.facts.CardFacts
import spa.agent.grader.GradingPackets
import spa.agent.grader.GradingPool
import spa.agent.otags.cares.CaresPacketSet
import spa.agent.otags.cares.CaresPackets
import spa.agent.otags.cares.CaresResources
import spa.agent.tags.TagPacketSet

/**
 * `spa cares-packet <code> --out <dir> [--otags <file>] [--data <dir>]` (SPA-055): writes the blind
 * cares-gaps packet for the set's sealed pool into `<dir>` (see [CaresPackets]). The otags are
 * `--otags`, else `<data>/otags/<code>.json`; the cares come from the cares table alone, not the
 * Fable file. The set's tags file, `<data>/otags/<code>.tags.json`, gives the vocabulary's
 * descriptions and the name tags. A set with no otags file or no tags file, or an invalid or
 * foreign one, is one line and exit 1, and nothing is written. Makes no network or model call.
 */
object CaresPacketCommand {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("out", "data", Otags.OPTION))
        val set = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val out = outDir(parsed)
        val data = Commands.dataDir(parsed)
        val otags = otags(data, set, parsed.value(Otags.OPTION))
        val tags = Otags.tags(data, set)
        val index = Commands.loadIndex(data, set)
        val brief = GradePacketCommand.loadBrief(data.briefFile(set))
        val themes = GradePacketCommand.packetThemes(data, index, brief)
        val facts = CardFacts.load(data.scryfall, index, otags = otags.otags)
        val packetSet =
            CaresPacketSet(
                TagPacketSet(index, brief, themes, facts),
                otags.otags,
                Otags.caresTable(data),
                tags,
            )
        val packet = CaresPackets.build(packetSet, CaresResources.prompt)
        try {
            CaresPackets.write(packet, out)
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

    private fun outDir(parsed: Args): Path =
        Path.of(parsed.value("out") ?: throw CliFailure("--out <dir> is required", ExitCode.USAGE))

    /** The set's otags, without the Fable cares; none is one line and exit 1. */
    private fun otags(data: DataDir, set: String, given: String?): OtagInputs =
        Otags.load(data, set, given, fable = false)
            ?: throw CliFailure(
                "no otags for set '$set': run 'spa fetch-tags --set $set' to write " +
                    "${data.root.resolve("otags").resolve("$set.json")}",
                ExitCode.ERROR,
            )
}
