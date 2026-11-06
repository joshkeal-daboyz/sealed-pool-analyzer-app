package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import spa.agent.grader.GradingPool
import spa.agent.otags.OtagBulk
import spa.agent.otags.OtagFile
import spa.agent.otags.OtagTagsFile

/**
 * `spa fetch-tags --set <code> [--bulk <file>] [--data <dir>]` (SPA-054): writes the set's otags
 * file, `<data>/otags/<code>.json` ([OtagFile]), and (SPA-055) its tags file,
 * `<data>/otags/<code>.tags.json` ([OtagTagsFile]: each tag in it with its description and
 * parents), from Scryfall's Oracle Tags bulk file: `--bulk` (JSON lines, plain or gzipped), else
 * the current file, downloaded through [source] into [cacheDir] and dated by Scryfall. Only the
 * download uses the network.
 *
 * It first checks every fact of `<data>/otags/cares.json` against the code fact families and the
 * bulk file's tags; an unknown one is named on stderr, one line each, and nothing is written (exit
 * 1). The file's `source` names the bulk file and its `updatedAt` is the bulk file's last-modified
 * time, which a download sets to Scryfall's `updated_at`; so the same bulk file always gives the
 * same bytes.
 */
class FetchTagsCommand(
    private val source: () -> OtagSource,
    private val cacheDir: Path = Path.of(".cache", "otags"),
) {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("set", "data", "bulk"))
        if (parsed.positionals.isNotEmpty()) {
            throw CliFailure("unexpected argument '${parsed.positionals.first()}'", ExitCode.USAGE)
        }
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val cares = Otags.caresTable(data)
        val path = parsed.value("bulk")?.let { Path.of(it) } ?: download()
        val bulk = read(path)
        val unknown = cares.unknown(bulk.slugs)
        if (unknown.isNotEmpty()) {
            unknown.forEach { stderr.println("spa: ${Otags.caresFile(data)}: $it") }
            stderr.flush()
            return ExitCode.ERROR
        }
        val index = Commands.loadIndex(data, set)
        val file =
            OtagFile.build(
                set,
                GradingPool.printings(index),
                bulk,
                "$SOURCE, ${path.fileName}",
                updatedAt(path),
            )
        val out = data.root.resolve("otags").resolve("$set.json")
        write(out, file.encode())
        val tags = OtagTagsFile.build(file, bulk)
        val tagsOut = Otags.tagsFile(data, set)
        write(tagsOut, tags.encode())
        stdout.println(
            "$set: ${file.cards.size} cards, ${file.tagged} with tags, " +
                "${file.total} tags with parents, ${file.distinct} distinct, written to $out"
        )
        stdout.println(
            "$set: ${tags.tags.size} tags, ${tags.described} with a description, " +
                "written to $tagsOut"
        )
        stdout.flush()
        return ExitCode.OK
    }

    private fun read(path: Path): OtagBulk {
        if (!Files.isRegularFile(path)) {
            throw CliFailure("bulk file not found: $path", ExitCode.ERROR)
        }
        return try {
            Commands.guard("invalid bulk file $path") { OtagBulk.read(path) }
        } catch (e: IOException) {
            throw CliFailure("cannot read bulk file $path: ${e.message}", ExitCode.ERROR, e)
        }
    }

    /** The bulk file's last-modified time, to the second, in UTC. */
    private fun updatedAt(path: Path): String =
        Files.getLastModifiedTime(path).toInstant().truncatedTo(ChronoUnit.SECONDS).toString()

    private fun write(path: Path, text: String) {
        try {
            Files.createDirectories(path.parent)
            Files.writeString(path, text)
        } catch (e: IOException) {
            throw CliFailure("cannot write $path: ${e.message}", ExitCode.ERROR, e)
        }
    }

    /**
     * Downloads the current Oracle Tags file named by Scryfall's bulk data index into [cacheDir],
     * with its last-modified time set to the index's `updated_at`, and returns its path.
     */
    private fun download(): Path =
        try {
            val scryfall = source()
            val index =
                scryfall.get(URI.create(BULK_INDEX)).use {
                    String(it.readAllBytes(), StandardCharsets.UTF_8)
                }
            val (uri, updated) = entry(index)
            Files.createDirectories(cacheDir)
            val file = cacheDir.resolve(uri.path.substringAfterLast('/'))
            val part = cacheDir.resolve("${file.fileName}.part")
            scryfall.get(uri).use { Files.copy(it, part, StandardCopyOption.REPLACE_EXISTING) }
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING)
            Files.setLastModifiedTime(file, FileTime.from(updated))
            file
        } catch (e: IOException) {
            throw CliFailure(
                "network error fetching Scryfall's oracle tags: ${e.message}",
                ExitCode.ERROR,
                e,
            )
        }

    /** The `oracle_tags` entry of the bulk data [index]: its JSON lines URI and `updated_at`. */
    private fun entry(index: String): Pair<URI, Instant> {
        val entry =
            entries(index).firstOrNull {
                text(it, "type") == TYPE && text(it, "jsonl_download_uri") != null
            }
                ?: throw CliFailure(
                    "Scryfall's bulk data index has no $TYPE file with a jsonl_download_uri",
                    ExitCode.ERROR,
                )
        val updated =
            try {
                OffsetDateTime.parse(text(entry, "updated_at").orEmpty()).toInstant()
            } catch (e: DateTimeParseException) {
                throw CliFailure("invalid updated_at in Scryfall's $TYPE entry", ExitCode.ERROR, e)
            }
        return URI.create(text(entry, "jsonl_download_uri")) to updated
    }

    /** The objects of the bulk data [index]'s `data` list (none when it has none). */
    private fun entries(index: String): List<JsonObject> {
        val data =
            try {
                (Json.parseToJsonElement(index) as? JsonObject)?.get("data") as? JsonArray
            } catch (e: SerializationException) {
                throw CliFailure(
                    "invalid Scryfall bulk data index: ${e.message}",
                    ExitCode.ERROR,
                    e,
                )
            }
        return data.orEmpty().filterIsInstance<JsonObject>()
    }

    private fun text(entry: JsonObject, key: String): String? =
        (entry[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        /** Scryfall's bulk data index. */
        const val BULK_INDEX = "https://api.scryfall.com/bulk-data"

        /** The index entry of the Oracle Tags file. */
        const val TYPE = "oracle_tags"

        /** The start of an otags file's `source`, before the bulk file's name. */
        const val SOURCE = "Scryfall bulk data oracle_tags"
    }
}
