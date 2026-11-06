package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import spa.agent.otags.CaresTable
import spa.agent.otags.OtagFile

/** A set's Scryfall Tagger tags by packet id, and the cares tags they give by packet id. */
internal class OtagInputs(
    val otags: Map<String, List<String>>,
    val cares: Map<String, Map<String, Int>>,
)

/**
 * The otags the synergy commands and `facts` read (SPA-054): `--otags <file>`, else
 * `<data>/otags/<code>.json` when it exists, with the cares tags of `<data>/otags/cares.json`.
 * `--otags none`, or a set with no otags file, gives none, and the commands work as before.
 */
internal object Otags {
    /** `--otags none`: no otags, as for a set with no otags file. */
    const val NONE = "none"

    /** The `--otags` option's name. */
    const val OPTION = "otags"

    /** The cares table's path under [data]. */
    fun caresFile(data: DataDir): Path = data.root.resolve("otags").resolve("cares.json")

    /** The cares table under [data], checked; none when the file does not exist. */
    fun caresTable(data: DataDir): CaresTable {
        val path = caresFile(data)
        if (!Files.isRegularFile(path)) return CaresTable.EMPTY
        val text = Commands.readFile(path, "cares table")
        return Commands.guard("invalid cares table $path") { CaresTable.parse(text) }
    }

    /**
     * The otags of [set]: [given] (`--otags`) when not null, else `<data>/otags/<code>.json` when
     * it exists; null for `none` or a set with no otags file. A given file that is missing, invalid
     * or for another set is one line and exit 1.
     */
    fun load(data: DataDir, set: String, given: String?): OtagInputs? {
        val path = given?.let { Path.of(it) } ?: data.root.resolve("otags").resolve("$set.json")
        val none = given == NONE || (given == null && !Files.isRegularFile(path))
        return if (none) null else read(data, set, path)
    }

    private fun read(data: DataDir, set: String, path: Path): OtagInputs {
        val text = Commands.readFile(path, "otags file")
        val file = Commands.guard("invalid otags file $path") { OtagFile.parse(text) }
        if (file.set != set) {
            throw CliFailure(
                "otags file $path is for set '${file.set}', not '$set'",
                ExitCode.ERROR,
            )
        }
        val otags = file.byId()
        return OtagInputs(otags, caresTable(data).pool(otags))
    }
}
