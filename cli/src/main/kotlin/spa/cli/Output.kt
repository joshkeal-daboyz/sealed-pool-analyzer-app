package spa.cli

import java.util.Properties
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import spa.core.baseline.Baseline
import spa.core.intake.KitWarning
import spa.core.intake.ParseResult
import spa.core.intake.UnresolvedLine
import spa.core.metrics.PoolMetrics

/**
 * The document `spa analyze` prints. Keys appear in the order the properties are declared here, so
 * the same input always prints the same bytes.
 *
 * @property baseline the set's baseline, or null with `--no-baseline` or when the set has none.
 */
@Serializable
data class AnalyzeOutput(
    val tool: ToolInfo,
    val set: String,
    val pool: PoolReport,
    val metrics: PoolMetrics,
    val baseline: Baseline?,
)

/** The tool's name and the version Gradle built. */
@Serializable data class ToolInfo(val name: String, val version: String)

/**
 * What the pool file parsed to. [file] is the path as given; [entries] counts resolved pool
 * entries, not cards.
 */
@Serializable
data class PoolReport(
    val file: String,
    val format: String,
    val entries: Int,
    val skippedLines: Int,
    val unresolved: List<UnresolvedReport>,
    val kitWarnings: List<KitWarningReport>,
)

/** One line that did not resolve: its 1-based [line], its [text], and the reason code. */
@Serializable data class UnresolvedReport(val line: Int, val text: String, val reason: String)

/** One kit warning, its allowed range written as [ExpectedRange]. */
@Serializable
data class KitWarningReport(
    val code: String,
    val expected: ExpectedRange,
    val actual: Int,
    val message: String,
)

/** An inclusive range of allowed values. */
@Serializable data class ExpectedRange(val min: Int, val max: Int)

/** Builds and encodes the output documents. */
object Output {
    /** The tool's name, as installed. */
    const val TOOL_NAME = "spa"

    private const val VERSION_RESOURCE = "/spa/cli/version.properties"

    private val json = Json { prettyPrint = true }

    /** The version from the generated resource, or `unknown` when it is missing. */
    val version: String by lazy {
        val stream = Output::class.java.getResourceAsStream(VERSION_RESOURCE)
        val properties = Properties()
        stream?.use { properties.load(it) }
        properties.getProperty("version") ?: "unknown"
    }

    /** The report of [result] for the pool file at [file], the path as the user gave it. */
    fun poolReport(file: String, result: ParseResult): PoolReport =
        PoolReport(
            file = file,
            format = result.format.name,
            entries = result.pool.entries.size,
            skippedLines = result.skippedLines,
            unresolved = result.unresolved.map(::unresolved),
            kitWarnings = result.warnings.map(::kitWarning),
        )

    /** [output] as pretty-printed JSON ending in a newline. */
    fun encode(output: AnalyzeOutput): String =
        json.encodeToString(AnalyzeOutput.serializer(), output) + "\n"

    private fun unresolved(line: UnresolvedLine) =
        UnresolvedReport(line.lineNumber, line.rawLine, line.reason.code)

    private fun kitWarning(warning: KitWarning) =
        KitWarningReport(
            warning.code.code,
            ExpectedRange(warning.expected.first, warning.expected.last),
            warning.actual,
            warning.message,
        )
}
