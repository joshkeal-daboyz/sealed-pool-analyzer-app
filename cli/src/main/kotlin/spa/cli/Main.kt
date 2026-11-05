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
 * `grade-packet`, `grade-import`, `grades`, `themes`, `pool-grades`, `deck-pick`, `fixing`,
 * `tags-packet`, `tags-import`, `synergy-table`, `synergy`, `synergy-check`, and `facts`.
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
        |  fixing [<pool-file>] --set <code> [--data <dir>]
        |      Print each fixing card of the set's sealed pool (mana of two or more colours,
        |      basic or land-type cycling, a land search, a Treasure), sorted by name, with its
        |      kinds, the colours it can supply and 'land' for a land, then the count of each
        |      kind. With a pool file, only the pool's fixing cards with their quantities, then
        |      the copies that can supply each colour.
        |  tags-packet <code> --out <dir> [--axes <file>] [--prompt <version>] [--data <dir>]
        |      Write a blind synergy tagging packet for the set's sealed pool into <dir>. Without
        |      --axes, pass 1: the axes prompt, the format brief and theme counts, the cards in
        |      files of 50, and axes.schema.json. With --axes, pass 2: the file's axes are
        |      checked first, then the roles prompt, the axes, the brief and theme counts, the
        |      cards, and response.schema.json. --prompt picks the prompt version, default v2,
        |      whose packets add facts.json and whose axes carry suppliedBy (up to 20 axes); v1
        |      has neither (up to 15).
        |  tags-import <code> --packet <dir> --provenance <text> [--data <dir>]
        |      Check a tagger's response files in the pass-2 packet <dir> and write
        |      <data>/tags/<code>.json. Writes nothing on any problem.
        |  synergy-table --set <code> [--min <score>] [--data <dir>]
        |      Print every pair of cards of the set's tag file <data>/tags/<code>.json, each card
        |      with itself included, scoring at least --min (default 1), highest first: the score,
        |      its axis and partner parts (and fact part when an axis has suppliedBy), and the
        |      two cards. The last line counts the pairs, the nonzero pairs and diagonal, and
        |      gives the maximum.
        |  synergy <pool-file> --set <code> --pair <XY> [--weights <file>] [--data <dir>]
        |      For each pool card castable in the pair, highest first, print its tag synergy part,
        |      its syn with the rest of the pool, the e of a typical pool, its quantity and name,
        |      then a line naming its strongest partners in the pool. The weight comes from
        |      --weights, else <data>/weights/tag-synergy.json.
        |  synergy-check --set <code> [--data <dir>]
        |      Run the owner's held-out synergy checks in <data>/synergy-checks/<code>.json on
        |      the pair table of the set's tag file: one line per check, in file order, with PASS
        |      or FAIL, its id, its status (independent or seen) and its numbers, then a line
        |      counting the checks that pass and how many of those are independent.
        |  facts --set <code> [--card <name or id>] [--data <dir>]
        |      Print each card fact of the set's sealed pool (card types, legendary, creature
        |      types, keywords, cheap, enters and dies triggers, makes tokens) with the number of
        |      cards that have it, most first, then the pool size and the number of facts. With
        |      --card, that card's facts, sorted.
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
    ): Int {
        val handler = command?.let { commands(fetcher)[it] }
        return when {
            handler != null -> handler(rest, stdout, stderr)
            command == null -> usageError("no command given", stderr)
            else -> usageError("unknown command '$command'", stderr)
        }
    }

    /** Each command by name. */
    private fun commands(fetcher: () -> ScryfallSetFetcher): Map<String, Handler> =
        mapOf(
            "analyze" to { a, out, err -> AnalyzeCommand.run(a, out, err) },
            "validate-brief" to { a, out, err -> ValidateBriefCommand.run(a, out, err) },
            "fetch-set" to { a, out, _ -> FetchSetCommand(fetcher).run(a, out) },
            "winrates" to { a, out, _ -> WinratesCommand().run(a, out) },
            "grade-backtest" to { a, out, _ -> GradeBacktestCommand.run(a, out) },
            "grade-packet" to { a, out, _ -> GradePacketCommand.run(a, out) },
            "grade-import" to { a, out, err -> GradeImportCommand().run(a, out, err) },
            "grades" to { a, out, err -> GradesCommand.run(a, out, err) },
            "themes" to { a, out, _ -> ThemesCommand.run(a, out) },
            "pool-grades" to { a, out, err -> PoolGradesCommand.run(a, out, err) },
            "deck-pick" to { a, out, _ -> DeckPickCommand.run(a, out) },
            "fixing" to { a, out, err -> FixingCommand.run(a, out, err) },
            "tags-packet" to { a, out, err -> TagsPacketCommand.run(a, out, err) },
            "tags-import" to { a, out, err -> TagsImportCommand().run(a, out, err) },
            "synergy-table" to { a, out, err -> SynergyTableCommand.run(a, out, err) },
            "synergy" to { a, out, err -> SynergyCommand.run(a, out, err) },
            "synergy-check" to { a, out, err -> SynergyCheckCommand.run(a, out, err) },
            "facts" to { a, out, _ -> FactsCommand.run(a, out) },
        )

    private fun usageError(message: String, stderr: PrintStream): Int {
        stderr.println("spa: $message")
        stderr.println(USAGE)
        return ExitCode.USAGE
    }

    private val HELP = setOf("--help", "-h", "help")
}

/** One command's entry point: its arguments, stdout and stderr in, its exit code out. */
private typealias Handler = (List<String>, PrintStream, PrintStream) -> Int
