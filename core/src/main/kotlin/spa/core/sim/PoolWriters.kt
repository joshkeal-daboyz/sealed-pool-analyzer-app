package spa.core.sim

import java.util.Locale
import spa.core.cards.Card
import spa.core.cards.Pool
import spa.core.cards.Printing
import spa.core.cards.Rarity

/**
 * Writes a pool as each of the three pool file formats, so that `PoolParser.parse` with the
 * matching format reads it back to the same cards. Every file ends each line with `\n`.
 */
object PoolWriters {
    /**
     * The ManaBox export header, the best-known layout until a real export from the 2026-11-06
     * event replaces it.
     */
    val MANABOX_HEADER: List<String> =
        listOf(
            "Name",
            "Set code",
            "Set name",
            "Collector number",
            "Foil",
            "Rarity",
            "Quantity",
            "ManaBox ID",
            "Scryfall ID",
            "Purchase price",
            "Misprint",
            "Altered",
            "Condition",
            "Language",
            "Purchase date",
            "Purchase price currency",
        )

    /** The purchase date every synthetic ManaBox row carries. */
    const val PURCHASE_DATE = "2026-11-06"

    private const val MANABOX_ID_BASE = 100_000
    private const val MANABOX_ID_SPAN = 900_000
    private const val NEEDS_QUOTES = ",\"\n\r"

    private val prices =
        mapOf(
            Rarity.COMMON to "0.10",
            Rarity.UNCOMMON to "0.25",
            Rarity.RARE to "1.50",
            Rarity.MYTHIC to "6.00",
            Rarity.SPECIAL to "2.00",
            Rarity.BONUS to "2.00",
        )

    /**
     * A ManaBox CSV: [MANABOX_HEADER], then one row per printing and finish. [setNames] maps a set
     * code to its name for the `Set name` column; a missing name writes the code in capitals.
     * [foilCounts] maps a Scryfall id to how many of its copies are foil; those get their own row.
     */
    fun manaBox(
        pool: Pool,
        setNames: Map<String, String> = emptyMap(),
        foilCounts: Map<String, Int> = emptyMap(),
    ): String = buildString {
        appendCsvLine(MANABOX_HEADER)
        pool.entries.forEach { entry ->
            val foil = (foilCounts[entry.printing.scryfallId] ?: 0).coerceIn(0, entry.count)
            val normal = entry.count - foil
            if (normal > 0) appendCsvLine(manaBoxRow(entry.printing, normal, false, setNames))
            if (foil > 0) appendCsvLine(manaBoxRow(entry.printing, foil, true, setNames))
        }
    }

    /**
     * A text list: `N Name` per card, copies merged across printings, in first-seen order. Names
     * are full Scryfall names with ` // ` between faces, except that a card whose oracle id is in
     * [frontFaceOnly] is written by its front face's name.
     */
    fun text(pool: Pool, frontFaceOnly: Set<String> = emptySet()): String {
        val cards = linkedMapOf<String, Pair<Card, Int>>()
        pool.entries.forEach { entry ->
            val card = entry.printing.card
            val count = (cards[card.oracleId]?.second ?: 0) + entry.count
            cards[card.oracleId] = card to count
        }
        return cards.values.joinToString("") { (card, count) ->
            val name = if (card.oracleId in frontFaceOnly) card.faces.first().name else card.name
            "$count $name\n"
        }
    }

    /** A collector list: `SET number` per printing, with ` xN` when there is more than one copy. */
    fun collector(pool: Pool): String =
        pool.entries.joinToString("") { entry ->
            val printing = entry.printing
            val copies = if (entry.count > 1) " x${entry.count}" else ""
            "${printing.setCode.uppercase(Locale.ROOT)} ${printing.collectorNumber}$copies\n"
        }

    private fun manaBoxRow(
        printing: Printing,
        quantity: Int,
        foil: Boolean,
        setNames: Map<String, String>,
    ): List<String> {
        val code = printing.setCode.uppercase(Locale.ROOT)
        return listOf(
            printing.card.name,
            code,
            setNames[printing.setCode] ?: code,
            printing.collectorNumber,
            if (foil) "foil" else "normal",
            printing.rarity.scryfallName,
            quantity.toString(),
            manaBoxId(printing, foil).toString(),
            printing.scryfallId,
            prices.getValue(printing.rarity),
            "false",
            "false",
            "near_mint",
            "en",
            PURCHASE_DATE,
            "USD",
        )
    }

    /** A stable stand-in for ManaBox's own id: six digits from the Scryfall id and finish. */
    private fun manaBoxId(printing: Printing, foil: Boolean): Int =
        MANABOX_ID_BASE + Math.floorMod("${printing.scryfallId}/$foil".hashCode(), MANABOX_ID_SPAN)

    private fun StringBuilder.appendCsvLine(fields: List<String>) {
        append(fields.joinToString(",") { csvField(it) })
        append('\n')
    }

    /** A field quoted when it holds a comma, a quote, or a line break, with quotes doubled. */
    internal fun csvField(value: String): String =
        if (value.any { it in NEEDS_QUOTES }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
