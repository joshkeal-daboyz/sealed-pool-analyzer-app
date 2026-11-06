package spa.cli

import java.io.PrintStream
import java.nio.file.Path
import spa.agent.build.BuildConfig
import spa.core.cards.Pool
import spa.core.math.LandMath

/**
 * `spa build <pool-file> --set <code> [--colours <main[/splash]>] [--top <n>] [--weights <file>]
 * [--tags <file>] [--otags <file>] [--data <dir>]`: the best 40 card deck the pool makes, by search
 * ([spa.agent.build.DeckBuilder]); no network or model call (SPA-044, SPA-045).
 *
 * With `--colours`, the best deck in those colours: the main colours then, after a `/`, one or two
 * splash colours, such as `WU`, `WU/R` or `WUB`; `--top` is ignored. Without it, every colour
 * configuration the weights' `configs` rules give for the pool is built and ranked by score
 * ([spa.agent.build.DeckRanking]), and the top `--top` decks are printed, three by default. Weights
 * come from `--weights`, else `<data>/weights/deck-default.json`; set grades as for `pool-grades`;
 * the synergy table, read only when the synergy weight is above 0, from `--tags` and `--otags` as
 * for `synergy-table`. A pool with too few candidates, a bad `--colours` or `--top` and a bad
 * weights file are one line and exit 1. Pool lines that do not resolve are listed on stderr.
 */
object BuildCommand {
    private const val DEFAULT_TOP = 3

    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed =
            Args.parse(
                args,
                valueOptions =
                    setOf("set", "data", "colours", "weights", "tags", "top", Otags.OPTION),
            )
        val file = Commands.onePositional(parsed, "pool file")
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val config =
            parsed.value("colours")?.let { text ->
                Commands.guard("bad --colours") { BuildConfig.parse(text) }
            }
        val top = if (config == null) top(parsed) else 1
        val poolText = Commands.readFile(Path.of(file), "pool file")
        val session =
            BuildSession.open(
                data,
                set,
                parsed.value("weights")?.let(Path::of),
                SynergyTableCommand.tagsOption(parsed),
                parsed.value(Otags.OPTION),
            )
        val pool = PoolGradesCommand.pool(poolText, file, session.index, stderr)
        if (config == null) ranked(session, pool, top, stdout)
        else single(session, pool, config, stdout)
        stdout.flush()
        stderr.flush()
        return ExitCode.OK
    }

    /** `--top`: a whole number, 1 or more, three when not given. */
    internal fun top(parsed: Args): Int {
        val text = parsed.value("top") ?: return DEFAULT_TOP
        val top = text.toIntOrNull()
        if (top == null || top < 1) {
            throw CliFailure("--top must be a whole number, 1 or more: '$text'", ExitCode.ERROR)
        }
        return top
    }

    private fun ranked(session: BuildSession, pool: Pool, top: Int, stdout: PrintStream) {
        val lines = rankedLines(session, pool, top)
        if (lines.isEmpty()) {
            throw CliFailure("no configuration can be built from this pool", ExitCode.ERROR)
        }
        lines.forEach(stdout::println)
    }

    /**
     * The lines `spa build` prints for the top [top] ranked decks of [pool], a blank line between
     * decks; empty when no configuration can be built.
     */
    internal fun rankedLines(session: BuildSession, pool: Pool, top: Int): List<String> =
        session.rank(pool).take(top).flatMapIndexed { i, deck ->
            buildList {
                if (i > 0) add("")
                add(BuildLines.rank(i + 1, deck))
                addAll(BuildLines.lines(deck.setup, deck.scored))
            }
        }

    private fun single(
        session: BuildSession,
        pool: Pool,
        config: BuildConfig,
        stdout: PrintStream,
    ) {
        val setup = session.setup(pool, config)
        val built = session.builder.build(setup)
        if (built == null) {
            val minimum = LandMath.DEFAULT_DECK_SIZE - session.weights.lands.high
            val copies = setup.capacity
            throw CliFailure(
                if (copies < minimum) {
                    "too few candidates for $config: $copies copies, need $minimum spells"
                } else {
                    "no 40 card deck in $config agrees with the land rule ($copies candidate copies)"
                },
                ExitCode.ERROR,
            )
        }
        BuildLines.lines(setup, built).forEach(stdout::println)
    }
}
