package spa.cli

import java.io.PrintStream
import java.nio.file.Path
import java.util.Locale
import spa.agent.deckbacktest.BacktestBuild
import spa.agent.deckbacktest.score.DeckScorer
import spa.agent.deckbacktest.score.RegisteredDeck
import spa.agent.deckbacktest.score.ScoreOutcome
import spa.agent.deckbacktest.score.ScoreReport
import spa.agent.deckbacktest.score.ScoreSkip
import spa.agent.deckbacktest.score.ScoreStatistic
import spa.agent.deckbacktest.score.ScoreStatistics
import spa.agent.deckbacktest.score.ScoredBuild

/**
 * `spa score-backtest <sealed-file> --set <code> [--limit <n>] [--weights <file>]
 * [--baseline <file>] [--tags <file>] [--otags <file>] [--data <dir>]` (SPA-061): scores the deck
 * each player registered, in every build of a 17Lands Sealed file with a win rate bucket, with the
 * deck builder's parts (`--weights`, default `<data>/weights/deck-tuned.json`) and with a baseline
 * (`--baseline`, default `<data>/weights/deck-baseline.json`, set grades alone), and prints how the
 * score relates to the bucket ([ScoreStatistics]). No search, no network or model call.
 */
object ScoreBacktestCommand {
    private const val NANOS_PER_SECOND = 1e9
    private const val NO_BUCKET = 0.0

    fun run(args: List<String>, stdout: PrintStream): Int = run(args, stdout, ScoreStatistics.ALL)

    /** [run], reporting [statistics] in place of the standard ones. */
    internal fun run(
        args: List<String>,
        stdout: PrintStream,
        statistics: List<ScoreStatistic>,
    ): Int {
        val parsed =
            Args.parse(
                args,
                valueOptions =
                    setOf("set", "data", "limit", "weights", "baseline", "tags", "otags"),
            )
        val file = Path.of(Commands.onePositional(parsed, "Sealed file"))
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val limit = DeckBacktestCommand.whole(parsed, "limit")
        val tags = SynergyTableCommand.tagsOption(parsed)
        val otags = parsed.value(Otags.OPTION)
        val weights =
            parsed.value("weights")?.let(Path::of)
                ?: data.root.resolve("weights").resolve("deck-tuned.json")
        val baselineFile =
            parsed.value("baseline")?.let(Path::of)
                ?: data.root.resolve("weights").resolve("deck-baseline.json")
        val builder = BuildSession.open(data, set, weights, tags, otags)
        val baseline = BuildSession.open(data, set, baselineFile, tags, otags)

        val selection = DeckBacktestCommand.read(file, builder, NO_BUCKET)
        val builds = limit?.let(selection::limited) ?: selection.builds
        val start = System.nanoTime()
        val skips = ScoreSkip.entries.associateWith { 0 }.toMutableMap()
        val builderScored = mutableListOf<ScoredBuild>()
        val baselineScored = mutableListOf<ScoredBuild>()
        for (build in builds) {
            val outcome =
                RegisteredDeck.score(build, listOf(scorer(builder, build), scorer(baseline, build)))
            when (outcome) {
                is ScoreOutcome.Skipped -> skips.merge(outcome.reason, 1, Int::plus)
                is ScoreOutcome.Scored -> {
                    builderScored += ScoredBuild(outcome.scores[0], build.bucket)
                    baselineScored += ScoredBuild(outcome.scores[1], build.bucket)
                }
            }
        }
        val seconds = (System.nanoTime() - start) / NANOS_PER_SECOND

        stdout.println(ScoreReport.readLine(selection, skips))
        stdout.println("scored ${builderScored.size} builds")
        stdout.println(ScoreReport.line("builder", builderScored, statistics))
        stdout.println(ScoreReport.line("baseline", baselineScored, statistics))
        stdout.println(String.format(Locale.ROOT, "time %.1f s", seconds))
        stdout.flush()
        return ExitCode.OK
    }

    /**
     * How [session] scores a deck of [build]'s pool; the pool's candidates are read on first use.
     */
    internal fun scorer(session: BuildSession, build: BacktestBuild): DeckScorer {
        val candidates by lazy { session.candidates(build.pool()) }
        return DeckScorer(session.builder) { session.setup(candidates, it) }
    }
}
