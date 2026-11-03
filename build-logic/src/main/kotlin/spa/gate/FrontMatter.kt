package spa.gate

/** Reads the `---` front matter at the top of a ticket file. */
object FrontMatter {
    private const val PERCENT = 100.0
    private val entry = Regex("^([A-Za-z_][A-Za-z0-9_-]*):\\s*(.*)$")

    /** Top-level `key: value` pairs. Nested keys (indented lines) are ignored. */
    fun parse(markdown: String): Map<String, String> {
        val lines = markdown.lines().dropWhile { it.isBlank() }
        if (lines.firstOrNull()?.trim() != "---") return emptyMap()
        return lines
            .drop(1)
            .takeWhile { it.trim() != "---" }
            .mapNotNull { entry.find(it.trimEnd()) }
            .associate { it.groupValues[1] to clean(it.groupValues[2]) }
    }

    /** The ticket's `coverage_threshold` as a percentage from 0 to 100. */
    fun coverageThreshold(markdown: String): Double {
        val raw =
            requireNotNull(parse(markdown)["coverage_threshold"]) {
                "the front matter has no coverage_threshold"
            }
        val value =
            requireNotNull(raw.removeSuffix("%").trim().toDoubleOrNull()) {
                "coverage_threshold '$raw' is not a number"
            }
        require(value in 0.0..PERCENT) { "coverage_threshold $value is not between 0 and 100" }
        return value
    }

    private fun clean(value: String): String =
        value.substringBefore(" #").trim().removeSurrounding("\"").removeSurrounding("'")
}
