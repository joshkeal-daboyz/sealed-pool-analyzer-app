package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import spa.agent.fixing.FixingClassifier
import spa.agent.grade.GradeLookup
import spa.agent.grader.GradingPool
import spa.core.baseline.MetricFlattener
import spa.core.cards.Pool
import spa.core.intake.PoolParser
import spa.core.metrics.MetricEngine

/**
 * `spa packet <pool-file> --set <code> [--data <dir>] [--format manabox|text|collector]
 * [--brief <file>] [--weights <file>] [--tags <file>] [--otags <file>|none] [--top <n>] [--out
 * <file>]` (SPA-071): one paste-ready text file for a Claude chat, worked out with no model call:
 * the pool's cards with grades ([GradesCommand]'s lookup), its numbers beside the set's baseline
 * ([AnalyzeCommand]'s), its fixing ([FixingCommand]), its strongest synergies, and the top decks
 * `spa build` finds with the weights `<data>/weights/deck-tuned.json` unless `--weights` says
 * otherwise. The text ([PacketText]) goes to `--out`, with one line `Packet written to <file>` on
 * stdout, or to stdout. Nothing is written when anything fails.
 */
object PacketCommand {
    private val OPTIONS =
        setOf("set", "data", "format", "brief", "weights", "tags", Otags.OPTION, "top", "out")

    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = OPTIONS)
        val file = Commands.onePositional(parsed, "pool file")
        val format = AnalyzeCommand.format(parsed)
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val top = BuildCommand.top(parsed)
        val tagsFile = SynergyTableCommand.tagsOption(parsed)

        val poolText = Commands.readFile(Path.of(file), "pool file")
        val weights = parsed.value("weights")?.let(Path::of) ?: data.root.resolve(TUNED)
        val hasTags = tagsFile != null || Files.isRegularFile(data.root.resolve("tags/$set.json"))
        val session =
            if (hasTags) {
                BuildSession.open(data, set, weights, tagsFile, parsed.value(Otags.OPTION))
            } else {
                BuildSession.openWithoutTags(data, set, weights)
            }
        val index = session.index
        val brief =
            AnalyzeCommand.loadBrief(parsed.value("brief")?.let(Path::of) ?: data.briefFile(set))
        val lookup = GradeLookup(GradesCommand.gradeFiles(data, set))
        val parse =
            Commands.guard("cannot parse pool file $file") {
                PoolParser.parse(poolText, index, format, brief.kitTemplate)
            }
        parse.unresolved.forEach {
            stderr.println(
                "spa: line ${it.lineNumber} unresolved (${it.reason.code}): ${it.rawLine}"
            )
        }
        val pool = parse.pool
        val baseline = AnalyzeCommand.loadBaseline(data.baselineFile(set), set, stderr)

        val decks = BuildCommand.rankedLines(session, pool, top)
        val text =
            PacketText.packet(
                PacketText.pool(set, brief.setName, pool, lookup),
                PacketText.numbers(MetricFlattener.flatten(MetricEngine.compute(pool)), baseline),
                fixing(pool),
                if (hasTags) synergy(parsed, data, session, pool, tagsFile)
                else listOf(PacketText.NO_TAGS),
                decks.ifEmpty { listOf(PacketText.NO_DECK) },
            )
        return emit(text, parsed.value("out"), stdout, stderr)
    }

    private fun fixing(pool: Pool): List<String> {
        val lines = FixingCommand.poolLines(FixingClassifier(), pool)
        return if (lines.size > 1) lines else listOf(PacketText.NO_FIXING)
    }

    /** The synergy section's lines, for a set that has a tag file. */
    private fun synergy(
        parsed: Args,
        data: DataDir,
        session: BuildSession,
        pool: Pool,
        tagsFile: Path?,
    ): List<String> {
        val set = session.set
        val tags = SynergyTableCommand.tagFile(data, set, tagsFile)
        val table =
            SynergyTableCommand.table(data, set, tags, SynergyTableCommand.otags(data, set, parsed))
        val ids =
            GradingPool.printings(session.index).associate {
                it.card.oracleId to GradingPool.id(it)
            }
        return PacketText.synergy(pool, ids, table)
    }

    private fun emit(text: String, out: String?, stdout: PrintStream, stderr: PrintStream): Int {
        if (out == null) {
            stdout.print(text)
        } else {
            try {
                Files.writeString(Path.of(out), text)
            } catch (e: IOException) {
                throw CliFailure("cannot write $out: ${e.message}", ExitCode.ERROR, e)
            }
            stdout.print("Packet written to $out\n")
        }
        stdout.flush()
        stderr.flush()
        return ExitCode.OK
    }

    private const val TUNED = "weights/deck-tuned.json"
}
