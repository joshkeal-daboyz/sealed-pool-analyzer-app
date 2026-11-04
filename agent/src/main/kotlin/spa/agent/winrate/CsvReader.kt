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
        if (peek() == END) return null
        val fields = ArrayList<String>()
        do {
            val end = readField()
            fields += take()
        } while (end == COMMA)
        return fields
    }

    /**
     * Reads one field into [field] and returns what ended it: [COMMA], [NEWLINE], or [END]. A `\r`
     * right before the newline is dropped.
     */
    private fun readField(): Int {
        var quoted = false
        var c = read()
        while (c != END && (quoted || c !in FIELD_ENDS)) {
            if (quoted) quoted = quotedChar(c)
            else if (c == QUOTE && field.isEmpty()) quoted = true
            else if (c != RETURN || peek() != NEWLINE) field.append(c.toChar())
            c = read()
        }
        return c
    }

    /** Takes [c] inside quotes, where `""` is one quote; false when [c] closes the quotes. */
    private fun quotedChar(c: Int): Boolean {
        if (c != QUOTE) {
            field.append(c.toChar())
            return true
        }
        val doubled = peek() == QUOTE
        if (doubled) field.append(read().toChar())
        return doubled
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
        const val QUOTE = '"'.code
        const val COMMA = ','.code
        const val NEWLINE = '\n'.code
        const val RETURN = '\r'.code
        val FIELD_ENDS = intArrayOf(COMMA, NEWLINE)
        val SMALL = Array(128) { it.toChar().toString() }
    }
}
