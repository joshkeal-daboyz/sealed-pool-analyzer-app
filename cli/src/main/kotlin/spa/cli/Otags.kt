package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import spa.agent.otags.CaresTable
import spa.agent.otags.OtagFile
import spa.agent.otags.OtagTagsFile
import spa.agent.otags.cares.FableCaresFile
import spa.agent.synergy.CaresTerm

/** A set's Scryfall Tagger tags by packet id, and the cares tags they give by packet id. */
internal class OtagInputs(
    val otags: Map<String, List<String>>,
    val cares: Map<String, Map<String, Int>>,
)

/**
 * The otags the synergy commands and `facts` read (SPA-054): `--otags <file>`, else
 * `<data>/otags/<code>.json` when it exists, with the cares tags of `<data>/otags/cares.json`, and
 * (SPA-055) the cares tags Fable added, `<data>/otags/<code>.fable.json`, joined with them by
 * [CaresTerm.merged]. `--otags none` gives none, and so does a set with no otags file and no Fable
 * file; the commands then work as before.
 */
internal object Otags {
    /** `--otags none`: no otags, as for a set with no otags file. */
    const val NONE = "none"

    /** The `--otags` option's name. */
    const val OPTION = "otags"

    /** The cares table's path under [data]. */
    fun caresFile(data: DataDir): Path = data.root.resolve("otags").resolve("cares.json")

    /** The tags file of [set] under [data] (SPA-055), `<code>.tags.json`. */
    fun tagsFile(data: DataDir, set: String): Path =
        data.root.resolve("otags").resolve("$set.tags.json")

    /** The Fable cares file of [set] under [data] (SPA-055), `<code>.fable.json`. */
    fun fableFile(data: DataDir, set: String): Path =
        data.root.resolve("otags").resolve("$set.fable.json")

    /** The cares table under [data], checked; none when the file does not exist. */
    fun caresTable(data: DataDir): CaresTable {
        val path = caresFile(data)
        if (!Files.isRegularFile(path)) return CaresTable.EMPTY
        val text = Commands.readFile(path, "cares table")
        return Commands.guard("invalid cares table $path") { CaresTable.parse(text) }
    }

    /** The tags file of [set] under [data], checked and for [set]. */
    fun tags(data: DataDir, set: String): OtagTagsFile {
        val path = tagsFile(data, set)
        val file =
            Commands.guard("invalid tags file $path") {
                OtagTagsFile.parse(Commands.readFile(path, "tags file"))
            }
        if (file.set != set) {
            throw CliFailure("tags file $path is for set '${file.set}', not '$set'", ExitCode.ERROR)
        }
        return file
    }

    /** The cares tags Fable added to [set], by packet id; none when it has no Fable file. */
    fun fableCares(data: DataDir, set: String): Map<String, Map<String, Int>> {
        val path = fableFile(data, set)
        if (!Files.isRegularFile(path)) return emptyMap()
        val text = Commands.readFile(path, "Fable cares file")
        val file = Commands.guard("invalid Fable cares file $path") { FableCaresFile.parse(text) }
        if (file.set != set) {
            throw CliFailure(
                "Fable cares file $path is for set '${file.set}', not '$set'",
                ExitCode.ERROR,
            )
        }
        return file.byId()
    }

    /**
     * The otags of [set]: [given] (`--otags`) when not null, else `<data>/otags/<code>.json` when
     * it exists; null for `none` or a set with no otags file. A given file that is missing, invalid
     * or for another set is one line and exit 1. With [fable] (the default) the cares include the
     * Fable file's; a set with a Fable file but no otags file has those cares and no otags.
     */
    fun load(data: DataDir, set: String, given: String?, fable: Boolean = true): OtagInputs? {
        val path = given?.let { Path.of(it) } ?: data.root.resolve("otags").resolve("$set.json")
        if (given == NONE) return null
        if (given == null && !Files.isRegularFile(path)) {
            val added = if (fable) fableCares(data, set) else emptyMap()
            return if (added.isEmpty()) null else OtagInputs(emptyMap(), added)
        }
        return read(data, set, path, fable)
    }

    private fun read(data: DataDir, set: String, path: Path, fable: Boolean): OtagInputs {
        val text = Commands.readFile(path, "otags file")
        val file = Commands.guard("invalid otags file $path") { OtagFile.parse(text) }
        if (file.set != set) {
            throw CliFailure(
                "otags file $path is for set '${file.set}', not '$set'",
                ExitCode.ERROR,
            )
        }
        val otags = file.byId()
        val scryfall = caresTable(data).pool(otags)
        val cares = if (fable) CaresTerm.merged(scryfall, fableCares(data, set)) else scryfall
        return OtagInputs(otags, cares)
    }
}
