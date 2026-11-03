package spa.gate

import java.util.Locale
import spa.gate.Xml.all
import spa.gate.Xml.children

/** Covered and missed line counts for one source file. */
data class LineCoverage(val covered: Int, val missed: Int) {
    /** Percentage of lines covered; a file with no executable lines counts as fully covered. */
    val percent: Double
        get() = if (covered + missed == 0) FULL else covered * FULL / (covered + missed)

    private companion object {
        const val FULL = 100.0
    }
}

/** A touched main source file and the key it has in the Kover report. */
data class TouchedSource(val path: String, val reportKey: String)

/** The `coverage` step: Kover line coverage per touched file against the ticket threshold. */
object Coverage {
    const val MAIN_SOURCES = "core/src/main/kotlin/"

    private val packageDeclaration =
        Regex("^\\s*package\\s+([A-Za-z0-9_.`]+)", RegexOption.MULTILINE)

    /** Changed paths narrowed to Kotlin files under [MAIN_SOURCES], repository-relative. */
    fun touchedMainFiles(changed: Collection<String>): List<String> =
        changed
            .map { it.trim().replace('\\', '/') }
            .filter { it.startsWith(MAIN_SOURCES) && it.endsWith(".kt") }
            .distinct()
            .sorted()

    /**
     * The key Kover uses for a source file: its declared package as a path, then the file name.
     * Kover keys by package, not by directory, so the two can differ.
     */
    fun reportKey(fileName: String, source: String): String {
        val pkg = packageDeclaration.find(source)?.groupValues?.get(1)?.replace("`", "")
        return if (pkg.isNullOrEmpty()) fileName else pkg.replace('.', '/') + "/" + fileName
    }

    /** Line coverage per `package/path/File.kt` from a Kover (JaCoCo-format) XML report. */
    fun parseKoverXml(xml: String): Map<String, LineCoverage> =
        Xml.parse(xml)
            .all("package")
            .flatMap { pkg ->
                val prefix = pkg.getAttribute("name").trim('/')
                pkg.children("sourcefile").map { file ->
                    val key =
                        if (prefix.isEmpty()) file.getAttribute("name")
                        else "$prefix/${file.getAttribute("name")}"
                    val counter =
                        file.children("counter").firstOrNull { it.getAttribute("type") == "LINE" }
                    key to
                        LineCoverage(
                            covered = counter?.getAttribute("covered")?.toIntOrNull() ?: 0,
                            missed = counter?.getAttribute("missed")?.toIntOrNull() ?: 0,
                        )
                }
            }
            .toMap()

    /**
     * One failure per touched file below [threshold]. A file absent from [coverage] has no
     * executable lines and passes. A null [coverage] means the report is missing.
     */
    fun failures(
        touched: List<TouchedSource>,
        coverage: Map<String, LineCoverage>?,
        threshold: Double,
        reproduce: String,
    ): List<GateFailure> = touched.mapNotNull { source ->
        val percent =
            when {
                coverage == null -> 0.0
                else -> coverage[source.reportKey]?.percent ?: return@mapNotNull null
            }
        if (percent >= threshold) return@mapNotNull null
        val detail = if (coverage == null) " (no Kover report was produced)" else ""
        GateFailure(
            GateStep.COVERAGE.id,
            source.path,
            null,
            "Line coverage ${format(percent)}% is below the ticket threshold " +
                "${format(threshold)}%$detail.",
            reproduce,
        )
    }

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.ROOT, "%.1f", value)
}
