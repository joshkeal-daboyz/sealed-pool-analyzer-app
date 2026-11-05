package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import spa.agent.facts.CardFacts
import spa.agent.tags.ImportSet
import spa.agent.tags.TagCheck
import spa.agent.tags.TagChecks
import spa.agent.tags.TagFiles
import spa.agent.tags.TagImport

/**
 * `spa tags-import <code> --packet <dir> --provenance <text> [--data <dir>]`: checks a tagger's
 * responses in the pass-2 packet `<dir>` against the packet's axes and [checks], and writes
 * `<data>/tags/<code>.json` (see [spa.agent.tags.TagImport]). A v2 packet's axes keep their
 * `suppliedBy`, checked against the set's card facts ([CardFacts]), which the import works out from
 * the Scryfall cache rather than reading the packet's `facts.json`. On any problem it prints one
 * `spa: ` line per problem to stderr, writes nothing, and exits 1. Makes no network or model call.
 */
class TagsImportCommand(private val checks: List<TagCheck> = TagChecks.ALL) {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("packet", "provenance", "data"))
        val set = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val packet = Path.of(required(parsed, "packet", "<dir>"))
        val provenance = required(parsed, "provenance", "<text>")
        val data = Commands.dataDir(parsed)
        if (!Files.isDirectory(packet)) {
            throw CliFailure("packet directory not found: $packet", ExitCode.ERROR)
        }
        val index = Commands.loadIndex(data, set)
        val importSet = ImportSet(index, set, CardFacts.load(data.scryfall, index))
        val result =
            io("cannot read $packet") { TagImport.run(packet, importSet, provenance, checks) }
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
        val responseFiles = countResponses(packet)
        val plural = if (responseFiles == 1L) "" else "s"
        stdout.println(
            "$set: ${file.cards.size} cards tagged on ${file.axes.size} axes from " +
                "$responseFiles response file$plural"
        )
        stdout.println("wrote $out")
        stdout.flush()
        return ExitCode.OK
    }

    private fun countResponses(packet: Path): Long =
        Files.list(packet).use { paths ->
            paths.filter { it.fileName.toString().matches(responsePattern) }.count()
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
        val responsePattern = Regex("cards-[0-9]+\\.response\\.json")
    }
}
