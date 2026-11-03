package spa.core.intake

import java.util.Locale
import spa.core.cards.CardIndex

/**
 * Reads a ManaBox CSV export. Columns are found by header name, so their order and any extra or
 * missing optional columns do not matter; only `Scryfall ID` and `Quantity` are required. Each row
 * resolves by exact Scryfall id.
 */
object ManaBoxCsvParser {
    const val SCRYFALL_ID = "Scryfall ID"
    const val QUANTITY = "Quantity"
    const val SET_CODE = "Set code"

    /**
     * Parses [text] against [index]; throws [PoolParseException] when a required column is missing.
     */
    fun parse(text: String, index: CardIndex): ParseResult {
        val builder = PoolBuilder(PoolFormat.MANABOX, index)
        val records = CsvReader.records(text)
        val headerAt = records.indexOfFirst { !it.isBlank }
        repeat(if (headerAt < 0) records.size else headerAt) { builder.skip() }
        val header = records.getOrNull(headerAt)?.fields.orEmpty().map { columnKey(it) }
        val missing = listOf(SCRYFALL_ID, QUANTITY).filter { columnKey(it) !in header }
        if (missing.isNotEmpty()) {
            throw PoolParseException(
                "ManaBox CSV is missing the required column(s) " +
                    missing.joinToString { "'$it'" } +
                    ".",
                missing,
            )
        }
        val columns = Columns(header)
        records.drop(headerAt + 1).forEach { record ->
            if (record.isBlank) builder.skip() else row(record, columns, index, builder)
        }
        return builder.build()
    }

    private fun row(record: CsvRecord, columns: Columns, index: CardIndex, builder: PoolBuilder) {
        val id = record.field(columns.scryfallId).trim()
        val printing = index.byScryfallId(id)
        val count = PoolLines.positiveCount(record.field(columns.quantity))
        when {
            printing == null ->
                builder.unresolved(
                    record.lineNumber,
                    record.raw,
                    unknownIdReason(record, columns, index),
                )
            count == null ->
                builder.unresolved(record.lineNumber, record.raw, UnresolvedReason.BAD_QUANTITY)
            else -> builder.add(record.lineNumber, printing, count)
        }
    }

    private fun unknownIdReason(
        record: CsvRecord,
        columns: Columns,
        index: CardIndex,
    ): UnresolvedReason {
        val setCode = columns.setCode?.let { record.field(it).trim().lowercase(Locale.ROOT) }
        return if (!setCode.isNullOrEmpty() && setCode !in index.setCodes) {
            UnresolvedReason.WRONG_SET
        } else {
            UnresolvedReason.UNKNOWN_SCRYFALL_ID
        }
    }

    private fun columnKey(name: String): String = name.trim().lowercase(Locale.ROOT)

    private class Columns(header: List<String>) {
        val scryfallId = header.indexOf(columnKey(SCRYFALL_ID))
        val quantity = header.indexOf(columnKey(QUANTITY))
        val setCode = header.indexOf(columnKey(SET_CODE)).takeIf { it >= 0 }
    }
}

/** One CSV record: its fields, its raw text, and the 1-based line it starts on. */
internal data class CsvRecord(val lineNumber: Int, val raw: String, val fields: List<String>) {
    val isBlank: Boolean
        get() = raw.isBlank()

    /** The field at [column], or empty when the row is shorter than the header. */
    fun field(column: Int): String = fields.getOrElse(column) { "" }
}

/**
 * A hand-written CSV reader: comma-separated fields, double-quoted fields that may hold commas,
 * line breaks, and doubled quotes. A record that starts a quoted field and does not close it on the
 * same line continues onto the next.
 */
internal object CsvReader {
    fun records(text: String): List<CsvRecord> {
        val lines = PoolLines.split(text)
        val records = mutableListOf<CsvRecord>()
        var at = 0
        while (at < lines.size) {
            val start = at
            var raw = lines[at]
            var split = fields(raw)
            while (split.openQuote && at + 1 < lines.size) {
                at++
                raw += "\n" + lines[at]
                split = fields(raw)
            }
            records += CsvRecord(start + 1, raw, split.fields)
            at++
        }
        return records
    }

    private class Split(val fields: List<String>, val openQuote: Boolean)

    private fun fields(line: String): Split {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var at = 0
        while (at < line.length) {
            val c = line[at]
            when {
                quoted && c == '"' && line.getOrNull(at + 1) == '"' -> {
                    field.append('"')
                    at++
                }
                c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                !quoted && c == ',' -> {
                    fields += field.toString()
                    field.clear()
                }
                else -> field.append(c)
            }
            at++
        }
        fields += field.toString()
        return Split(fields, quoted)
    }
}
