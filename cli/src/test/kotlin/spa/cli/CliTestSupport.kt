package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import spa.core.cards.ScryfallSetFetcher

/** The committed fixtures directory, passed in by the test task. */
internal object Fixtures {
    val root: Path =
        Path.of(
            requireNotNull(System.getProperty("spa.fixtures")) {
                "The test task sets spa.fixtures to the fixtures directory."
            }
        )

    val data: String = root.toString()

    fun pool(name: String): String = root.resolve("pools").resolve(name).toString()

    fun brief(set: String): String = root.resolve("briefs").resolve("$set.json").toString()

    /**
     * The frozen v1 tag file of [set], `fixtures/tags/v1/<code>.json` (SPA-053). A test that pins a
     * number or line computed from a tag file reads this, so the live `tags/<code>.json` can
     * change.
     */
    fun v1Tags(set: String): String = root.resolve("tags/v1/$set.json").toString()

    /**
     * A data directory under [dir] holding a copy of set `syn`'s Scryfall cache and brief, and no
     * baselines, for tests that need to change what the data directory holds.
     */
    fun synDataDir(dir: Path): Path {
        val cache = dir.resolve("scryfall/syn").createDirectories()
        root.resolve("scryfall/syn").listDirectoryEntries().forEach {
            it.copyTo(cache.resolve(it.fileName.toString()))
        }
        val briefs = dir.resolve("briefs").createDirectories()
        root.resolve("briefs/syn.json").copyTo(briefs.resolve("syn.json"))
        return dir
    }
}

/** What one in-process run of the command line printed and returned. */
internal data class CliRun(val exit: Int, val stdout: String, val stderr: String) {
    /** Stdout parsed as one JSON object. */
    fun json(): JsonObject = Json.parseToJsonElement(stdout).jsonObject

    /** Stderr's non-blank lines. */
    fun stderrLines(): List<String> = stderr.lines().filter { it.isNotBlank() }

    /** Stdout's non-blank lines. */
    fun stdoutLines(): List<String> = stdout.lines().filter { it.isNotBlank() }
}

/** Runs `spa <args>` through [Main.run], with [fetcher] for `fetch-set` when given. */
internal fun runCli(vararg args: String, fetcher: (() -> ScryfallSetFetcher)? = null): CliRun {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val stdout = PrintStream(out, true, StandardCharsets.UTF_8)
    val stderr = PrintStream(err, true, StandardCharsets.UTF_8)
    val array = arrayOf(*args)
    val exit =
        if (fetcher == null) Main.run(array, stdout, stderr)
        else Main.run(array, stdout, stderr, fetcher)
    return CliRun(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8))
}

/** Writes [text] to [name] under [dir] and returns the file's path as a string. */
internal fun writeFile(dir: Path, name: String, text: String): String {
    val file = dir.resolve(name)
    Files.createDirectories(file.parent)
    Files.writeString(file, text)
    return file.toString()
}
