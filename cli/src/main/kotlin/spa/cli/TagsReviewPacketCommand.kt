package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Path
import java.util.Locale
import spa.agent.facts.CardFacts
import spa.agent.grader.GradingPackets
import spa.agent.grader.GradingPool
import spa.agent.tags.TagFiles
import spa.agent.tags.TagPacketSet
import spa.agent.tags.review.ReviewPacketException
import spa.agent.tags.review.ReviewPrompts
import spa.agent.tags.review.ReviewSource
import spa.agent.tags.review.TagReviewPackets

/**
 * `spa tags-review-packet <code> --out <dir> [--tags <file>] [--prompt <version>] [--data <dir>]`:
 * writes the blind review packet (pass 3, SPA-052) for the set's tag file into `<dir>` (see
 * [TagReviewPackets]). The tag file is `--tags`, else `<data>/tags/<code>.json`; its absolute path
 * and the SHA-256 of its bytes go into the packet's `tag-file.json`, for `spa tags-review-import`.
 * `--prompt` names the review prompt version, `v1` by default. A missing or invalid tag file, one
 * for another set or not covering the set's pool, or a version with no prompt file, is one line and
 * exit 1, and nothing is written. Makes no network or model call.
 */
object TagsReviewPacketCommand {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("out", "data", "tags", "prompt"))
        val set = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val out =
            Path.of(
                parsed.value("out") ?: throw CliFailure("--out <dir> is required", ExitCode.USAGE)
            )
        val version = parsed.value("prompt") ?: ReviewPrompts.DEFAULT_VERSION
        val prompt =
            ReviewPrompts.load(version)
                ?: throw CliFailure(
                    "no tags review prompt $version: ${ReviewPrompts.resource(version)} is not " +
                        "on the classpath",
                    ExitCode.ERROR,
                )
        val data = Commands.dataDir(parsed)
        val tagsPath =
            parsed.value("tags")?.let { Path.of(it) }
                ?: data.root.resolve("tags").resolve("$set.json")
        val text = Commands.readFile(tagsPath, "tag file")
        val tags = Commands.guard("invalid tag file $tagsPath") { TagFiles.parse(text) }
        if (tags.set != set) {
            throw CliFailure(
                "tag file $tagsPath is for set '${tags.set}', not '$set'",
                ExitCode.ERROR,
            )
        }
        val index = Commands.loadIndex(data, set)
        val brief = GradePacketCommand.loadBrief(data.briefFile(set))
        val themes = GradePacketCommand.packetThemes(data, index, brief)
        val source =
            ReviewSource(
                tagsPath.toAbsolutePath().normalize().toString(),
                TagReviewPackets.sha256(text.toByteArray(Charsets.UTF_8)),
            )
        val packetSet = TagPacketSet(index, brief, themes, CardFacts.load(data.scryfall, index))
        val packet =
            try {
                TagReviewPackets.build(packetSet, prompt, tags, source)
            } catch (e: ReviewPacketException) {
                throw CliFailure(e.message.orEmpty(), ExitCode.ERROR, e)
            }
        try {
            TagReviewPackets.write(packet, out)
        } catch (e: IOException) {
            throw CliFailure("cannot write the packet to $out: ${e.message}", ExitCode.ERROR, e)
        }
        val cardFiles = packet.files.count { GradingPackets.isCardFile(it.name) }
        val cards = GradingPool.printings(index).size
        val plural = if (cardFiles == 1) "" else "s"
        stdout.println("$set: $cards cards in $cardFiles card file$plural, ${tags.axes.size} axes")
        stdout.println("wrote $out")
        stdout.flush()
        return ExitCode.OK
    }
}
