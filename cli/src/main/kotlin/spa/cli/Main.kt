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
 * `grade-packet`, and `grade-import`.
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
        |  grade-packet <code> --out <dir> [--data <dir>]
        |      Write the blind grading packet for the set's sealed pool into <dir>: instructions,
        |      the format brief, the cards in files of 50, and the response schema.
        |  grade-import <code> --responses <dir> --provenance <text> [--data <dir>]
        |      Check a grader's response files in the packet directory <dir> and write
        |      <data>/grades/<code>.json with source MODEL. Writes nothing on any problem.
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
            when (command) {
                "analyze" -> AnalyzeCommand.run(rest, stdout, stderr)
                "validate-brief" -> ValidateBriefCommand.run(rest, stdout, stderr)
                "fetch-set" -> FetchSetCommand(fetcher).run(rest, stdout)
                "winrates" -> WinratesCommand().run(rest, stdout)
                "grade-backtest" -> GradeBacktestCommand.run(rest, stdout)
                "grade-packet" -> GradePacketCommand.run(rest, stdout)
                "grade-import" -> GradeImportCommand().run(rest, stdout, stderr)
                null -> usageError("no command given", stderr)
                else -> usageError("unknown command '$command'", stderr)
            }
        } catch (e: CliFailure) {
            stderr.println("spa: ${e.message}")
            if (e.exitCode == ExitCode.USAGE) stderr.println("Run 'spa --help' for usage.")
            e.exitCode
        }
    }

    private fun usageError(message: String, stderr: PrintStream): Int {
        stderr.println("spa: $message")
        stderr.println(USAGE)
        return ExitCode.USAGE
    }

    private val HELP = setOf("--help", "-h", "help")
}
