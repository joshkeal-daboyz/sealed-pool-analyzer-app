package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinPool
import spa.agent.build.DeckWeights
import spa.agent.deckbacktest.tune.CoordinateAscent
import spa.agent.deckbacktest.tune.DeckGrid
import spa.agent.deckbacktest.tune.TuneEvaluation

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

    /** The sample and the search limits: `--limit` (per set), `--threads` and `--rounds`. */
    private class Limits(val limit: Int?, val threads: Int, val rounds: Int)

    /** What a [BuildSession] opens: the data directory, the start weights, `--tags`, `--otags`. */
    private class Source(val data: DataDir, val start: Path, val tags: Path?, val otags: String?)

    /** What the command line asked for, checked: the sets with their Sealed files, and the rest. */
    private class Options(
        val sealed: List<Pair<String, Path>>,
        val limits: Limits,
        val out: Path,
        val source: Source,
        val grid: Path,
    )

    fun run(args: List<String>, stdout: PrintStream): Int {
        val options = options(args)
        val startText = Commands.readFile(options.source.start, "weights file")
        Commands.guard("invalid weights file ${options.source.start}") {
            DeckWeights.parse(startText)
        }
        val gridText = Commands.readFile(options.grid, "grid file")
        val grid =
            Commands.guard("invalid grid file ${options.grid}") {
                DeckGrid.parse(gridText, startText)
            }
        val tuneSets = open(options, stdout)
        val pool = ForkJoinPool(options.limits.threads)
        try {
            val evaluator = DeckTuneEvaluator(tuneSets, pool)
            val listen = { evaluation: TuneEvaluation ->
                stdout.println(evaluation.line())
                stdout.flush()
            }
            val search =
                CoordinateAscent(
                    grid,
                    startText,
                    options.limits.rounds,
                    evaluator,
                    listen,
                    { write(options.out, it) },
                )
            val began = System.nanoTime()
            val result = DeckTuneFailures.guarded { search.run() }
            val seconds = (System.nanoTime() - began) / NANOS_PER_SECOND
            DeckTuneFailures.guarded { DeckTuneReport.block(result, evaluator, seconds) }
                .forEach(stdout::println)
        } finally {
            pool.shutdown()
        }
        stdout.flush()
        return ExitCode.OK
    }

    /**
     * The options in [args], each checked: the sets first, then the files, the numbers, `--out`.
     */
    private fun options(args: List<String>): Options {
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
        val sealed = sets.zip(sealedFiles(parsed, sets))
        val limits =
            Limits(
                DeckBacktestCommand.whole(parsed, "limit"),
                DeckBacktestCommand.whole(parsed, "threads") ?: DEFAULT_THREADS,
                DeckBacktestCommand.whole(parsed, "rounds") ?: DEFAULT_ROUNDS,
            )
        val out =
            parsed.value("out")?.let(Path::of)
                ?: throw CliFailure("--out <file> is required", ExitCode.ERROR)
        val data = Commands.dataDir(parsed)
        val weights = data.root.resolve("weights")
        val start = parsed.value("start")?.let(Path::of) ?: weights.resolve("deck-default.json")
        val grid = parsed.value("grid")?.let(Path::of) ?: weights.resolve("deck-grid.json")
        val source =
            Source(data, start, SynergyTableCommand.tagsOption(parsed), parsed.value(Otags.OPTION))
        return Options(sealed, limits, out, source, grid)
    }

    /** The Sealed files, one per set in [sets], each an existing file. */
    private fun sealedFiles(parsed: Args, sets: List<String>): List<Path> {
        val files = parsed.positionals.map(Path::of)
        if (files.size != sets.size) {
            throw CliFailure(
                "${count(sets.size, "set")} given with ${count(files.size, "file")}: " +
                    "one Sealed file per set",
                ExitCode.ERROR,
            )
        }
        files.forEach {
            if (!Files.isRegularFile(it)) {
                throw CliFailure("cannot read Sealed file $it: no such file", ExitCode.ERROR)
            }
        }
        return files
    }

    /** A session for each set under the start weights, and its builds, as `deck-backtest` reads. */
    private fun open(options: Options, stdout: PrintStream): List<TuneSet> {
        val source = options.source
        val sets =
            options.sealed.map { (code, file) ->
                val session =
                    BuildSession.open(source.data, code, source.start, source.tags, source.otags)
                val selection = DeckBacktestCommand.read(file, session)
                stdout.println("$code: ${selection.summary()}")
                val builds = options.limits.limit?.let(selection::limited) ?: selection.builds
                stdout.println("$code: used ${builds.size} builds")
                TuneSet(code, session, builds)
            }
        stdout.flush()
        return sets
    }

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
}
