package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import spa.agent.grader.GradingPool
import spa.agent.review.AnalysisAgent
import spa.agent.review.DeckSummary
import spa.agent.review.ReviewConfig
import spa.agent.review.ReviewHeader
import spa.agent.review.ReviewInput
import spa.agent.review.SynergyView
import spa.core.brief.FormatBrief
import spa.core.cards.CardIndex
import spa.core.intake.ParseResult
import spa.core.intake.PoolParser
import spa.core.metrics.MetricEngine

/** The token caps given on the command line; null for one not given. */
internal class Caps(val input: Long?, val output: Long?) {
    companion object {
        /** `--max-input-tokens` and `--max-output-tokens`; a bad number is exit 2. */
        fun of(parsed: Args): Caps =
            Caps(cap(parsed, "max-input-tokens"), cap(parsed, "max-output-tokens"))

        private fun cap(parsed: Args, name: String): Long? {
            val text = parsed.value(name) ?: return null
            val value = text.toLongOrNull()
            if (value == null || value < 1) {
                throw CliFailure("--$name must be a positive whole number: '$text'", ExitCode.USAGE)
            }
            return value
        }
    }
}

/** What one `spa review` command line asks for: the arguments, the pool [file], the [set]. */
internal class ReviewOptions(
    val parsed: Args,
    val file: String,
    val set: String,
    val agents: List<AnalysisAgent>,
    val caps: Caps,
) {
    val data: DataDir = Commands.dataDir(parsed)
}

/**
 * Everything `spa review` resolves from the files before it plans or sends a call (SPA-070): the
 * [input] the agents read, the builder's top [decks], the review's [header] and the [config] with
 * the command line's overrides applied.
 */
internal class ReviewSetup(
    val input: ReviewInput,
    val decks: List<DeckSummary>,
    val header: ReviewHeader,
    val config: ReviewConfig,
) {
    companion object {
        private const val TOP_DECKS = 3

        /** The deck weights used without `--weights` (frozen in G-005). */
        private const val DEFAULT_WEIGHTS = "deck-tuned.json"

        /**
         * Reads the pool, the set's data and the config named by [options]. A missing or invalid
         * file is a one line [CliFailure]; a missing baseline or tag file is not.
         */
        fun load(options: ReviewOptions, stderr: PrintStream): ReviewSetup {
            val parsed = options.parsed
            val data = options.data
            val set = options.set
            val poolText = Commands.readFile(Path.of(options.file), "pool file")
            val config = config(options)
            val tags = SynergyTableCommand.tagsOption(parsed)
            val hasTags = tags != null || Files.isRegularFile(defaultTags(data, set))
            val weights =
                parsed.value("weights")?.let(Path::of)
                    ?: data.root.resolve("weights").resolve(DEFAULT_WEIGHTS)
            val session =
                if (hasTags) {
                    BuildSession.open(data, set, weights, tags, parsed.value(Otags.OPTION))
                } else {
                    BuildSession.openWithoutTags(data, set, weights)
                }
            val brief =
                AnalyzeCommand.loadBrief(
                    parsed.value("brief")?.let(Path::of) ?: data.briefFile(set)
                )
            val result = parse(poolText, options, session.index, brief, stderr)
            val view = if (hasTags) synergyView(options, tags) else null
            val input = input(options, result, brief, view, stderr)
            val decks =
                session.rank(result.pool).take(TOP_DECKS).mapIndexed { i, ranked ->
                    DeckSummary.of(i + 1, ranked)
                }
            return ReviewSetup(input, decks, header(options, result, brief), config)
        }

        private fun defaultTags(data: DataDir, set: String): Path =
            data.root.resolve("tags").resolve("$set.json")

        private fun synergyView(options: ReviewOptions, given: Path?): SynergyView {
            val data = options.data
            val set = options.set
            val file = SynergyTableCommand.tagFile(data, set, given)
            val otags = Otags.load(data, set, options.parsed.value(Otags.OPTION))
            val table = SynergyTableCommand.table(data, set, file, otags)
            return SynergyView(file, table, GradingPool::id)
        }

        private fun input(
            options: ReviewOptions,
            result: ParseResult,
            brief: FormatBrief,
            view: SynergyView?,
            stderr: PrintStream,
        ): ReviewInput {
            val data = options.data
            val set = options.set
            val lookup =
                PoolGradesCommand.lookup(PoolGradesCommand.defaultGradesFile(data, set), set)
            return ReviewInput(
                set = set,
                pool = result.pool,
                metrics = MetricEngine.compute(result.pool),
                baseline = AnalyzeCommand.loadBaseline(data.baselineFile(set), set, stderr),
                brief = brief,
                gradeOf = { lookup.grade(it, set)?.score },
                kitWarnings = result.warnings,
                synergy = view,
            )
        }

        private fun header(
            options: ReviewOptions,
            result: ParseResult,
            brief: FormatBrief,
        ): ReviewHeader =
            ReviewHeader(
                set = options.set,
                setName = brief.setName,
                totalCards = result.pool.totalCount,
                distinct = result.pool.entries.map { it.printing.card.oracleId }.distinct().size,
                unresolved = result.unresolved.size,
                kitWarnings = result.warnings.map { it.message },
            )

        private fun parse(
            text: String,
            options: ReviewOptions,
            index: CardIndex,
            brief: FormatBrief,
            stderr: PrintStream,
        ): ParseResult {
            val format = AnalyzeCommand.format(options.parsed)
            val result =
                Commands.guard("cannot parse pool file ${options.file}") {
                    PoolParser.parse(text, index, format, brief.kitTemplate)
                }
            result.unresolved.forEach {
                stderr.println(
                    "spa: line ${it.lineNumber} unresolved (${it.reason.code}): ${it.rawLine}"
                )
            }
            return result
        }

        private fun config(options: ReviewOptions): ReviewConfig {
            val parsed = options.parsed
            val path =
                parsed.value("config")?.let(Path::of)
                    ?: options.data.root.resolve("agents").resolve("review.json")
            val text = Commands.readFile(path, "config")
            val read =
                Commands.guard("invalid config $path") {
                    ReviewConfig.parse(text, options.agents.map { it.id })
                }
            return read.copy(
                model = parsed.value("model") ?: read.model,
                maxInputTokens = options.caps.input ?: read.maxInputTokens,
                maxOutputTokens = options.caps.output ?: read.maxOutputTokens,
            )
        }
    }
}
