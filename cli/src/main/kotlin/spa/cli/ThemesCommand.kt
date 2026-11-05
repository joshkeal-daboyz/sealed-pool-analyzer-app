package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import java.util.Locale
import spa.agent.themes.ColourGroup
import spa.agent.themes.PoolThemes
import spa.agent.themes.Tally
import spa.agent.themes.ThemeCount
import spa.core.brief.BriefLoader
import spa.core.brief.BriefValidationException
import spa.core.cards.Rarity

/**
 * `spa themes <code> [--data <dir>]`: one line per kept theme of the set's sealed pool (see
 * [spa.agent.themes.SetThemes]), most cards first. A set with no brief has no mechanics, so its
 * evergreen keywords are never themes. Makes no network or model call.
 */
object ThemesCommand {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("data"))
        val set = Commands.onePositional(parsed, "set code").lowercase(Locale.ROOT)
        val data = Commands.dataDir(parsed)
        val index = Commands.loadIndex(data, set)
        val mechanics = mechanics(data, set)
        // loadIndex has just read the same cache files, so they exist and parse.
        PoolThemes.count(data.scryfall, index, mechanics).forEach { stdout.println(line(it)) }
        stdout.flush()
        return ExitCode.OK
    }

    /**
     * The line printed for [count]: name, kind, the has, refers and either counts, then the either
     * count's colour and rarity splits.
     */
    fun line(count: ThemeCount): String {
        val either = count.either
        return "${count.theme.name} ${count.theme.kind.label} has=${count.has.total} " +
            "refers=${count.refers.total} either=${either.total} " +
            "colour ${colours(either)} rarity ${rarities(either)}"
    }

    private fun colours(tally: Tally): String =
        ColourGroup.entries.joinToString(" ") { "${it.label}=${tally.byColour[it] ?: 0}" }

    private fun rarities(tally: Tally): String =
        Tally.RARITIES.joinToString(" ") {
            "${RARITY_LABELS.getValue(it)}=${tally.byRarity[it] ?: 0}"
        }

    private val RARITY_LABELS =
        mapOf(
            Rarity.COMMON to "C",
            Rarity.UNCOMMON to "U",
            Rarity.RARE to "R",
            Rarity.MYTHIC to "M",
        )

    /** The brief's mechanic names, or none when the set has no brief. */
    private fun mechanics(data: DataDir, set: String): List<String> {
        val file = data.briefFile(set)
        if (!Files.isRegularFile(file)) return emptyList()
        val text = Commands.readFile(file, "brief")
        return try {
            BriefLoader.parse(text).mechanics.map { it.name }
        } catch (e: BriefValidationException) {
            throw CliFailure(
                "brief $file is invalid (${e.errors.size} error(s)); " +
                    "run 'spa validate-brief' for the list",
                ExitCode.ERROR,
                e,
            )
        }
    }
}
