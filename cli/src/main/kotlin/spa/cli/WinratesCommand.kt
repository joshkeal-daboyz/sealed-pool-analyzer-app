package spa.cli

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import spa.agent.grade.GradeFiles
import spa.agent.winrate.GameData
import spa.agent.winrate.GameDataSummary
import spa.agent.winrate.WinRateScale
import spa.agent.winrate.WinRateTables

/**
 * `spa winrates <game-data.csv.gz> --set <code> [--data <dir>] [--dataset-date <yyyy-mm-dd>]`:
 * derives the set's win-rate grade table from a 17Lands public game data file and writes it to
 * `<data>/winrates/<code>.json`.
 *
 * Prints the game and card counts, the scale, one `unmatched: <name>` line per 17Lands name that is
 * no card of the set (those are skipped), and the path written. The provenance names the file and
 * its `Last-Modified` date: `--dataset-date`, or else the file's own modified date.
 */
class WinratesCommand(private val now: () -> Instant = Instant::now) {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("set", "data", "dataset-date"))
        val file = Path.of(Commands.onePositional(parsed, "game data file"))
        val set = Commands.requiredSet(parsed)
        val date = parsed.value("dataset-date")?.let(::parseDate)
        val data = Commands.dataDir(parsed)
        if (!Files.isRegularFile(file)) {
            throw CliFailure("game data file not found: $file", ExitCode.ERROR)
        }
        val index = Commands.loadIndex(data, set)
        val summary = readGameData(file)
        val provenance =
            "17Lands public game data ${file.fileName}, Last-Modified ${date ?: modified(file)}"
        val createdAt = now().truncatedTo(ChronoUnit.SECONDS).toString()
        val table = WinRateTables.build(set, summary, index, provenance, createdAt)
        val out = data.root.resolve("winrates").resolve("$set.json")
        write(out, GradeFiles.write(table.file))

        stdout.println(
            "$set: ${summary.games} games, ${summary.cards.size} card names, " +
                "${summary.cards.size - table.unmatched.size} matched, " +
                "${table.file.grades.size} graded (${WinRateScale.MIN_GAMES}+ games)"
        )
        stdout.println(scaleLine(table.scale))
        table.unmatched.forEach { stdout.println("unmatched: $it") }
        stdout.println("wrote $out")
        stdout.flush()
        return ExitCode.OK
    }

    private fun scaleLine(scale: WinRateScale?): String =
        if (scale == null) "scale: none, no card has ${WinRateScale.MIN_GAMES} games"
        else
            String.format(
                Locale.ROOT,
                "scale: mean GIH WR %.4f, standard deviation %.4f",
                scale.mean,
                scale.sd,
            )

    private fun parseDate(text: String): LocalDate =
        try {
            LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            throw CliFailure("--dataset-date must be yyyy-mm-dd, got '$text'", ExitCode.USAGE, e)
        }

    private fun modified(file: Path): LocalDate =
        LocalDate.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneOffset.UTC)

    private fun readGameData(file: Path): GameDataSummary =
        try {
            Commands.guard("cannot read game data $file") { GameData.read(file) }
        } catch (e: IOException) {
            throw CliFailure("cannot read game data $file: ${e.message}", ExitCode.ERROR, e)
        }

    private fun write(out: Path, text: String) {
        try {
            Files.createDirectories(out.parent)
            Files.writeString(out, text)
        } catch (e: IOException) {
            throw CliFailure("cannot write $out: ${e.message}", ExitCode.ERROR, e)
        }
    }
}
