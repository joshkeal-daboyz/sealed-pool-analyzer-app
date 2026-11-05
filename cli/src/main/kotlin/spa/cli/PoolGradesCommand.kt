package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import spa.agent.grade.GradeFiles
import spa.agent.grade.GradeLookup
import spa.agent.poolgrade.PairGrades
import spa.agent.poolgrade.PoolGrader
import spa.agent.poolgrade.Weights
import spa.agent.themes.PoolThemes
import spa.agent.themes.SetThemes
import spa.core.baseline.Baseline
import spa.core.cards.CardIndex
import spa.core.cards.Pool
import spa.core.cards.Printing
import spa.core.intake.PoolParser
import spa.core.metrics.ColorPair
import spa.core.text.Color

/**
 * `spa pool-grades <pool-file> --set <code> [--pair <XY>] [--grades <file>] [--weights <file>]
 * [--data <dir>]`: each pair's candidate cards with their set grades adjusted for this pool, and
 * one reason line per nonzero part ([spa.agent.poolgrade.PoolGrader]). No network or model call.
 *
 * Set grades come from `--grades`, else `<data>/grades/<code>.v2.json` if it exists, else
 * `<data>/grades/<code>.json`; weights from `--weights`, else `<data>/weights/default.json`. A pool
 * line that does not resolve is listed on stderr and the exit stays 0.
 */
object PoolGradesCommand {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val pairs = compute(args, stderr)
        pairs.forEachIndexed { i, grades ->
            if (i > 0) stdout.println()
            PoolGradeLines.lines(grades).forEach(stdout::println)
        }
        stdout.flush()
        stderr.flush()
        return ExitCode.OK
    }

    /** The pairs [run] prints for [args], in print order. Unresolved pool lines go to [stderr]. */
    fun compute(args: List<String>, stderr: PrintStream): List<PairGrades> {
        val parsed =
            Args.parse(args, valueOptions = setOf("set", "data", "pair", "grades", "weights"))
        val file = Commands.onePositional(parsed, "pool file")
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val pair = parsed.value("pair")?.let(::pair)

        val text = Commands.readFile(Path.of(file), "pool file")
        val index = Commands.loadIndex(data, set)
        val baseline = baseline(data, set)
        val lookup =
            lookup(parsed.value("grades")?.let(Path::of) ?: defaultGradesFile(data, set), set)
        val weights = weights(parsed.value("weights")?.let(Path::of) ?: data.weightsFile())
        val pool = pool(text, file, index, stderr)

        val keywords = PoolThemes.keywords(data.scryfall, index.setCodes)
        val setCards = PoolThemes.cards(index, keywords)
        val themes = SetThemes.count(setCards, ThemesCommand.mechanics(data, set)).map { it.theme }
        val grader = PoolGrader(setCards, themes, keywords, baseline, weights)
        val setGrade = { printing: Printing -> lookup.grade(printing, set)?.score }
        return if (pair == null) grader.gradeAll(pool, setGrade)
        else listOf(grader.grade(pool, pair, setGrade))
    }

    /**
     * The grade file used without `--grades`: `<code>.v2.json` if it exists, else `<code>.json`.
     */
    fun defaultGradesFile(data: DataDir, set: String): Path {
        val grades = data.root.resolve("grades")
        val v2 = grades.resolve("$set.v2.json")
        val v1 = grades.resolve("$set.json")
        return when {
            Files.isRegularFile(v2) -> v2
            Files.isRegularFile(v1) -> v1
            else -> throw CliFailure("no grade file for set '$set': $v1", ExitCode.ERROR)
        }
    }

    /** `--pair`, in either letter order and any case. */
    internal fun pair(text: String): ColorPair {
        val colours = text.uppercase(Locale.ROOT).mapNotNull { Color.fromSymbol(it) }.toSet()
        return ColorPair.entries.firstOrNull { text.length == 2 && it.colors == colours }
            ?: throw CliFailure(
                "unknown pair '$text': expected two different colours of WUBRG, such as WU",
                ExitCode.ERROR,
            )
    }

    internal fun baseline(data: DataDir, set: String): Baseline {
        val path = data.baselineFile(set)
        if (!Files.isRegularFile(path)) {
            throw CliFailure("no baseline for set '$set': $path", ExitCode.ERROR)
        }
        val text = Commands.readFile(path, "baseline")
        return Commands.guard("cannot read baseline $path") { Baseline.decode(text) }
    }

    internal fun lookup(path: Path, set: String): GradeLookup {
        val text = Commands.readFile(path, "grade file")
        val file = Commands.guard("invalid grade file $path") { GradeFiles.parse(text) }
        if (file.header.setCode != set) {
            throw CliFailure(
                "grade file $path is for set '${file.header.setCode}', not '$set'",
                ExitCode.ERROR,
            )
        }
        return GradeLookup(listOf(file))
    }

    private fun weights(path: Path): Weights {
        val text = Commands.readFile(path, "weights file")
        return Commands.guard("invalid weights file $path") { Weights.parse(text) }
    }

    internal fun pool(
        text: String,
        file: String,
        index: CardIndex,
        stderr: PrintStream,
    ): Pool {
        val result =
            Commands.guard("cannot parse pool file $file") { PoolParser.parse(text, index) }
        result.unresolved.forEach {
            stderr.println(
                "spa: line ${it.lineNumber} unresolved (${it.reason.code}): ${it.rawLine}"
            )
        }
        return result.pool
    }
}
