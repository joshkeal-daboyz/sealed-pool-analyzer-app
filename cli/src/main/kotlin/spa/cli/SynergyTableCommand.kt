package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import spa.agent.facts.CardFacts
import spa.agent.synergy.PairScore
import spa.agent.synergy.SynergyTable
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.agent.tags.TagFiles

/**
 * `spa synergy-table --set <code> [--min <score>] [--tags <file>] [--otags <file>] [--data <dir>]`:
 * every pair of the set's tag file (`--tags`, else `<data>/tags/<code>.json`), each card with
 * itself included, whose synergy score is at least `--min` (default 1), highest first
 * ([SynergyTable]), then a summary line. The otags come from `--otags` ([Otags]). No network or
 * model call.
 *
 * Each pair line gives the score, each term's part by name (a term that prints no zero, such as the
 * cares term, only when its part is not 0), and the two cards' names and ids; a card with itself is
 * printed once, as `<name> (<id>) with itself`. Ties print in the tag file's (collector) order of
 * the first card, then of the second. The summary counts every pair, whatever `--min` is.
 */
object SynergyTableCommand {
    private const val SEP = "  "
    private const val DEFAULT_MIN = 1.0

    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed =
            Args.parse(args, valueOptions = setOf("set", "data", "min", "tags", Otags.OPTION))
        if (parsed.positionals.isNotEmpty()) {
            throw CliFailure("unexpected argument '${parsed.positionals.first()}'", ExitCode.USAGE)
        }
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val min =
            parsed.value("min")?.let {
                it.toDoubleOrNull()
                    ?: throw CliFailure("--min must be a number, got '$it'", ExitCode.ERROR)
            } ?: DEFAULT_MIN
        val tags = tagFile(data, set, tagsOption(parsed))
        lines(table(data, set, tags, otags(data, set, parsed)), min).forEach(stdout::println)
        stdout.flush()
        stderr.flush()
        return ExitCode.OK
    }

    /** The `--tags <file>` the synergy commands were given, or null for the default. */
    internal fun tagsOption(parsed: Args): Path? = parsed.value("tags")?.let { Path.of(it) }

    /**
     * The tag file of [set], checked: [given] (`--tags <file>`) when not null, else
     * `<data>/tags/<code>.json`.
     */
    internal fun tagFile(data: DataDir, set: String, given: Path? = null): TagFile {
        val path = given ?: data.root.resolve("tags").resolve("$set.json")
        if (given == null && !Files.isRegularFile(path)) {
            throw CliFailure("no tag file for set '$set': $path", ExitCode.ERROR)
        }
        val text = Commands.readFile(path, "tag file")
        val file = Commands.guard("invalid tag file $path") { TagFiles.parse(text) }
        if (file.set != set) {
            throw CliFailure("tag file $path is for set '${file.set}', not '$set'", ExitCode.ERROR)
        }
        return file
    }

    /** The otags of [set] the synergy commands read: `--otags`, else the set's file if any. */
    internal fun otags(data: DataDir, set: String, parsed: Args): OtagInputs? =
        Otags.load(data, set, parsed.value(Otags.OPTION))

    /**
     * The pair table of [tags], the tag file of [set]. When an axis has `suppliedBy` facts (tags
     * prompt v2) or [otags] are given (SPA-054), the set's card facts are worked out from the
     * Scryfall cache under [data], with each card's otag facts, for the fact supply and cares
     * terms; a v1 file with no otags needs no cache and scores exactly as before.
     */
    internal fun table(
        data: DataDir,
        set: String,
        tags: TagFile,
        otags: OtagInputs? = null,
    ): SynergyTable {
        val supplied = tags.axes.any { !it.suppliedBy.isNullOrEmpty() }
        val facts =
            if (supplied || otags != null) {
                CardFacts.load(
                    data.scryfall,
                    Commands.loadIndex(data, set),
                    otags = otags?.otags.orEmpty(),
                )
            } else {
                emptyMap()
            }
        return SynergyTable(tags, facts = facts, cares = otags?.cares.orEmpty())
    }

    /** The lines [run] prints for [table] and [min]. */
    fun lines(table: SynergyTable, min: Double): List<String> {
        val pairs = table.pairs()
        // pairs() is in tag file order of first then second card, and the sort is stable.
        val shown =
            pairs.filter { it.score >= min }.sortedByDescending { it.score }.map { line(table, it) }
        val n = table.cards.size
        val summary =
            "$n cards, ${pairs.size} pairs with the diagonal, " +
                "${pairs.count { it.score != 0 }} nonzero, " +
                "${pairs.count { it.isDiagonal && it.score != 0 }} nonzero on the diagonal, " +
                "maximum ${pairs.maxOfOrNull { it.score } ?: 0}"
        return shown + summary
    }

    private fun line(table: SynergyTable, pair: PairScore): String {
        val parts =
            table.terms
                .zip(pair.parts)
                .filter { (term, part) -> term.printsZero || part != 0 }
                .joinToString(SEP) { (term, part) -> "${term.name} $part" }
        val cards =
            if (pair.isDiagonal) "${card(pair.first)} with itself"
            else "${card(pair.first)} with ${card(pair.second)}"
        return listOf(pair.score.toString(), parts, cards).joinToString(SEP)
    }

    private fun card(card: TagCard) = "${card.name} (${card.id})"
}
