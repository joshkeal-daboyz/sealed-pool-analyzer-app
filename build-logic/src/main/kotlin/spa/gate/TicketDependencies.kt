package spa.gate

/**
 * Parses the list under "Dependencies this ticket may add" in a ticket's Scope section. Each
 * sub-bullet holds one backticked coordinate: `group:artifact` for a library or `plugin:<plugin
 * id>` for a Gradle plugin.
 */
object TicketDependencies {
    const val HEADING = "Dependencies this ticket may add"

    private val bullet = Regex("^(\\s*)[-*]\\s+(.*)$")
    private val backticked = Regex("`([^`]+)`")
    private val coordinate = Regex("^(plugin:[A-Za-z0-9_.-]+|[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+)$")

    fun allowed(markdown: String): Set<String> {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { HEADING in it && bullet.matches(it) }
        if (start < 0) return emptySet()
        val headingIndent = indentOf(lines[start])
        return lines
            .drop(start + 1)
            .takeWhile { line -> bullet.matches(line) && indentOf(line) > headingIndent }
            .mapNotNull { line -> backticked.find(line)?.groupValues?.get(1)?.trim() }
            .filter { coordinate.matches(it) }
            .toSet()
    }

    private fun indentOf(line: String): Int = line.length - line.trimStart().length
}
