package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import spa.agent.synergy.PairScore
import spa.agent.synergy.SynergyTable
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.agent.tags.TagFiles

/**
 * `spa synergy-table --set <code> [--min <score>] [--data <dir>]`: every pair of the set's tag
 * file, each card with itself included, whose synergy score is at least `--min` (default 1),
 * highest first ([SynergyTable]), then a summary line. No network or model call.
 *
 * Each pair line gives the score, each term's part by name, and the two cards' names and ids; a
 * card with itself is printed once, as `<name> (<id>) with itself`. Ties print in the tag file's
 * (collector) order of the first card, then of the second. The summary counts every pair, whatever
 * `--min` is.
 */
object SynergyTableCommand {
    private const val SEP = "  "
    private const val DEFAULT_MIN = 1.0

    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("set", "data", "min"))
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
        lines(SynergyTable(tagFile(data, set)), min).forEach(stdout::println)
        stdout.flush()
        stderr.flush()
        return ExitCode.OK
    }

    /** The tag file of [set] under [data], `<data>/tags/<code>.json`, checked. */
    internal fun tagFile(data: DataDir, set: String): TagFile {
        val path = data.root.resolve("tags").resolve("$set.json")
        if (!Files.isRegularFile(path)) {
            throw CliFailure("no tag file for set '$set': $path", ExitCode.ERROR)
        }
        val text = Commands.readFile(path, "tag file")
        val file = Commands.guard("invalid tag file $path") { TagFiles.parse(text) }
        if (file.set != set) {
            throw CliFailure("tag file $path is for set '${file.set}', not '$set'", ExitCode.ERROR)
        }
        return file
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
            table.terms.zip(pair.parts).joinToString(SEP) { (term, part) -> "${term.name} $part" }
        val cards =
            if (pair.isDiagonal) "${card(pair.first)} with itself"
            else "${card(pair.first)} with ${card(pair.second)}"
        return listOf(pair.score.toString(), parts, cards).joinToString(SEP)
    }

    private fun card(card: TagCard) = "${card.name} (${card.id})"
}
