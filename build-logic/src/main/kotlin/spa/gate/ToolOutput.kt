package spa.gate

import spa.gate.Xml.all
import spa.gate.Xml.children

/** A problem a tool reported at a place in a file. [path] is as the tool printed it. */
data class Located(val path: String, val line: Int?, val message: String)

/** Parsers for what ktfmt, detekt, and the Kotlin compiler say when they fail. */
object ToolOutput {
    private val compilerUri = Regex("^e: (file:\\S+?):(\\d+):\\d+ (.*)$")
    private val compilerLegacy = Regex("^e: (.+?): \\((\\d+), \\d+\\): (.*)$")

    /**
     * Files named in a failed ktfmt check, relative to the checked project's directory. ktfmt lists
     * them one per line after a "not properly formatted:" line.
     */
    fun ktfmtFiles(message: String): List<String> =
        message
            .lines()
            .dropWhile { "not properly formatted" !in it }
            .drop(1)
            .map { it.trim().replace('\\', '/') }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Findings from detekt's checkstyle-format XML report. */
    fun detektFindings(xml: String): List<Located> =
        Xml.parse(xml).all("file").flatMap { file ->
            file.children("error").map { error ->
                val rule = error.getAttribute("source").removePrefix("detekt.")
                Located(
                    path = file.getAttribute("name"),
                    line = error.getAttribute("line").toIntOrNull(),
                    message = "[$rule] ${error.getAttribute("message")}",
                )
            }
        }

    /** Kotlin compiler errors (`e: file:///path:line:column message`) from captured output. */
    fun compilerErrors(output: String): List<Located> =
        output.lines().mapNotNull { raw ->
            val line = raw.trim()
            compilerUri.find(line)?.destructured?.let { (uri, row, message) ->
                Located(RepoPaths.fromFileUri(uri), row.toInt(), message)
            }
                ?: compilerLegacy.find(line)?.destructured?.let { (path, row, message) ->
                    Located(path, row.toInt(), message)
                }
        }
}
