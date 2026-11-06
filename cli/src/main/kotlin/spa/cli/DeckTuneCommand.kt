package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ForkJoinPool
import spa.agent.build.DeckWeights
import spa.agent.deckbacktest.tune.CoordinateAscent
import spa.agent.deckbacktest.tune.DeckGrid
import spa.agent.deckbacktest.tune.TuneResult

/**
 * `spa deck-tune <sealed-file>... --set <code,...> [--limit <n>] [--threads <t>] [--grid <file>]
 * [--start <weights file>] [--rounds <r>] --out <file> [--tags <file>] [--otags <file>]
 * [--data <dir>]`: a deterministic coordinate ascent over the grid of deck weights (SPA-060), each
 * candidate scored by running the deck backtest ([DeckTuneEvaluator]) on the Sealed files, one per
 * set. The weights found are written to `--out` after every move. Only the tuning sets may be
 * named: `fra` and `trk` are held out. No network or model call.
 */
object DeckTuneCommand {
    /** The sets that may be tuned on: `msh` and `hob`, and `syn`, the tests' hand-built set. */
    private val TUNABLE = listOf("msh", "hob", "syn")
    private const val DEFAULT_THREADS = 2
    private const val DEFAULT_ROUNDS = 3
    private const val NANOS_PER_SECOND = 1e9

    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed =
            Args.parse(
                args,
                valueOptions =
                    setOf(
                        "set",
                        "data",
                        "limit",
                        "threads",
                        "grid",
                        "start",
                        "rounds",
                        "out",
                        "tags",
                        "otags",
                    ),
            )
        val sets = sets(parsed)
        val files = parsed.positionals.map(Path::of)
        if (files.size != sets.size) {
            throw CliFailure(
                "${count(sets.size, "set")} given with ${count(files.size, "file")}: " +
                    "one Sealed file per set",
                ExitCode.ERROR,
            )
        }
        val limit = DeckBacktestCommand.whole(parsed, "limit")
        val threads = DeckBacktestCommand.whole(parsed, "threads") ?: DEFAULT_THREADS
        val rounds = DeckBacktestCommand.whole(parsed, "rounds") ?: DEFAULT_ROUNDS
        val out =
            parsed.value("out")?.let(Path::of)
                ?: throw CliFailure("--out <file> is required", ExitCode.ERROR)
        files.forEach {
            if (!Files.isRegularFile(it)) {
                throw CliFailure("cannot read Sealed file $it: no such file", ExitCode.ERROR)
            }
        }
        val data = Commands.dataDir(parsed)
        val weights = data.root.resolve("weights")
        val startFile = parsed.value("start")?.let(Path::of) ?: weights.resolve("deck-default.json")
        val gridFile = parsed.value("grid")?.let(Path::of) ?: weights.resolve("deck-grid.json")
        val startText = Commands.readFile(startFile, "weights file")
        Commands.guard("invalid weights file $startFile") { DeckWeights.parse(startText) }
        val gridText = Commands.readFile(gridFile, "grid file")
        val grid =
            Commands.guard("invalid grid file $gridFile") { DeckGrid.parse(gridText, startText) }

        val tags = SynergyTableCommand.tagsOption(parsed)
        val otags = parsed.value(Otags.OPTION)
        val tuneSets = sets.mapIndexed { i, code ->
            val session = BuildSession.open(data, code, startFile, tags, otags)
            val selection = DeckBacktestCommand.read(files[i], session)
            stdout.println("$code: ${selection.summary()}")
            val builds = limit?.let(selection::limited) ?: selection.builds
            stdout.println("$code: used ${builds.size} builds")
            TuneSet(code, session, builds)
        }
        stdout.flush()

        val pool = ForkJoinPool(threads)
        try {
            val evaluator = DeckTuneEvaluator(tuneSets, pool)
            val search =
                CoordinateAscent(
                    grid,
                    startText,
                    rounds,
                    evaluator,
                    {
                        stdout.println(it.line())
                        stdout.flush()
                    },
                    { write(out, it) },
                )
            val began = System.nanoTime()
            val result = guarded { search.run() }
            val seconds = (System.nanoTime() - began) / NANOS_PER_SECOND
            val report = guarded { block(result, evaluator, seconds) }
            report.forEach(stdout::println)
        } finally {
            pool.shutdown()
        }
        stdout.flush()
        return ExitCode.OK
    }

    /** The final block: both objectives, each set builder lines for the start and the result. */
    private fun block(
        result: TuneResult,
        evaluator: DeckTuneEvaluator,
        seconds: Double,
    ): List<String> {
        val start = evaluator.run(result.startWeights)
        val final = evaluator.run(result.weights)
        val lines = mutableListOf(objectiveLine("start", result.startObjective))
        lines += objectiveLine("final", result.finalObjective)
        for ((label, evaluation) in listOf("start" to start, "final" to final)) {
            evaluation.report.forEach { (set, report) ->
                lines += "$set $label"
                lines += report
            }
        }
        lines +=
            String.format(Locale.ROOT, "evaluations %d, time %.1f s", result.evaluations, seconds)
        return lines
    }

    private fun objectiveLine(label: String, value: Double): String =
        String.format(Locale.ROOT, "%s objective %.6f", label, value)

    /** The sets of `--set`, each one that may be tuned, none twice, in the order given. */
    private fun sets(parsed: Args): List<String> {
        val sets = Commands.requiredSet(parsed).split(',').map { it.trim() }
        sets.forEach {
            if (it !in TUNABLE) {
                throw CliFailure(
                    "cannot tune on set '$it': fra and trk are held out, tune on msh or hob",
                    ExitCode.ERROR,
                )
            }
        }
        val twice = sets.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull()
        if (twice != null) throw CliFailure("set '$twice' is given twice", ExitCode.ERROR)
        return sets
    }

    private fun count(n: Int, what: String): String = "$n $what" + if (n == 1) "" else "s"

    private fun write(path: Path, text: String) {
        try {
            Files.writeString(path, text)
        } catch (e: IOException) {
            throw CliFailure("cannot write $path: ${e.message}", ExitCode.ERROR, e)
        }
    }

    /** [block], with a failure on a worker thread, or in the search, turned into a [CliFailure]. */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> guarded(block: () -> T): T =
        try {
            block()
        } catch (e: CliFailure) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw CliFailure("deck-tune: ${firstLine(e.message)}", ExitCode.ERROR, e)
        } catch (e: Exception) {
            throw oneLine(e)
        }

    /**
     * The failure [e] as one line: the first [CliFailure] among its causes as it is, else the root
     * cause, named, as `deck-tune failed: <class>: <message>`.
     */
    internal fun oneLine(e: Throwable): CliFailure {
        val causes = generateSequence(e) { it.cause }.take(MAX_CAUSES).toList()
        causes.filterIsInstance<CliFailure>().firstOrNull()?.let {
            return it
        }
        val root = causes.last()
        return CliFailure(
            "deck-tune failed: ${root.javaClass.simpleName}: ${firstLine(root.message)}",
            ExitCode.ERROR,
            e,
        )
    }

    private fun firstLine(text: String?): String = text.orEmpty().lineSequence().first()

    private const val MAX_CAUSES = 20
}
