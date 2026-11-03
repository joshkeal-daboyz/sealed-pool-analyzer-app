package spa.cli

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess
import spa.core.cards.HttpClientTransport
import spa.core.cards.ScryfallSetFetcher

/** The `spa` command line: `analyze`, `validate-brief`, and `fetch-set`. */
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
        |
        |<data> defaults to ${DataDir.DEFAULT}. Exit codes: 0 ok, 1 error, 2 bad command line.
        |Run 'spa --help' for this text.
        """
            .trimMargin()

    // The ticket fixes the main class as spa.cli.Main, and the JVM entry point must be named main.
    @Suppress("MemberNameEqualsClassName")
    @JvmStatic
    fun main(args: Array<String>) {
        val stdout = PrintStream(FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8)
        val stderr = PrintStream(FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8)
        exitProcess(run(args, stdout, stderr))
    }

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
