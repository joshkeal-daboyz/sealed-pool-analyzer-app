package spa.cli

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import kotlin.io.path.extension
import spa.agent.ApiKey
import spa.agent.MissingApiKeyException
import spa.agent.model.AnthropicModelClient
import spa.agent.model.BudgetedModelClient
import spa.agent.model.ModelClient
import spa.agent.model.RecordingModelClient
import spa.agent.model.ReplayModelClient
import spa.agent.model.UnrecordedRequestException
import spa.agent.review.AnalysisAgent
import spa.agent.review.AnalysisAgents
import spa.agent.review.ReviewJson
import spa.agent.review.ReviewRun
import spa.agent.review.ReviewText

/**
 * `spa review <pool-file> --set <code> [--data <dir>] [--format manabox|text|collector]
 * [--brief <file>] [--weights <file>] [--tags <file>] [--otags <file>|none] [--config <file>]
 * [--model <id>] [--max-input-tokens <n>] [--max-output-tokens <n>] [--json] [--dry-run]
 * [--show-input] [--record <dir>] [--replay <dir>]` (SPA-070): the pool review of G-006. The
 * builder's top three decks and the seven analysts' findings, explained by one more call, are
 * printed as text or, with `--json`, as the structured review.
 *
 * The key comes only from `ANTHROPIC_API_KEY`; without it, and without `--dry-run`, `--show-input`
 * or `--replay`, the command prints `ANTHROPIC_API_KEY is not set` and exits 1 before it reads a
 * file. `--dry-run` plans the calls and prints their estimated size; `--show-input` prints what the
 * agents are given; `--record <dir>` writes every exchange as a recording that `--replay <dir>`
 * answers from, offline. Deck weights come from `--weights`, else `<data>/weights/deck-tuned.json`;
 * the config from `--config`, else `<data>/agents/review.json`, whose model and caps `--model`,
 * `--max-input-tokens` and `--max-output-tokens` replace. A set with no tag file reviews without
 * the Synergy Detector; a set with no baseline reviews without `typical`.
 */
object ReviewCommand {
    private val valueOptions =
        setOf(
            "set",
            "data",
            "format",
            "brief",
            "weights",
            "tags",
            Otags.OPTION,
            "config",
            "model",
            "max-input-tokens",
            "max-output-tokens",
            "record",
            "replay",
        )
    private val flagOptions = setOf("json", "dry-run", "show-input")

    /** The client factory a live run uses: the Anthropic client, keyed from the environment. */
    private val liveClients: (Map<String, String>) -> ModelClient = {
        AnthropicModelClient.fromEnvironment(it)
    }

    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int =
        run(args, stdout, stderr, System.getenv(), AnalysisAgents.ALL, liveClients)

    /**
     * [run] with the environment, the analysts and the client [clients] makes from the environment
     * supplied, so a test never reads the real environment or reaches the network.
     */
    @Suppress("LongParameterList") // the seams a test needs: environment, agents and client
    internal fun run(
        args: List<String>,
        stdout: PrintStream,
        stderr: PrintStream,
        environment: Map<String, String>,
        agents: List<AnalysisAgent>,
        clients: (Map<String, String>) -> ModelClient,
    ): Int {
        val parsed = Args.parse(args, valueOptions, flagOptions)
        val file = Commands.onePositional(parsed, "pool file")
        val caps = Caps.of(parsed)
        checkCombination(parsed)
        val set = Commands.requiredSet(parsed)
        val offline = parsed.has("dry-run") || parsed.has("show-input")
        val replaying = parsed.value("replay") != null
        val missingKey = if (offline || replaying) null else keyProblem(environment)
        if (missingKey != null) {
            stderr.println(missingKey)
            return ExitCode.ERROR
        }
        val options = ReviewOptions(parsed, file, set, agents, caps)
        val setup = ReviewSetup.load(options, stderr)
        val text =
            when {
                parsed.has("dry-run") -> ReviewPreview.dryRun(file, setup, agents)
                parsed.has("show-input") -> ReviewPreview.showInput(setup, agents)
                else -> review(options, setup, environment, clients)
            }
        stdout.print(text)
        stdout.flush()
        stderr.flush()
        return ExitCode.OK
    }

    /** `--replay` with `--record`, and a preview with either, are a bad command line. */
    private fun checkCombination(parsed: Args) {
        val record = parsed.value("record") != null
        val replay = parsed.value("replay") != null
        val preview = parsed.has("dry-run") || parsed.has("show-input")
        val message =
            when {
                record && replay -> "--replay and --record cannot be used together"
                preview && (record || replay) ->
                    "--dry-run and --show-input send no call, so --record and --replay do not apply"
                else -> null
            }
        if (message != null) throw CliFailure(message, ExitCode.USAGE)
    }

    private fun keyProblem(environment: Map<String, String>): String? =
        try {
            ApiKey.require(environment)
            null
        } catch (e: MissingApiKeyException) {
            e.message
        }

    /** The review of [setup], run through the client chain and printed as text or JSON. */
    private fun review(
        options: ReviewOptions,
        setup: ReviewSetup,
        environment: Map<String, String>,
        clients: (Map<String, String>) -> ModelClient,
    ): String {
        val parsed = options.parsed
        val record = parsed.value("record")?.let(Path::of)
        if (record != null) checkEmpty(record)
        val base = parsed.value("replay")?.let { replay(Path.of(it)) } ?: clients(environment)
        try {
            val recorded = if (record == null) base else RecordingModelClient(base, record)
            val config = setup.config
            val client =
                BudgetedModelClient(recorded, config.maxInputTokens, config.maxOutputTokens)
            val run = ReviewRun(client, { client.totals }, config, options.agents)
            val review = unrecorded { run.run(setup.input, setup.decks, setup.header) }
            return if (parsed.has("json")) ReviewJson.render(review) else ReviewText.render(review)
        } finally {
            (base as? AutoCloseable)?.close()
        }
    }

    private fun replay(directory: Path): ModelClient {
        if (!Files.isDirectory(directory)) {
            throw CliFailure("replay directory not found: $directory", ExitCode.ERROR)
        }
        return Commands.guard("cannot read the recordings in $directory") {
            ReplayModelClient.fromDirectory(directory)
        }
    }

    /** A recording directory must not hold recordings: a recording is never overwritten. */
    private fun checkEmpty(directory: Path) {
        val holdsRecordings =
            Files.isDirectory(directory) &&
                Files.list(directory).use { files -> files.anyMatch { it.extension == "json" } }
        if (holdsRecordings) {
            throw CliFailure("record directory $directory already holds recordings", ExitCode.ERROR)
        }
    }

    /** Runs [block]; a request with no recording is one line, not a stack trace. */
    private fun <T> unrecorded(block: () -> T): T =
        try {
            block()
        } catch (e: UnrecordedRequestException) {
            throw noRecording(e)
        } catch (e: ExecutionException) {
            throw if (e.cause is UnrecordedRequestException) noRecording(e) else e
        }

    private fun noRecording(cause: Throwable) =
        CliFailure(
            "the replayed recordings have no answer for a request of this review; " +
                "the pool, a prompt, the weights or the config changed since they were recorded",
            ExitCode.ERROR,
            cause,
        )
}
