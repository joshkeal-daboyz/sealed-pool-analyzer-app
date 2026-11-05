package spa.cli

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess
import spa.core.cards.HttpClientTransport
import spa.core.cards.ScryfallSetFetcher

/**
 * The `spa` command line: `analyze`, `validate-brief`, `fetch-set`, `winrates`, `grade-backtest`,
 * `grade-packet`, `grade-import`, `grades`, `themes`, `pool-grades`, and `deck-pick`.
 */
object Main {
    /** The text `spa --help` prints. */
    val USAGE =
        """
        |Usage: spa <command> [options]
        |
        |Commands:
        |  analyze <pool-file> --set <code> [--format manabox|text|collector] [--data <dir>]
        |          [--no-baseline] [--brief <file>]
        |      Parse a pool and print its metrics, kit warnings, and the set's baseline as JSON.
        |  validate-brief <file> --set <code> [--data <dir>]
        |      Check a format brief, and warn about signpost and key-common names not in the set.
        |  fetch-set <code> [--data <dir>] [--companions a,b]
        |      Refresh a set's Scryfall cache under <data>/scryfall (needs the network).
        |  winrates <game-data.csv.gz> --set <code> [--data <dir>] [--dataset-date <yyyy-mm-dd>]
        |      Derive the set's win-rate grades from a 17Lands public game data file and write
        |      <data>/winrates/<code>.json. Lists the names that match no card of the set.
        |  grade-backtest --set <code> [--model <file>] [--winrates <file>] [--data <dir>]
        |      Print the Spearman rank correlation between a model grade file (default
        |      <data>/grades/<code>.json) and a win-rate grade file (default
        |      <data>/winrates/<code>.json), over all cards and by rarity.
        |  grade-packet <code> --out <dir> [--data <dir>] [--prompt <file>] [--themes]
        |      Write the blind grading packet for the set's sealed pool into <dir>: instructions,
        |      the format brief, the cards in files of 50, and the response schema. --prompt uses
        |      <file> in place of prompt v1; --themes adds the set's theme counts and each card's
        |      themes.
        |  grade-import <code> --responses <dir> --provenance <text> [--data <dir>]
        |      Check a grader's response files in the packet directory <dir> and write
        |      <data>/grades/<code>.json with source MODEL. Writes nothing on any problem.
        |  grades <pool-file> --set <code> [--data <dir>]
        |      Print each pool entry with its grade (0.0 to 5.0), letter label (F to A+),
        |      confidence and source (WIN_RATE if <data>/winrates/<code>.json grades it, else
        |      MODEL), highest first. Entries with no grade, such as basic lands, come last
        |      with - and source none.
        |  pool-grades <pool-file> --set <code> [--pair <XY>] [--grades <file>]
        |          [--weights <file>] [--data <dir>]
        |      For each colour pair (or the one --pair names, as WU or UW), highest mean first,
        |      print the pool's cards castable in it with each set grade adjusted for this pool by
        |      synergy, curve and colour depth, capped, and one reason line per adjustment. Set
        |      grades come from --grades, else <data>/grades/<code>.v2.json, else <code>.json;
        |      weights from --weights, else <data>/weights/default.json.
        |  deck-pick <sealed-game-data.csv.gz> --set <code> [--grades <file>] [--weights <file>]
        |          [--data <dir>]
        |      For each build of a 17Lands Sealed game data file by a strong player (win-rate
        |      bucket 0.60 or more) in two colours, rank the pool's cards castable in them and
        |      print the mean share of the top N that were in the deck (N: the deck's copies of
        |      them), for a random ranking, set grades alone and pool-adjusted grades, and how
        |      many builds were skipped and why. Grades and weights default as for pool-grades.
        |  deck-pick <file>... --set <a,b,...> --tune [--grades <a,b,...>] [--out <file>]
        |          [--data <dir>]
        |      Measure every combination of the adjustment's weight grid on the files' builds
        |      pooled (one file per set) and write the best to --out, else
        |      <data>/weights/tuned.json.
        |  themes <code> [--data <dir>]
        |      Print each theme of the set's sealed pool (a keyword that is not evergreen, or a
        |      subtype) held or referred to by 3 or more cards: the cards that have it, refer to
        |      it, or either, and the either count by colour and rarity, most cards first.
        |
        |<data> defaults to ${DataDir.DEFAULT}. Exit codes: 0 ok, 1 error, 2 bad command line.
        |Run 'spa --help' for this text.
        """
            .trimMargin()

    // The ticket fixes the main class as spa.cli.Main, and the JVM entry point must be named main.
    @Suppress("MemberNameEqualsClassName")
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args, utf8(FileDescriptor.out), utf8(FileDescriptor.err)))
    }

    /** An auto-flushing UTF-8 stream onto [descriptor], so card names print the same everywhere. */
    internal fun utf8(descriptor: FileDescriptor): PrintStream =
        PrintStream(FileOutputStream(descriptor), true, StandardCharsets.UTF_8)

    /** Runs the command in [args], writing to [stdout] and [stderr], and returns the exit code. */
    fun run(args: Array<String>, stdout: PrintStream, stderr: PrintStream): Int =
        run(args, stdout, stderr) { ScryfallSetFetcher(HttpClientTransport()) }

    /** [run], with the Scryfall fetcher `fetch-set` uses supplied by [fetcher]. */
    fun run(
        args: Array<String>,
        stdout: PrintStream,
        stderr: PrintStream,
        fetcher: () -> ScryfallSetFetcher,
    ): Int {
        val command = args.firstOrNull()
        val rest = args.drop(1)
        if (command in HELP || "--help" in rest) {
            stdout.println(USAGE)
            return ExitCode.OK
        }
        return try {
            dispatch(command, rest, stdout, stderr, fetcher)
        } catch (e: CliFailure) {
            stderr.println("spa: ${e.message}")
            if (e.exitCode == ExitCode.USAGE) stderr.println("Run 'spa --help' for usage.")
            e.exitCode
        }
    }

    /** Runs [command] on [rest]; an absent or unknown command is a usage error. */
    private fun dispatch(
        command: String?,
        rest: List<String>,
        stdout: PrintStream,
        stderr: PrintStream,
        fetcher: () -> ScryfallSetFetcher,
    ): Int =
        when (command) {
            "analyze" -> AnalyzeCommand.run(rest, stdout, stderr)
            "validate-brief" -> ValidateBriefCommand.run(rest, stdout, stderr)
            "fetch-set" -> FetchSetCommand(fetcher).run(rest, stdout)
            "winrates" -> WinratesCommand().run(rest, stdout)
            "grade-backtest" -> GradeBacktestCommand.run(rest, stdout)
            "grade-packet" -> GradePacketCommand.run(rest, stdout)
            "grade-import" -> GradeImportCommand().run(rest, stdout, stderr)
            "grades" -> GradesCommand.run(rest, stdout, stderr)
            "themes" -> ThemesCommand.run(rest, stdout)
            "pool-grades" -> PoolGradesCommand.run(rest, stdout, stderr)
            "deck-pick" -> DeckPickCommand.run(rest, stdout)
            null -> usageError("no command given", stderr)
            else -> usageError("unknown command '$command'", stderr)
        }

    private fun usageError(message: String, stderr: PrintStream): Int {
        stderr.println("spa: $message")
        stderr.println(USAGE)
        return ExitCode.USAGE
    }

    private val HELP = setOf("--help", "-h", "help")
}
