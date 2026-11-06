package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import spa.agent.build.BuildConfig
import spa.agent.build.Deck
import spa.agent.grader.GradingPool
import spa.core.cards.CardIndex
import spa.core.cards.Printing
import spa.core.text.Color

/**
 * A built deck as files a ManaBox import can read (SPA-072): a `.csv` in the format of ManaBox's
 * own export, and a `.txt` of `<quantity> <name> (<SET>) <number>` lines as a fallback. One row per
 * distinct card: the spells and nonbasic lands as the pool printing the builder used, by name, then
 * the set's own basic lands in WUBRG order.
 */
internal object DeckExport {
    /** The header of ManaBox's export, which the deck file repeats. */
    const val HEADER =
        "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID," +
            "Purchase price,Misprint,Altered,Condition,Language,Purchase date," +
            "Purchase price currency"

    private val BASICS =
        listOf(
            Color.W to "Plains",
            Color.U to "Island",
            Color.B to "Swamp",
            Color.R to "Mountain",
            Color.G to "Forest",
        )

    /** [quantity] copies of one [printing], one row of the file. */
    data class Row(val printing: Printing, val quantity: Int)

    /**
     * The rows of [deck]: spells and nonbasic lands by name, then its basics. A card the deck plays
     * is found in [index] by the set and collector number of its id.
     */
    fun rows(index: CardIndex, deck: Deck): List<Row> {
        val named =
            (deck.spells + deck.nonbasics)
                .groupingBy { it.id }
                .eachCount()
                .map { (id, copies) -> Row(printing(index, id), copies) }
                .sortedWith(compareBy<Row>({ it.printing.card.name }, { it.printing.setCode }))
        val basics = BASICS.mapNotNull { (colour, name) ->
            val copies = deck.basics[colour] ?: 0
            if (copies > 0) Row(basic(index, name), copies) else null
        }
        return named + basics
    }

    private fun printing(index: CardIndex, id: String): Printing =
        index.byCollectorNumber(id.substringBefore('/'), id.substringAfter('/'))
            ?: throw CliFailure("no printing for $id in the card index", ExitCode.ERROR)

    /** The set's own basic land [name]: the lowest collector number, else a companion set's. */
    private fun basic(index: CardIndex, name: String): Printing {
        val sets = listOf(listOf(index.setCodes.first()), index.setCodes.drop(1))
        return sets.firstNotNullOfOrNull { codes ->
            codes
                .flatMap { index.printings(it) }
                .filter { it.card.isBasicLand && it.card.name == name }
                .minWithOrNull(
                    compareBy<Printing, String>(GradingPool.collectorOrder) { it.collectorNumber }
                )
        }
            ?: throw CliFailure(
                "no basic land $name in ${index.setCodes.joinToString()}",
                ExitCode.ERROR,
            )
    }

    /** The `.csv` text of [rows]; [setName] is the name of the main set, [mainSet] its code. */
    fun csv(rows: List<Row>, mainSet: String, setName: String): String = buildString {
        append(HEADER).append('\n')
        rows.forEach { (printing, quantity) ->
            val inMain = printing.setCode.equals(mainSet, ignoreCase = true)
            val fields =
                listOf(
                    printing.card.name,
                    printing.setCode.uppercase(),
                    if (inMain) setName else "",
                    printing.collectorNumber,
                    "normal",
                    printing.rarity.scryfallName,
                    quantity.toString(),
                    "",
                    printing.scryfallId,
                    "",
                    "false",
                    "false",
                    "near_mint",
                    "en",
                    "",
                    "",
                )
            append(fields.joinToString(",", transform = ::quoted)).append('\n')
        }
    }

    /** The `.txt` text of [rows]: `<quantity> <name> (<SET CODE>) <collector number>` each. */
    fun txt(rows: List<Row>): String = buildString {
        rows.forEach { (printing, quantity) ->
            append("$quantity ${printing.card.name} (${printing.setCode.uppercase()}) ")
            append(printing.collectorNumber).append('\n')
        }
    }

    /** A field as ManaBox writes it: quoted when it holds a comma or a quote, quotes doubled. */
    private fun quoted(field: String): String =
        if (',' in field || '"' in field) "\"" + field.replace("\"", "\"\"") + "\"" else field

    /** The file name stem for [prefix] and a configuration's text: `WU/R` is written `WU+R`. */
    fun stem(prefix: String, config: BuildConfig): String =
        "$prefix-${config.toString().replace('/', '+')}"

    /**
     * Writes `<stem>.csv` and `<stem>.txt` for [deck] into [dir], created if missing and replacing
     * files of those names, and says `Wrote <path>` on [stderr] for each. A file that cannot be
     * written is a [CliFailure].
     */
    fun write(
        dir: Path,
        stem: String,
        index: CardIndex,
        setName: String,
        deck: Deck,
        stderr: PrintStream,
    ) {
        val rows = rows(index, deck)
        val files =
            mapOf(
                "$stem.csv" to csv(rows, index.setCodes.first(), setName),
                "$stem.txt" to txt(rows),
            )
        try {
            Files.createDirectories(dir)
            files.forEach { (name, text) ->
                val path = dir.resolve(name)
                Files.writeString(path, text)
                stderr.println("Wrote $path")
            }
        } catch (e: IOException) {
            throw CliFailure(
                "cannot write the export to $dir: ${e.javaClass.simpleName} ${e.message}",
                ExitCode.ERROR,
                e,
            )
        }
    }
}
