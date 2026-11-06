package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import spa.core.baseline.Baseline
import spa.core.brief.BriefLoader
import spa.core.brief.BriefValidationException
import spa.core.brief.FormatBrief
import spa.core.intake.PoolFormat
import spa.core.intake.PoolParser
import spa.core.metrics.MetricEngine

/**
 * `spa analyze <pool-file> --set <code> [--format manabox|text|collector] [--data <dir>]
 * [--no-baseline] [--brief <file>]`: parses a pool and prints [AnalyzeOutput] to stdout.
 * Diagnostics go to stderr.
 */
object AnalyzeCommand {
    private val formats = PoolFormat.entries.associateBy { it.name.lowercase(Locale.ROOT) }

    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed =
            Args.parse(
                args,
                valueOptions = setOf("set", "format", "data", "brief"),
                flagOptions = setOf("no-baseline"),
            )
        val file = Commands.onePositional(parsed, "pool file")
        val format = format(parsed)
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)

        val text = Commands.readFile(Path.of(file), "pool file")
        val index = Commands.loadIndex(data, set)
        val brief = loadBrief(parsed.value("brief")?.let(Path::of) ?: data.briefFile(set))
        val result =
            Commands.guard("cannot parse pool file $file") {
                PoolParser.parse(text, index, format, brief.kitTemplate)
            }
        val baseline =
            if (parsed.has("no-baseline")) {
                stderr.println("baseline: none (--no-baseline)")
                null
            } else {
                loadBaseline(data.baselineFile(set), set, stderr)
            }
        val output =
            AnalyzeOutput(
                tool = ToolInfo(Output.TOOL_NAME, Output.version),
                set = set,
                pool = Output.poolReport(file, result),
                metrics = MetricEngine.compute(result.pool),
                baseline = baseline,
            )
        stdout.print(Output.encode(output))
        stdout.flush()
        return ExitCode.OK
    }

    /** `--format`, or null to detect the pool's format; anything else is exit 2. */
    internal fun format(parsed: Args): PoolFormat? =
        parsed.value("format")?.let { name ->
            formats[name.lowercase(Locale.ROOT)]
                ?: throw CliFailure(
                    "--format must be manabox, text, or collector, not '$name'",
                    ExitCode.USAGE,
                )
        }

    internal fun loadBrief(path: Path): FormatBrief {
        val text = Commands.readFile(path, "brief")
        return try {
            BriefLoader.parse(text)
        } catch (e: BriefValidationException) {
            throw CliFailure(
                "brief $path is invalid (${e.errors.size} error(s)); " +
                    "run 'spa validate-brief' for the list",
                ExitCode.ERROR,
                e,
            )
        }
    }

    internal fun loadBaseline(path: Path, set: String, stderr: PrintStream): Baseline? {
        if (!Files.isRegularFile(path)) {
            stderr.println("baseline: none for set '$set' (no file at $path)")
            return null
        }
        val text = Commands.readFile(path, "baseline")
        return Commands.guard("cannot read baseline $path") { Baseline.decode(text) }
    }
}
