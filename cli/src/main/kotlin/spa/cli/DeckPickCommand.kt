package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import spa.agent.deckpick.BuildSelection
import spa.agent.deckpick.DeckPick
import spa.agent.deckpick.DeckPickReport
import spa.agent.deckpick.GridPoint
import spa.agent.deckpick.SealedBuilds
import spa.agent.deckpick.SkipReason
import spa.agent.deckpick.TunedWeights
import spa.agent.deckpick.Tuning
import spa.agent.deckpick.TuningSet
import spa.agent.grade.GradeLookup
import spa.agent.poolgrade.PoolGrader
import spa.agent.poolgrade.Weights
import spa.agent.themes.PoolThemes
import spa.agent.themes.SetThemes
import spa.core.cards.CardIndex
import spa.core.cards.Printing

/**
 * `spa deck-pick <sealed-game-data.csv.gz> --set <code> [--grades <file>] [--weights <file>]
 * [--data <dir>]`: the deck-pick test ([DeckPick]) on one 17Lands Sealed game data file, for a
 * random ranking, set grades alone and pool-adjusted grades.
 *
 * With `--tune`, the positionals are one file per set of `--set a,b` (and `--grades`, if given, one
 * file per set); every [Tuning.GRID] combination is measured on all the sets' builds pooled, and
 * the best is written, with a comment, to `--out` or `<data>/weights/tuned.json`. No network or
 * model call.
 */
object DeckPickCommand {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed =
            Args.parse(
                args,
                valueOptions = setOf("set", "data", "grades", "weights", "out"),
                flagOptions = setOf("tune"),
            )
        if (parsed.has("tune")) tune(parsed, stdout) else measure(parsed, stdout)
        stdout.flush()
        return ExitCode.OK
    }

    private fun measure(parsed: Args, stdout: PrintStream) {
        if (parsed.value("out") != null) {
            throw CliFailure("--out is for --tune only", ExitCode.USAGE)
        }
        val file = Commands.onePositional(parsed, "game data file")
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val grades = parsed.value("grades")?.let(Path::of)
        val context = SetContext(data, set, grades)
        val weightsFile = parsed.value("weights")?.let(Path::of) ?: data.weightsFile()
        val weights =
            Commands.guard("invalid weights file $weightsFile") {
                TunedWeights.parse(Commands.readFile(weightsFile, "weights file"))
            }
        val selection = context.read(Path.of(file))
        val report =
            Commands.guard("cannot measure $file") {
                DeckPick.measure(selection, context.setGrade, context.grader(weights))
            }
        lines(set, weights, report).forEach(stdout::println)
    }

    private fun tune(parsed: Args, stdout: PrintStream) {
        if (parsed.value("weights") != null) {
            throw CliFailure("--weights cannot be given with --tune", ExitCode.USAGE)
        }
        val files = parsed.positionals
        val sets = Commands.requiredSet(parsed).split(',').map { it.trim() }
        val grades = parsed.value("grades")?.split(',')?.map { Path.of(it.trim()) }
        val sizes = listOfNotNull(files.size, sets.size, grades?.size).toSet()
        if (files.isEmpty() || sizes.size != 1) {
            throw CliFailure(
                "--tune needs one game data file per set of --set (and per --grades file): " +
                    "${files.size} files, ${sets.size} sets",
                ExitCode.USAGE,
            )
        }
        val data = Commands.dataDir(parsed)
        val out = parsed.value("out")?.let(Path::of) ?: data.root.resolve("weights/tuned.json")
        val tuning =
            sets.indices.map { i ->
                val context = SetContext(data, sets[i], grades?.get(i))
                TuningSet(sets[i], context.read(Path.of(files[i])).builds, context.setGrade) {
                    context.grader(it)
                }
            }
        val counts = tuning.joinToString(", ") { "${it.code} (${it.builds.size} builds)" }
        stdout.println("tuning on $counts: ${tuning.sumOf { it.builds.size }} builds pooled")
        val points = Commands.guard("cannot tune") { Tuning.run(tuning) }
        points.forEach { stdout.println(point(it)) }
        val best = Tuning.best(points)
        stdout.println("best: ${point(best)}")
        Files.createDirectories(out.toAbsolutePath().parent)
        Files.writeString(out, TunedWeights.encode(best.weights, comment(best, counts)))
        stdout.println("wrote $out")
    }

    private fun comment(best: GridPoint, counts: String): String =
        "SPA-039 grid winner. Grid: synergy ${Tuning.SYNERGY.joinToString(", ", "{", "}")}, " +
            "curve ${Tuning.CURVE.joinToString(", ", "{", "}")}, " +
            "colour ${Tuning.COLOUR.joinToString(", ", "{", "}")}, cap ${Tuning.CAP}. " +
            "Highest mean adjusted overlap (${decimals(best.overlap)}) over the builds of " +
            "$counts pooled; ties to the smaller total weight."

    private fun point(point: GridPoint): String =
        "${weights(point.weights, "  ")}: ${decimals(point.overlap)}"

    private fun weights(w: Weights, separator: String): String =
        listOf(
                "synergy ${w.synergy}",
                "curve ${w.curve}",
                "colour ${w.colour}",
                "cap ${w.cap}",
            )
            .joinToString(separator)

    private fun lines(set: String, weights: Weights, report: DeckPickReport): List<String> =
        listOf("set: $set", "builds used: ${report.used}") +
            SkipReason.entries.map { "skipped, ${it.label}: ${report.skipped[it] ?: 0}" } +
            listOf(
                "weights: ${weights(weights, ", ")}",
                "random: ${decimals(report.random)}",
                "set grade: ${decimals(report.setGrade)}",
                "adjusted: ${decimals(report.adjusted)}",
                "adjusted minus set grade: ${signed(report.difference)}",
            )

    private fun decimals(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

    private fun signed(value: Double): String = String.format(Locale.ROOT, "%+.4f", value)

    /** One set's card index, set grades, and what a [PoolGrader] needs. */
    private class SetContext(data: DataDir, private val set: String, grades: Path?) {
        private val index: CardIndex = Commands.loadIndex(data, set)
        private val lookup: GradeLookup =
            PoolGradesCommand.lookup(
                grades ?: PoolGradesCommand.defaultGradesFile(data, set),
                set,
            )
        private val baseline = PoolGradesCommand.baseline(data, set)
        private val keywords = PoolThemes.keywords(data.scryfall, index.setCodes)
        private val setCards = PoolThemes.cards(index, keywords)
        private val themes =
            SetThemes.count(setCards, ThemesCommand.mechanics(data, set)).map { it.theme }

        /** Each card's printing: its first in the set itself, else its first in a companion. */
        private val printings: Map<String, Printing> =
            index.printings
                .sortedBy { if (it.setCode == set) 0 else 1 }
                .groupBy { it.card.oracleId }
                .mapValues { it.value.first() }

        val setGrade: (Printing) -> Double? = { lookup.grade(it, set)?.score }

        fun grader(weights: Weights) = PoolGrader(setCards, themes, keywords, baseline, weights)

        /** The builds of [file], each name resolved by full or front-face name. */
        fun read(file: Path): BuildSelection {
            if (!Files.isRegularFile(file)) {
                throw CliFailure("cannot read game data file $file: no such file", ExitCode.ERROR)
            }
            return Commands.guard("cannot read game data file $file") {
                SealedBuilds.read(file) { name ->
                    index.byName(name)?.let { printings[it.oracleId] }
                }
            }
        }
    }
}
