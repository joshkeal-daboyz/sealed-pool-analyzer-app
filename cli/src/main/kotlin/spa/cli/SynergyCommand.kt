package spa.cli

import java.io.PrintStream
import java.nio.file.Path
import java.util.Locale
import spa.agent.grader.GradingPool
import spa.agent.synergy.CardSynergy
import spa.agent.synergy.PoolSynergy
import spa.agent.synergy.SynergyWeight
import spa.agent.synergy.TagSynergy

/**
 * `spa synergy <pool-file> --set <code> --pair <XY> [--weights <file>] [--tags <file>]
 * [--otags <file>] [--data <dir>]`: each distinct candidate of the pool in the pair with its tag
 * synergy part, syn, e, quantity and name, highest part first, and under each a reason line naming
 * its partners ([TagSynergy]). No network or model call.
 *
 * The tags come from `--tags`, else `<data>/tags/<code>.json`; the otags from `--otags` ([Otags]);
 * the weight from `--weights`, else `<data>/weights/tag-synergy.json`. A pool line that does not
 * resolve is listed on stderr and the exit stays 0.
 */
object SynergyCommand {
    private const val SEP = "  "

    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        lines(compute(args, stderr)).forEach(stdout::println)
        stdout.flush()
        stderr.flush()
        return ExitCode.OK
    }

    /** The result [run] prints for [args]. Unresolved pool lines go to [stderr]. */
    fun compute(args: List<String>, stderr: PrintStream): PoolSynergy {
        val parsed =
            Args.parse(
                args,
                valueOptions = setOf("set", "data", "pair", "weights", "tags", Otags.OPTION),
            )
        val file = Commands.onePositional(parsed, "pool file")
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val pair =
            PoolGradesCommand.pair(
                parsed.value("pair") ?: throw CliFailure("--pair <XY> is required", ExitCode.ERROR)
            )
        val text = Commands.readFile(Path.of(file), "pool file")
        val index = Commands.loadIndex(data, set)
        val tags = SynergyTableCommand.tagFile(data, set, SynergyTableCommand.tagsOption(parsed))
        val otags = SynergyTableCommand.otags(data, set, parsed)
        val weight =
            weight(
                parsed.value("weights")?.let(Path::of)
                    ?: data.root.resolve("weights").resolve("tag-synergy.json")
            )
        val pool = PoolGradesCommand.pool(text, file, index, stderr)
        return TagSynergy(
                SynergyTableCommand.table(data, set, tags, otags),
                GradingPool.printings(index),
                weight,
            )
            .score(pool, pair)
    }

    /** Each card's line, then its reason line indented by two spaces. */
    fun lines(result: PoolSynergy): List<String> =
        result.cards.flatMap { listOf(line(it), "$SEP${it.reason}") }

    /** A card's line: part, syn, e, quantity and name. */
    fun line(card: CardSynergy): String =
        listOf(
                String.format(Locale.ROOT, "%+.3f", card.value),
                "syn ${card.syn}",
                String.format(Locale.ROOT, "e %.3f", card.expected),
                "${card.quantity} ${card.printing.card.name}",
            )
            .joinToString(SEP)

    private fun weight(path: Path): SynergyWeight {
        val text = Commands.readFile(path, "weights file")
        return Commands.guard("invalid weights file $path") { SynergyWeight.parse(text) }
    }
}
