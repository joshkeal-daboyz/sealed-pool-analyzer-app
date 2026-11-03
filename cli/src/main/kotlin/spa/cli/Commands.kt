package spa.cli

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import spa.core.cards.CardIndex
import spa.core.cards.ScryfallCache

/**
 * The `--data` directory: Scryfall caches under `scryfall/`, briefs under `briefs/`, and baselines
 * under `baselines/`, each named by set code.
 */
class DataDir(val root: Path) {
    val scryfall: Path = root.resolve("scryfall")

    fun briefFile(set: String): Path = root.resolve("briefs").resolve("$set.json")

    fun baselineFile(set: String): Path = root.resolve("baselines").resolve("$set.json")

    companion object {
        /** The data directory when `--data` is not given. */
        const val DEFAULT = "fixtures"
    }
}

/** Steps the commands share, each turning a failure into a one-line [CliFailure]. */
object Commands {
    /** The single positional argument, named [what] in the error when it is absent or repeated. */
    fun onePositional(args: Args, what: String): String =
        args.positionals.singleOrNull()
            ?: throw CliFailure(
                if (args.positionals.isEmpty()) "missing $what"
                else "expected one $what, got ${args.positionals.size} arguments",
                ExitCode.USAGE,
            )

    /** `--set`, lowercased. Its absence exits 1, as the contract says, not 2. */
    fun requiredSet(args: Args): String =
        args.value("set")?.lowercase(Locale.ROOT)
            ?: throw CliFailure("--set <code> is required", ExitCode.ERROR)

    fun dataDir(args: Args): DataDir = DataDir(Path.of(args.value("data") ?: DataDir.DEFAULT))

    /** The text of [path], which the messages call [what]. */
    fun readFile(path: Path, what: String): String {
        if (!Files.isRegularFile(path)) throw CliFailure("$what not found: $path", ExitCode.ERROR)
        return try {
            Files.readString(path)
        } catch (e: IOException) {
            throw CliFailure("cannot read $what $path: ${e.message}", ExitCode.ERROR, e)
        }
    }

    /** The card index of [set] and its companions from the caches under [data]. */
    fun loadIndex(data: DataDir, set: String): CardIndex {
        if (!ScryfallCache.exists(data.scryfall, set)) {
            throw CliFailure(
                "no Scryfall cache for set '$set' in ${data.scryfall}; run 'spa fetch-set $set'",
                ExitCode.ERROR,
            )
        }
        return try {
            guard("cannot load the Scryfall cache for set '$set'") {
                CardIndex.load(data.scryfall, set)
            }
        } catch (e: IOException) {
            throw CliFailure(
                "cannot read the Scryfall cache for set '$set': ${e.message}",
                ExitCode.ERROR,
                e,
            )
        }
    }

    /** Runs [block], reporting an [IllegalArgumentException] as `<what>: <its message>`. */
    fun <T> guard(what: String, block: () -> T): T =
        try {
            block()
        } catch (e: IllegalArgumentException) {
            throw CliFailure("$what: ${e.message?.lineSequence()?.first()}", ExitCode.ERROR, e)
        }
}
