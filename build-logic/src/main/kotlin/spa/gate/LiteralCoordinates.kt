package spa.gate

/** A dependency coordinate written as a string literal in a build script. [line] is 1-based. */
data class LiteralHit(val line: Int, val text: String)

/**
 * Finds dependency coordinates written as string literals in a `*.gradle.kts` file instead of
 * catalog aliases: library strings such as `"org.example:lib:1.0"`, and plugin requests with a
 * literal version such as `id("org.example.plugin") version "1.0"`.
 */
object LiteralCoordinates {
    private val stringLiteral = Regex("\"((?:[^\"\\\\$]|\\\\.)*)\"")
    private val libraryCoordinate =
        Regex(
            "^[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)+:[A-Za-z0-9_.-]+(:[A-Za-z0-9_.+\\[\\](),-]*)?(@\\w+)?$"
        )
    private val pluginVersion = Regex("(?:id|kotlin)\\(\"[^\"]+\"\\)\\s*version\\s*\"[^\"]*\"")

    fun scan(script: String): List<LiteralHit> =
        script.lines().flatMapIndexed { index, line ->
            val code = line.trim()
            if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) {
                return@flatMapIndexed emptyList()
            }
            val libraries =
                stringLiteral
                    .findAll(code)
                    .map { it.groupValues[1] }
                    .filter { libraryCoordinate.matches(it) }
            val plugins = pluginVersion.findAll(code).map { it.value }
            (libraries + plugins).map { LiteralHit(index + 1, it) }.toList()
        }
}
