package spa.cli

import java.io.PrintStream
import java.nio.file.Path
import spa.agent.fixing.CardFixing
import spa.agent.fixing.FixingClassifier
import spa.core.cards.CardIndex
import spa.core.cards.Pool
import spa.core.intake.PoolParser
import spa.core.text.Color

/**
 * `spa fixing [<pool-file>] --set <code> [--data <dir>]`: the colour fixing of the set's sealed
 * pool, or of a pool (G-005 deliverable 1), classed by [FixingClassifier]'s rules.
 *
 * With no pool file, one line per fixing card of the set's sealed pool, sorted by name: the name,
 * its kinds joined by `+`, its colours in WUBRG order, and `land` when its front face is a land.
 * The last line counts the fixing cards and the cards of each kind.
 *
 * With a pool file, only the pool's fixing cards, each with its quantity first, and a last line
 * giving the fixing copies and how many of them can supply each colour. Pool lines that do not
 * resolve are listed on stderr. Makes no network or model call.
 */
object FixingCommand {
    private const val SEP = "  "

    fun run(
        args: List<String>,
        stdout: PrintStream,
        stderr: PrintStream,
        classifier: FixingClassifier = FixingClassifier(),
    ): Int {
        val parsed = Args.parse(args, valueOptions = setOf("set", "data"))
        if (parsed.positionals.size > 1) {
            throw CliFailure(
                "expected at most one pool file, got ${parsed.positionals.size} arguments",
                ExitCode.USAGE,
            )
        }
        val file = parsed.positionals.firstOrNull()
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val text = file?.let { Commands.readFile(Path.of(it), "pool file") }
        val index = Commands.loadIndex(data, set)
        val lines =
            if (text == null) setLines(classifier, index)
            else poolLines(classifier, index, text, file, stderr)
        lines.forEach(stdout::println)
        stdout.flush()
        stderr.flush()
        return ExitCode.OK
    }

    private fun setLines(classifier: FixingClassifier, index: CardIndex): List<String> {
        val fixing = classifier.setFixing(index)
        val counts =
            classifier.kinds.joinToString(", ") { kind ->
                "${kind.name} ${fixing.count { kind in it.kinds }}"
            }
        return fixing.map(::line) + "${fixing.size} fixing cards: $counts"
    }

    private fun poolLines(
        classifier: FixingClassifier,
        index: CardIndex,
        text: String,
        file: String,
        stderr: PrintStream,
    ): List<String> {
        val result =
            Commands.guard("cannot parse pool file $file") { PoolParser.parse(text, index) }
        result.unresolved.forEach {
            stderr.println(
                "spa: line ${it.lineNumber} unresolved (${it.reason.code}): ${it.rawLine}"
            )
        }
        return poolLines(classifier, result.pool)
    }

    /** The pool form's lines for [pool]: each fixing card with its quantity, then the totals. */
    internal fun poolLines(classifier: FixingClassifier, pool: Pool): List<String> {
        val copies = pool.cardCounts()
        val fixing =
            pool.entries
                .map { it.printing.card }
                .distinctBy { it.oracleId }
                .mapNotNull { classifier.classify(it) }
                .sortedBy { it.card.name }
                .map { it to copies.getValue(it.card.oracleId) }
        val total = fixing.sumOf { it.second }
        val byColour =
            Color.entries.joinToString(", ") { colour ->
                "${colour.symbol} ${fixing.filter { colour in it.first.colours }.sumOf { it.second }}"
            }
        return fixing.map { (card, count) -> "$count ${line(card)}" } +
            "$total fixing copies: $byColour"
    }

    /** A fixing card's line: name, kinds, colours, and `land` for a land. */
    fun line(fixing: CardFixing): String =
        listOfNotNull(
                fixing.card.name,
                fixing.kinds.joinToString("+") { it.name },
                fixing.colours.joinToString("") { it.symbol.toString() },
                if (fixing.isLand) "land" else null,
            )
            .joinToString(SEP)
}
