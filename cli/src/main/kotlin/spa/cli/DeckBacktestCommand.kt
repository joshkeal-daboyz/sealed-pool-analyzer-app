package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import spa.agent.build.DeckRanking
import spa.agent.deckbacktest.Backtest
import spa.agent.deckbacktest.BacktestBuild
import spa.agent.deckbacktest.BacktestBuilds
import spa.agent.deckbacktest.BacktestReport
import spa.agent.deckbacktest.BacktestSelection
import spa.agent.deckbacktest.BuildOutcome
import spa.agent.deckbacktest.Measure
import spa.agent.deckbacktest.Measures
import spa.core.cards.Printing

/**
 * `spa deck-backtest <sealed-file> --set <code> [--limit <n>] [--threads <t>] [--weights <file>]
 * [--baseline <file>] [--tags <file>] [--otags <file>] [--data <dir>]`: the deck builder
 * ([BuildSession]) on the pool of every strong player build, of any colour count, in a 17Lands
 * Sealed game data file (SPA-046). For the builder, and for a baseline builder with the deck
 * weights file `--baseline` (default `<data>/weights/deck-baseline.json`), it prints how often the
 * ranked decks agree with the player, overall and by deck shape ([Measures]). `--limit n` keeps n
 * builds spread evenly through the kept ones; `--threads` (default 2) changes the time, never a
 * number. No network or model call.
 */
object DeckBacktestCommand {
    private const val DEFAULT_THREADS = 2
    private const val NANOS_PER_SECOND = 1e9
    private const val MIN_SECONDS = 1e-9

    fun run(args: List<String>, stdout: PrintStream): Int = run(args, stdout, Measures.ALL)

    /** [run], reporting [measures] in place of the standard ones. */
    internal fun run(args: List<String>, stdout: PrintStream, measures: List<Measure>): Int {
        val parsed =
            Args.parse(
                args,
                valueOptions =
                    setOf(
                        "set",
                        "data",
                        "limit",
                        "threads",
                        "weights",
                        "baseline",
                        "tags",
                        "otags",
                    ),
            )
        val file = Path.of(Commands.onePositional(parsed, "Sealed file"))
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val limit = whole(parsed, "limit")
        val threads = whole(parsed, "threads") ?: DEFAULT_THREADS
        val tags = SynergyTableCommand.tagsOption(parsed)
        val otags = parsed.value(Otags.OPTION)
        val builder =
            BuildSession.open(data, set, parsed.value("weights")?.let(Path::of), tags, otags)
        val baselineFile =
            parsed.value("baseline")?.let(Path::of)
                ?: data.root.resolve("weights").resolve("deck-baseline.json")
        val baseline = BuildSession.open(data, set, baselineFile, tags, otags)

        val selection = read(file, builder)
        stdout.println(selection.summary())
        val builds = limit?.let(selection::limited) ?: selection.builds
        val start = System.nanoTime()
        val outcomes = evaluate(builds, builder, baseline, threads)
        val seconds = (System.nanoTime() - start) / NANOS_PER_SECOND

        stdout.println(
            "used ${builds.size} builds, unbuildable ${outcomes.count { it.first.built == null }}"
        )
        BacktestReport.lines("builder", outcomes.map { it.first }, measures)
            .forEach(stdout::println)
        BacktestReport.lines("baseline", outcomes.map { it.second }, measures)
            .forEach(stdout::println)
        val rate = builds.size / maxOf(seconds, MIN_SECONDS)
        stdout.println(String.format(Locale.ROOT, "time %.1f s, %.2f builds/s", seconds, rate))
        stdout.flush()
        return ExitCode.OK
    }

    /** The positive whole number of option [name], or null when it is not given. */
    private fun whole(parsed: Args, name: String): Int? {
        val text = parsed.value(name) ?: return null
        val value = text.toIntOrNull()
        if (value == null || value < 1) {
            throw CliFailure("--$name must be a whole number, 1 or more: '$text'", ExitCode.ERROR)
        }
        return value
    }

    /** The builds of [file], each name resolved by full or front-face name. */
    private fun read(file: Path, session: BuildSession): BacktestSelection {
        if (!Files.isRegularFile(file)) {
            throw CliFailure("cannot read Sealed file $file: no such file", ExitCode.ERROR)
        }
        val printings = printings(session)
        return Commands.guard("cannot read Sealed file $file") {
            BacktestBuilds.read(file) { name ->
                session.index.byName(name)?.let { printings[it.oracleId] }
            }
        }
    }

    /** Each card printing: its first in the set itself, else its first in a companion. */
    private fun printings(session: BuildSession): Map<String, Printing> =
        session.index.printings
            .sortedBy { if (it.setCode == session.set) 0 else 1 }
            .groupBy { it.card.oracleId }
            .mapValues { it.value.first() }

    /**
     * The builder outcome and the baseline outcome of each of [builds], in order, on [threads]
     * threads.
     */
    private fun evaluate(
        builds: List<BacktestBuild>,
        builder: BuildSession,
        baseline: BuildSession,
        threads: Int,
    ): List<Pair<BuildOutcome, BuildOutcome>> {
        val pool = ForkJoinPool(threads)
        try {
            val work = Callable {
                builds
                    .parallelStream()
                    .map { outcome(builder, it, true) to outcome(baseline, it, false) }
                    .toList()
            }
            return pool.invoke(ForkJoinTask.adapt(work))
        } finally {
            pool.shutdown()
        }
    }

    /**
     * [build] through [session]: the ranked decks of every configuration of its pool, and the deck
     * to measure overlap and land count on, which is the one in the player own colours when [own]
     * (null when the pool cannot fill it), else the top ranked deck.
     */
    private fun outcome(session: BuildSession, build: BacktestBuild, own: Boolean): BuildOutcome {
        val candidates = session.candidates(build.pool())
        val ranked =
            DeckRanking(session.builder).rank(session.configs(candidates)) {
                session.setup(candidates, it)
            }
        val deck =
            if (own) build.config?.let { session.builder.build(session.setup(candidates, it)) }
            else ranked.firstOrNull()?.scored
        return Backtest.outcome(build.player, ranked, deck)
    }
}
