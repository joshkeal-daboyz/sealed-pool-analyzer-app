package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import spa.core.cards.ScryfallCache
import spa.core.cards.ScryfallException
import spa.core.cards.ScryfallSetFetcher

/**
 * `spa fetch-set <code> [--data <dir>] [--companions a,b]`: refreshes the Scryfall cache of [code]
 * under `<data>/scryfall/` and prints its card count.
 *
 * With `--companions`, writes them to the set's `companions.json` and refreshes each of their
 * caches too. Without it, an existing `companions.json` is kept and a missing one is written as
 * `[]`. A network or API error exits 1.
 */
class FetchSetCommand(
    private val fetcher: () -> ScryfallSetFetcher,
    private val now: () -> Instant = Instant::now,
) {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("data", "companions"))
        val code = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val companions =
            parsed.value("companions")?.split(',')?.map { it.trim().lowercase(Locale.ROOT) }
        if (companions != null && companions.any { it.isEmpty() }) {
            throw CliFailure("--companions must be set codes separated by commas", ExitCode.USAGE)
        }
        val dir = Commands.dataDir(parsed).scryfall
        val scryfall = fetcher()
        fetch(scryfall, dir, code, stdout)
        writeCompanions(dir, code, companions)
        companions.orEmpty().forEach { fetch(scryfall, dir, it, stdout) }
        stdout.flush()
        return ExitCode.OK
    }

    private fun fetch(scryfall: ScryfallSetFetcher, dir: Path, code: String, stdout: PrintStream) {
        val written =
            network(code) {
                val fetched = scryfall.fetch(code)
                ScryfallCache.write(dir, fetched, now())
                fetched.cards.size
            }
        stdout.println("$code: $written cards in ${dir.resolve(code)}")
    }

    private fun writeCompanions(dir: Path, code: String, companions: List<String>?) {
        val file = dir.resolve(code).resolve(ScryfallCache.COMPANIONS_FILE)
        if (companions == null && Files.exists(file)) return
        val text = Json.encodeToString(ListSerializer(String.serializer()), companions.orEmpty())
        try {
            Files.writeString(file, text + "\n")
        } catch (e: IOException) {
            throw CliFailure("cannot write $file: ${e.message}", ExitCode.ERROR, e)
        }
    }

    /** Runs [block], reporting a network, API, or response error for [code] as exit 1. */
    private fun <T> network(code: String, block: () -> T): T =
        try {
            block()
        } catch (e: ScryfallException) {
            throw CliFailure("Scryfall error for set '$code': ${e.message}", ExitCode.ERROR, e)
        } catch (e: IOException) {
            throw CliFailure("network error for set '$code': ${e.message}", ExitCode.ERROR, e)
        } catch (e: IllegalArgumentException) {
            throw CliFailure("cannot fetch set '$code': ${e.message}", ExitCode.ERROR, e)
        }
}
