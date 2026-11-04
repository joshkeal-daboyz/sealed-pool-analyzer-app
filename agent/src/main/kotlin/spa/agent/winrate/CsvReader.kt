package spa.agent.winrate

import java.io.Reader

/**
 * Reads RFC 4180 CSV records one at a time: fields separated by commas, a field in double quotes
 * may hold commas, line breaks, and `""` for one quote. A record ends at `\n` or `\r\n` outside
 * quotes.
 */
class CsvReader(private val reader: Reader) {
    private val buffer = CharArray(BUFFER_SIZE)
    private var length = 0
    private var position = 0
    private val field = StringBuilder()

    /** The next record's fields, or null at the end of the input. */
    fun next(): List<String>? {
        var c = read()
        if (c == END) return null
        val fields = ArrayList<String>()
        var quoted = false
        while (true) {
            when {
                quoted && c == END -> {
                    fields += take()
                    return fields
                }
                quoted && c == QUOTE.code ->
                    if (peek() == QUOTE.code) field.append(read().toChar()) else quoted = false
                quoted -> field.append(c.toChar())
                c == END || c == '\n'.code -> {
                    fields += take()
                    return fields
                }
                c == QUOTE.code && field.isEmpty() -> quoted = true
                c == ','.code -> fields += take()
                c == '\r'.code && peek() == '\n'.code -> Unit
                else -> field.append(c.toChar())
            }
            c = read()
        }
    }

    /** The field read so far, and an empty buffer for the next. One-character fields are shared. */
    private fun take(): String {
        val text =
            if (field.length == 1 && field[0].code < SMALL.size) SMALL[field[0].code]
            else field.toString()
        field.setLength(0)
        return text
    }

    private fun read(): Int {
        if (position == length && !fill()) return END
        return buffer[position++].code
    }

    private fun peek(): Int {
        if (position == length && !fill()) return END
        return buffer[position].code
    }

    private fun fill(): Boolean {
        length = reader.read(buffer)
        position = 0
        if (length <= 0) length = 0
        return length > 0
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 16
        const val END = -1
        const val QUOTE = '"'
        val SMALL = Array(128) { it.toChar().toString() }
    }
}
