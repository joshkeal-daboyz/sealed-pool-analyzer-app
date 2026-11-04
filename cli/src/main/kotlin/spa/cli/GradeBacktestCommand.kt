package spa.cli

import java.io.PrintStream
import java.nio.file.Path
import spa.agent.backtest.Backtest
import spa.agent.grade.GradeFile
import spa.agent.grade.GradeFiles

/**
 * `spa grade-backtest --set <code> [--model <file>] [--winrates <file>] [--data <dir>]`: the
 * Spearman rank correlation between a model grade file (default `<data>/grades/<code>.json`) and a
 * win-rate grade file (default `<data>/winrates/<code>.json`), over all cards and by rarity.
 *
 * Rarity comes from the set's Scryfall cache under `<data>`. Prints the report's lines (see
 * [spa.agent.backtest.BacktestReport.lines]) and exits 0; a missing or invalid file is one line and
 * exit 1. Makes no network or model call.
 */
object GradeBacktestCommand {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("set", "model", "winrates", "data"))
        if (parsed.positionals.isNotEmpty()) {
            throw CliFailure("unexpected argument '${parsed.positionals.first()}'", ExitCode.USAGE)
        }
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val modelPath =
            parsed.value("model")?.let(Path::of) ?: data.root.resolve("grades").resolve("$set.json")
        val winRatePath =
            parsed.value("winrates")?.let(Path::of)
                ?: data.root.resolve("winrates").resolve("$set.json")
        val model = load(modelPath, "model grade file")
        val winRates = load(winRatePath, "win-rate grade file")
        val index = Commands.loadIndex(data, set)

        val report =
            Backtest.run(model, winRates) { setCode, number ->
                index.byCollectorNumber(setCode, number)?.rarity
            }
        report.lines().forEach(stdout::println)
        stdout.flush()
        return ExitCode.OK
    }

    private fun load(path: Path, what: String): GradeFile {
        val text = Commands.readFile(path, what)
        return Commands.guard("invalid $what $path") { GradeFiles.parse(text) }
    }
}
