package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import spa.agent.facts.CardFacts
import spa.agent.tags.TagCheck
import spa.agent.tags.TagChecks
import spa.agent.tags.TagFiles
import spa.agent.tags.review.ReviewAction
import spa.agent.tags.review.ReviewActions
import spa.agent.tags.review.TagReviewImport

/**
 * `spa tags-review-import <code> --packet <dir> --provenance <text> [--data <dir>]`: applies a
 * reviewer's corrections in the review packet `<dir>` to the tag file the packet was built from,
 * through [actions], checks the result with [checks] (the pass-2 checks), and writes
 * `<data>/tags/<code>.json` with a `review` block (see [TagReviewImport]). The set's card facts,
 * for the supply checks, are worked out from the Scryfall cache. On any problem it prints one `spa:
 * ` line per problem to stderr, writes nothing, and exits 1. On success it prints the corrections
 * by action, each fact suggestion on its own line, and the path written. Makes no network or model
 * call.
 */
class TagsReviewImportCommand(
    private val actions: List<ReviewAction> = ReviewActions.ALL,
    private val checks: List<TagCheck> = TagChecks.ALL,
) {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("packet", "provenance", "data"))
        val set = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val packet = Path.of(required(parsed, "packet", "<dir>"))
        val provenance = required(parsed, "provenance", "<text>")
        val data = Commands.dataDir(parsed)
        if (!Files.isDirectory(packet)) {
            throw CliFailure("packet directory not found: $packet", ExitCode.ERROR)
        }
        val facts = CardFacts.load(data.scryfall, Commands.loadIndex(data, set))
        val result =
            io("cannot read $packet") {
                TagReviewImport.run(packet, set, facts, provenance, actions, checks)
            }
        val file = result.file
        if (file == null) {
            result.problems.forEach { stderr.println("spa: $it") }
            stderr.flush()
            return ExitCode.ERROR
        }
        val out = data.root.resolve("tags").resolve("$set.json")
        io("cannot write $out") {
            Files.createDirectories(out.parent)
            Files.writeString(out, TagFiles.write(file))
        }
        val review = requireNotNull(file.review)
        val reviewFiles =
            Files.list(packet).use { paths ->
                paths.filter { it.fileName.toString().matches(reviewPattern) }.count()
            }
        val plural = if (reviewFiles == 1L) "" else "s"
        val counts = review.counts.entries.joinToString(", ") { (action, n) -> "$action $n" }
        stdout.println(
            "$set: ${review.corrections.size} corrections from $reviewFiles review file$plural: " +
                counts
        )
        result.suggestions.forEach {
            stdout.println(
                "fact suggestion: ${it.fact}: ${it.rule} (examples ${it.examples.joinToString()}): " +
                    it.reason
            )
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

    private companion object {
        val reviewPattern = Regex("cards-[0-9]+\\.review\\.json")
    }
}
