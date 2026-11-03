package spa.gate

/**
 * One entry of `[libraries]` or `[plugins]` in `gradle/libs.versions.toml`. [coordinate] is
 * `group:artifact` for a library and `plugin:<id>` for a plugin, or null when the entry could not
 * be read. [line] is 1-based.
 */
data class CatalogEntry(
    val section: String,
    val alias: String,
    val coordinate: String?,
    val line: Int,
)

/** Reads the subset of TOML that a Gradle version catalog uses. */
object VersionCatalog {
    const val LIBRARIES = "libraries"
    const val PLUGINS = "plugins"

    private val sectionHeader = Regex("^\\[([A-Za-z0-9_.-]+)]$")
    private val assignment = Regex("^([A-Za-z0-9_.-]+)\\s*=\\s*(.+)$")
    private val quoted = Regex("^\"([^\"]*)\"$")

    fun entries(toml: String): List<CatalogEntry> {
        var section = ""
        val result = mutableListOf<CatalogEntry>()
        toml.lines().forEachIndexed { index, raw ->
            val line = stripComment(raw).trim()
            val header = sectionHeader.find(line)
            val entry = assignment.find(line)
            when {
                header != null -> section = header.groupValues[1]
                entry != null && (section == LIBRARIES || section == PLUGINS) -> {
                    val (alias, value) = entry.destructured
                    result += CatalogEntry(section, alias, coordinate(section, value), index + 1)
                }
            }
        }
        return result
    }

    /** Entries in [head] whose coordinate does not appear anywhere in [base]. */
    fun added(base: String?, head: String): List<CatalogEntry> {
        val known =
            base?.let { text -> entries(text).mapNotNull { it.coordinate }.toSet() }.orEmpty()
        return entries(head).filter { it.coordinate == null || it.coordinate !in known }
    }

    private fun coordinate(section: String, value: String): String? {
        val text = value.trim()
        val shorthand = quoted.find(text)?.groupValues?.get(1)
        return if (section == PLUGINS) {
            val id = shorthand?.substringBefore(':') ?: tableValue(text, "id")
            id?.takeIf { it.isNotBlank() }?.let { "plugin:$it" }
        } else {
            val module =
                shorthand?.split(':')?.take(2)?.takeIf { it.size == 2 }?.joinToString(":")
                    ?: tableValue(text, "module")
                    ?: tableModule(text)
            module?.takeIf { it.count { c -> c == ':' } == 1 }
        }
    }

    private fun tableModule(text: String): String? {
        val group = tableValue(text, "group")
        val name = tableValue(text, "name")
        return if (group != null && name != null) "$group:$name" else null
    }

    private fun tableValue(text: String, key: String): String? {
        if (!text.startsWith("{")) return null
        return Regex("(?:^|[{,\\s])${Regex.escape(key)}\\s*=\\s*\"([^\"]*)\"")
            .find(text)
            ?.groupValues
            ?.get(1)
    }

    private fun stripComment(line: String): String {
        var inString = false
        line.forEachIndexed { index, c ->
            when {
                c == '"' -> inString = !inString
                c == '#' && !inString -> return line.substring(0, index)
            }
        }
        return line
    }
}
