package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.roundToInt
import spa.agent.grade.CardGrade
import spa.agent.grade.GradeFile
import spa.agent.grade.GradeFiles
import spa.agent.grade.GradeLookup
import spa.core.cards.PoolEntry
import spa.core.intake.PoolParser

/**
 * `spa grades <pool-file> --set <code> [--data <dir>]`: one line per pool entry with its grade,
 * letter label, confidence and source, highest grade first (ties by name), then the entries with no
 * grade (basic lands, which grade files never hold), by name, with `-` and source `none`.
 *
 * Grades come from `<data>/winrates/<code>.json` and `<data>/grades/<code>.json`, whichever exist,
 * through [GradeLookup], which prefers the win-rate grade. Neither existing is one line and exit 1.
 * Pool lines that do not resolve are listed on stderr. Makes no network or model call.
 */
object GradesCommand {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("set", "data"))
        val file = Commands.onePositional(parsed, "pool file")
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)

        val text = Commands.readFile(Path.of(file), "pool file")
        val index = Commands.loadIndex(data, set)
        val lookup = GradeLookup(gradeFiles(data, set))
        val result =
            Commands.guard("cannot parse pool file $file") { PoolParser.parse(text, index) }
        result.unresolved.forEach {
            stderr.println(
                "spa: line ${it.lineNumber} unresolved (${it.reason.code}): ${it.rawLine}"
            )
        }

        val rows = result.pool.entries.map { it to lookup.grade(it.printing, set) }
        val graded =
            rows
                .filter { it.second != null }
                .sortedWith(
                    compareByDescending<Pair<PoolEntry, CardGrade?>> { it.second?.score }
                        .thenBy { it.first.printing.card.name }
                )
        val ungraded = rows.filter { it.second == null }.sortedBy { it.first.printing.card.name }
        (graded + ungraded).forEach { (entry, grade) -> stdout.println(line(entry, grade)) }
        stdout.flush()
        stderr.flush()
        return ExitCode.OK
    }

    /** The letter label of [score] on the 0.0 to 5.0 scale (G-003 Decided 2). */
    fun label(score: Double): String = LABELS[(score / CardGrade.SCORE_STEP).roundToInt()]

    private fun line(entry: PoolEntry, grade: CardGrade?): String {
        val printing = entry.printing
        return String.format(
            Locale.ROOT,
            "%-3s  %-2s  %-4s  %-8s  %dx %s (%s %s)",
            grade?.let { String.format(Locale.ROOT, "%.1f", it.score) } ?: NONE,
            grade?.let { label(it.score) } ?: NONE,
            grade?.let { String.format(Locale.ROOT, "%.2f", it.confidence) } ?: NONE,
            grade?.source?.name ?: "none",
            entry.count,
            printing.card.name,
            printing.setCode,
            printing.collectorNumber,
        )
    }

    /** The set's win-rate and model grade files under [data] that exist. */
    internal fun gradeFiles(data: DataDir, set: String): List<GradeFile> {
        val model = data.root.resolve("grades").resolve("$set.json")
        val winRates = data.root.resolve("winrates").resolve("$set.json")
        val files = listOf(winRates, model).filter(Files::isRegularFile)
        if (files.isEmpty()) {
            throw CliFailure("no grade file for set '$set': $model", ExitCode.ERROR)
        }
        return files.map { path ->
            val text = Commands.readFile(path, "grade file")
            Commands.guard("invalid grade file $path") { GradeFiles.parse(text) }
        }
    }

    private const val NONE = "-"

    /** Labels by half-step, 0 (0.0) to 10 (5.0), as on the owner's grade sheet. */
    private val LABELS = listOf("F", "D−", "D", "C−", "C", "C+", "B", "B+", "A−", "A", "A+")
}
