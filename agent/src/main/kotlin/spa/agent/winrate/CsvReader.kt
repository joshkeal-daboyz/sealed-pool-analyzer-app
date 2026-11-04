package spa.agent.winrate

import java.io.Reader

/**
 * Reads RFC 4180 CSV records one at a time: fields separated by commas, a field in double quotes
 * may hold commas, line breaks, and `""` for one quote. A record ends at `\n` or `\r\n` outside
 * quotes.
 */
class CsvReader(private val reader: Reader) {
    /** The next record's fields, or null at the end of the input. */
    fun next(): List<String>? = TODO()
}
