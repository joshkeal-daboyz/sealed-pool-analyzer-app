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
 * `build`, `deck-backtest`, `score-backtest`, `deck-tune`, `tags-packet`, `tags-import`,
 * `tags-review-packet`, `tags-review-import`, `synergy-table`, `synergy`, `synergy-check`, `facts`,
 * `fetch-tags`, `cares-packet`, and `cares-import`.
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
        |          [--weights <file>] [--tags <file>] [--data <dir>]
        |      For each colour pair (or the one --pair names, as WU or UW), highest mean first,
        |      print the pool's cards castable in it with each set grade adjusted for this pool by
        |      synergy, curve, colour depth and tag synergy, capped, and one reason line per
        |      adjustment. Set grades come from --grades, else <data>/grades/<code>.v2.json, else
        |      <code>.json; weights from --weights, else <data>/weights/default.json; tags, read
        |      only when tagSynergy is above 0, from --tags, else <data>/tags/<code>.json.
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
        |  build <pool-file> --set <code> --colours <main[/splash]> [--weights <file>] [--tags <file>]
        |          [--otags <file>] [--data <dir>]
        |  build <pool-file> --set <code> [--top <n>] [--weights <file>] [--tags <file>]
        |          [--otags <file>] [--data <dir>]
        |      With --colours, build the best 40 card deck the pool makes in those colours; without
        |      it, try every colour configuration the pool supports, rank the decks by score and
        |      print the top <n> (default 3), each headed with its rank and colours; --top is
        |      ignored with --colours. The build is by search with no model
        |      call: --colours is the main colours then, after a /, up to two splash colours (WU,
        |      WU/R, WUB). The score is the sum of the deck parts (grades, curve, creatures,
        |      removal, castability, fixing, synergy), weighted from --weights, else
        |      <data>/weights/deck-default.json. Print the heading with the score and each part's
        |      value and reason, the spells by mana value each with its grade and reason, the
        |      lands, the sideboard, and the off-colour cards by colour. Set grades come as for
        |      pool-grades; the synergy table, read only when its weight is above 0, from --tags
        |      and --otags as for synergy-table. Too few candidates for 22 spells is one line and
        |      exit 1.
        |  deck-backtest <sealed-game-data.csv.gz> --set <code> [--limit <n>] [--threads <t>]
        |          [--weights <file>] [--baseline <file>] [--tags <file>] [--otags <file>]
        |          [--data <dir>]
        |      Run the deck builder on the pool of every strong player's build (win-rate bucket 0.60
        |      or more, two to four main colours, with or without a splash) in a 17Lands Sealed game
        |      data file, and print how often it agrees with the player, overall and for decks of two
        |      colours, two plus a splash, and three or more main colours: the top deck's main
        |      colours (colour), the player's among the top three decks' (top3), the top deck
        |      splashing when the player did (splash), the mean number of the player's nonland cards
        |      the builder's deck in the player's colours plays beside the mean number registered
        |      (overlap), and a land count within one of the player's (lands). The same numbers
        |      follow for a baseline builder with --baseline, else
        |      <data>/weights/deck-baseline.json (set grades alone, two-colour pairs). --limit n
        |      keeps n builds spread evenly through the kept ones; --threads (default 2) changes
        |      the time, never a number. Weights default as for build.
        |  score-backtest <sealed-game-data.csv.gz> --set <code> [--limit <n>] [--weights <file>]
        |          [--baseline <file>] [--tags <file>] [--otags <file>] [--data <dir>]
        |      Score the deck each player registered, in every build of a 17Lands Sealed game data
        |      file that has a win-rate bucket, with the deck builder's parts (--weights, else
        |      <data>/weights/deck-tuned.json) and with set grades alone (--baseline, else
        |      <data>/weights/deck-baseline.json), no search. Print the builds read and skipped, the
        |      builds scored, and for each scorer the Spearman rank correlation between score and
        |      bucket with its rough standard error, the mean bucket of the top and bottom quarter
        |      of builds by score and their gap. --limit n keeps n builds spread evenly through the
        |      kept ones. A player's win rate carries the player's skill as well as the deck.
        |  deck-tune <sealed-game-data.csv.gz>... --set <code,...> --out <file> [--limit <n>]
        |          [--threads <t>] [--grid <file>] [--start <weights file>] [--rounds <r>]
        |          [--tags <file>] [--otags <file>] [--data <dir>]
        |      Tune the deck builder's weights by coordinate ascent: one Sealed file per set, in the
        |      order of --set, which may name msh and hob (fra and trk are held out). Starting from
        |      --start (default <data>/weights/deck-default.json), each round visits the knobs of
        |      --grid (default <data>/weights/deck-grid.json) in file order, tries every other
        |      value of a knob with the rest as they stand, and moves to the best if it beats the
        |      current by more than a billionth; it stops when a round changes nothing or after
        |      --rounds rounds (default 3). A weights file scores overlap share (the builder's mean
        |      main-deck overlap over the mean cards the players registered) plus colour agreement,
        |      averaged over the sets. Prints one line per evaluation, then both objectives and
        |      each set's builder lines for the start and the result; --out is written after every
        |      move. --limit is per set; --threads (default 2) changes the time, never a number.
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
        |  tags-review-packet <code> --out <dir> [--tags <file>] [--prompt <version>] [--data <dir>]
        |      Write a blind review packet for the set's tag file (--tags, else
        |      <data>/tags/<code>.json) into <dir>: the review prompt (default v1), the axes with
        |      their supply, the brief and theme counts, the cards in files of 50 with each file's
        |      current tags beside it as tags-NN.json, facts.json, tag-file.json (the tag file's
        |      path and hash), review.schema.json and fact-suggestions.schema.json.
        |  tags-review-import <code> --packet <dir> --provenance <text> [--data <dir>]
        |      Apply the reviewer's cards-NN.review.json corrections (add-role, remove-role,
        |      set-strength, add-partner, remove-partner, each with a reason) in <dir> to the tag
        |      file the packet was built from, unchanged since, check the result as tags-import
        |      does, and write <data>/tags/<code>.json with a review block. Prints the count by
        |      action and each fact suggestion. Writes nothing on any problem.
        |  synergy-table --set <code> [--min <score>] [--tags <file>] [--otags <file>]
        |          [--data <dir>]
        |      Print every pair of cards of the set's tag file, --tags or by default
        |      <data>/tags/<code>.json, each card with itself included, scoring at least --min
        |      (default 1), highest first: the score, its axis and partner parts (and fact part
        |      when an axis has suppliedBy, and cares part when it is not 0), and the two cards.
        |      The last line counts the pairs, the nonzero pairs and diagonal, and gives the
        |      maximum. The otags come from --otags, else <data>/otags/<code>.json if it exists;
        |      --otags none reads none.
        |  synergy <pool-file> --set <code> --pair <XY> [--weights <file>] [--tags <file>]
        |          [--otags <file>] [--data <dir>]
        |      For each pool card castable in the pair, highest first, print its tag synergy part,
        |      its syn with the rest of the pool, the e of a typical pool, its quantity and name,
        |      then a line naming its strongest partners in the pool. The weight comes from
        |      --weights, else <data>/weights/tag-synergy.json; the tags from --tags, else
        |      <data>/tags/<code>.json; the otags as for synergy-table.
        |  synergy-check --set <code> [--tags <file>] [--otags <file>] [--data <dir>]
        |      Run the owner's held-out synergy checks in <data>/synergy-checks/<code>.json on
        |      the pair table of the set's tag file, --tags or by default <data>/tags/<code>.json,
        |      and otags as for synergy-table: one line per check, in file order, with PASS or
        |      FAIL, its id, its status (independent or seen) and its numbers, then a line
        |      counting the checks that pass and how many of those are independent.
        |  facts --set <code> [--card <name or id>] [--otags <file>] [--data <dir>]
        |      Print each card fact of the set's sealed pool (card types, legendary, creature
        |      types, keywords, cheap, enters and dies triggers, makes tokens, and otag:<slug>
        |      for each Scryfall tag when the set has otags, as for synergy-table) with the number
        |      of cards that have it, most first, then the pool size and the number of facts.
        |      With --card, that card's facts, sorted, then its cares tags as cares:<fact>=<n>.
        |  fetch-tags --set <code> [--bulk <file>] [--data <dir>]
        |      Write <data>/otags/<code>.json, each sealed-pool card's Scryfall Tagger oracle tags
        |      with their parents, and <data>/otags/<code>.tags.json, each tag in it with its
        |      description and parents, from the Oracle Tags bulk file --bulk (.jsonl or
        |      .jsonl.gz), else the current one, downloaded to .cache/otags (needs the network).
        |      Checks every fact of <data>/otags/cares.json first, naming each unknown one, and
        |      writes nothing if any is unknown.
        |  cares-packet <code> --out <dir> [--otags <file>] [--data <dir>]
        |      Write a blind cares-gaps packet for the set's sealed pool into <dir>: the prompt
        |      and the cares table (<data>/otags/cares.json), the brief and theme counts, the
        |      cards in files of 50 with each file's current cares tags beside it as cares-NN.json,
        |      facts.json, vocabulary.json (every fact name with its description; name tags left
        |      out, from <data>/otags/<code>.tags.json) and the response schemas.
        |  cares-import <code> --packet <dir> --provenance <text> [--data <dir>]
        |      Check Fable's cards-NN.cares.json responses (cares tags to add, each with a reason)
        |      in <dir> and write <data>/otags/<code>.fable.json, which the synergy commands join
        |      with the cares table's cares tags. Prints the count of additions and each
        |      suggestion for the cares table (never applied). Writes nothing on any problem.
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
            "build" to { a, out, err -> BuildCommand.run(a, out, err) },
            "deck-backtest" to { a, out, _ -> DeckBacktestCommand.run(a, out) },
            "score-backtest" to { a, out, _ -> ScoreBacktestCommand.run(a, out) },
            "deck-tune" to { a, out, _ -> DeckTuneCommand.run(a, out) },
            "tags-packet" to { a, out, err -> TagsPacketCommand.run(a, out, err) },
            "tags-import" to { a, out, err -> TagsImportCommand().run(a, out, err) },
            "tags-review-packet" to { a, out, _ -> TagsReviewPacketCommand.run(a, out) },
            "tags-review-import" to { a, out, err -> TagsReviewImportCommand().run(a, out, err) },
            "synergy-table" to { a, out, err -> SynergyTableCommand.run(a, out, err) },
            "synergy" to { a, out, err -> SynergyCommand.run(a, out, err) },
            "synergy-check" to { a, out, err -> SynergyCheckCommand.run(a, out, err) },
            "facts" to { a, out, _ -> FactsCommand.run(a, out) },
            "fetch-tags" to
                { a, out, err ->
                    FetchTagsCommand({ ScryfallOtagSource() }).run(a, out, err)
                },
            "cares-packet" to { a, out, _ -> CaresPacketCommand.run(a, out) },
            "cares-import" to { a, out, err -> CaresImportCommand().run(a, out, err) },
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
