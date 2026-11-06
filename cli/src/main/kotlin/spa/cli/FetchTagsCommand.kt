package spa.cli

import java.io.PrintStream
import java.nio.file.Path

/** `spa fetch-tags --set <code> [--bulk <file>] [--data <dir>]` (SPA-054). */
class FetchTagsCommand(
    private val source: () -> OtagSource,
    private val cacheDir: Path = Path.of(".cache", "otags"),
) {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int = ExitCode.ERROR

    companion object {
        /** Scryfall's bulk data index. */
        const val BULK_INDEX = "https://api.scryfall.com/bulk-data"
    }
}
