package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import spa.agent.model.ModelClient
import spa.agent.model.ModelRequest
import spa.agent.model.ModelResponse
import spa.agent.model.ModelUsage
import spa.agent.model.TokenEstimate
import spa.agent.review.AnalysisAgent
import spa.agent.review.AnalysisAgents
import spa.agent.review.NumberTable
import spa.agent.review.NumberTables
import spa.agent.review.ReviewInput

/** The key the tests put in the environment of a run that uses a fake client. */
internal const val TEST_KEY = "test-key-1234"

/** An environment holding [TEST_KEY]. */
internal val KEY_ENV: Map<String, String> = mapOf("ANTHROPIC_API_KEY" to TEST_KEY)

/**
 * Runs `spa review <args>` in process (SPA-070) with [env] as the environment, [agents] as the
 * analysts and [clients] as the client factory; a factory the test does not give fails the test if
 * the command calls it. A [CliFailure] is printed as `Main` prints it.
 */
internal fun runReview(
    vararg args: String,
    env: Map<String, String> = emptyMap(),
    agents: List<AnalysisAgent> = AnalysisAgents.ALL,
    clients: (Map<String, String>) -> ModelClient = { error("no client is expected") },
): CliRun {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val stdout = PrintStream(out, true, StandardCharsets.UTF_8)
    val stderr = PrintStream(err, true, StandardCharsets.UTF_8)
    val exit =
        try {
            ReviewCommand.run(args.toList(), stdout, stderr, env, agents, clients)
        } catch (e: CliFailure) {
            stderr.println("spa: ${e.message}")
            e.exitCode
        }
    return CliRun(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8))
}

/**
 * A data directory under [dir] holding what a review of set `trk` reads, copied from the committed
 * fixtures: the Scryfall caches, the brief, the deck weights and the review config always, and the
 * tag file, the baseline and the grade files unless left out.
 */
internal fun trkData(
    dir: Path,
    tags: Boolean = true,
    baseline: Boolean = true,
    grades: Boolean = true,
): Path {
    for (set in listOf("trk", "sds")) {
        val cache = dir.resolve("scryfall/$set").createDirectories()
        Fixtures.root.resolve("scryfall/$set").listDirectoryEntries().forEach {
            it.copyTo(cache.resolve(it.fileName.toString()))
        }
    }
    val files =
        listOf("briefs/trk.json", "weights/deck-tuned.json", "agents/review.json") +
            (if (tags) listOf("tags/trk.json") else emptyList()) +
            (if (baseline) listOf("baselines/trk.json") else emptyList()) +
            (if (grades) listOf("grades/trk.json", "grades/trk.v2.json") else emptyList())
    for (file in files) {
        val target = dir.resolve(file)
        target.parent.createDirectories()
        Fixtures.root.resolve(file).copyTo(target)
    }
    return dir
}

/** A test-only analyst: it quotes the pool's size and is added to the list the command runs. */
internal object ProbeAgent : AnalysisAgent {
    override val id: String = "probe"
    override val title: String = "Probe notes"

    override fun numbers(input: ReviewInput): NumberTable =
        NumberTables.fromMetrics(input, listOf("/totalCards"))

    override fun facts(input: ReviewInput): String = ""

    override fun unavailable(input: ReviewInput): String? = null
}

/** The review config of [ids] (each switched on) in the fixtures' format. */
internal fun configFor(ids: List<String>): String {
    val agents =
        ids.joinToString(",\n") {
            """    { "id": "$it", "enabled": true, "maxTokens": 3000, "prompt": "v1" }"""
        }
    return """
        {
          "version": 1,
          "model": "claude-sonnet-5-5",
          "maxInputTokens": 300000,
          "maxOutputTokens": 60000,
          "callTimeoutSeconds": 120,
          "parallel": 6,
          "retries": 1,
          "maxFindings": 8,
          "agents": [
        $agents
          ],
          "explainer": { "enabled": true, "maxTokens": 6000, "prompt": "v1" }
        }
        """
        .trimIndent() + "\n"
}

/** What a [CannedClient] answers for one agent: one finding quoting [keys] and citing [cards]. */
internal class Script(
    val summary: String,
    val claim: String,
    val pair: String?,
    val cards: List<String>,
    val keys: List<String>,
)

/**
 * A fake model, for tests only: it answers each analyst with a valid hand-written answer that cites
 * real cards of the `trk` sample pool and quotes keys of that agent's table with the table's own
 * values, and the Explainer with a valid answer holding one disagreement. It reaches no network.
 * The answers are canned, not a model's output.
 */
internal class CannedClient(private val extra: Map<String, Script> = emptyMap()) : ModelClient {
    private val sent = CopyOnWriteArrayList<ModelRequest>()

    /** Every request this client was sent. */
    val requests: List<ModelRequest>
        get() = sent.toList()

    override fun send(request: ModelRequest): ModelResponse {
        sent += request
        val text =
            Json.encodeToString(
                JsonElement.serializer(),
                if (request.label == "explainer") explainer() else analyst(request),
            )
        val usage =
            ModelUsage(TokenEstimate.of(request), (text.length / CHARACTERS_PER_TOKEN).toLong())
        return ModelResponse(request.model, text, "end_turn", usage)
    }

    private fun analyst(request: ModelRequest): JsonElement {
        val script = (SCRIPTS + extra).getValue(request.label)
        return buildJsonObject {
            put("agent", request.label)
            put("confidence", CONFIDENCE)
            put("summary", script.summary)
            putJsonArray("findings") {
                add(
                    buildJsonObject {
                        put("claim", script.claim)
                        put("pair", script.pair)
                        put("severity", "warn")
                        putJsonArray("cards") { script.cards.forEach { add(JsonPrimitive(it)) } }
                        putJsonArray("numbers") {
                            script.keys.forEach { key ->
                                add(
                                    buildJsonObject {
                                        put("key", key)
                                        put("value", valueOf(request, key))
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    private fun explainer(): JsonElement = buildJsonObject {
        put("confidence", CONFIDENCE)
        put(
            "verdict",
            "Play the top ranked deck: it has the cleanest mana and the most removal. " +
                "The pool is thin on cheap interaction, so keep an eye on tempo.",
        )
        putJsonArray("decks") {
            add(
                buildJsonObject {
                    put("rank", 1)
                    put(
                        "note",
                        "The builder's first deck plays the strongest removal and the best " +
                            "creatures the pool holds.",
                    )
                    putJsonArray("cards") {
                        add(JsonPrimitive("He's Dead, Jim"))
                        add(JsonPrimitive("Command Decision"))
                    }
                }
            )
        }
        putJsonArray("disagreements") {
            add(
                buildJsonObject {
                    put("topic", "Fixing against removal")
                    putJsonArray("agents") {
                        add(JsonPrimitive("mana"))
                        add(JsonPrimitive("interaction"))
                    }
                    putJsonArray("cards") {
                        add(JsonPrimitive("Bajor"))
                        add(JsonPrimitive("Plasma Cascade"))
                    }
                    put(
                        "note",
                        "The mana analyst wants the dual lands in the deck; the interaction " +
                            "analyst would rather spend the slots on more removal.",
                    )
                }
            )
        }
    }

    /** The table's value for [key], read from the request's number table. */
    private fun valueOf(request: ModelRequest, key: String): Double {
        val line =
            request.userMessage.lineSequence().firstOrNull { it.startsWith("$key = ") }
                ?: error("no $key in the table of ${request.label}")
        return line.removePrefix("$key = ").takeWhile { !it.isWhitespace() }.toDouble()
    }

    companion object {
        private const val CONFIDENCE = 0.8
        private const val CHARACTERS_PER_TOKEN = 4

        /** The ids of the analysts, in order, then the Explainer: the files a recording holds. */
        val RECORDING_NAMES: List<String> =
            AnalysisAgents.ALL.map { it.id }.plus("explainer").map { "$it.json" }

        private val SCRIPTS =
            mapOf(
                "colour-depth" to
                    Script(
                        "Black is the deepest colour but thin in commons.",
                        "Black has 14 cards, but only four of them are commons.",
                        null,
                        listOf("He's Dead, Jim", "Khan, Vengeful Heart"),
                        listOf("/byColor/B/cards/total", "/byColor/B/cards/common"),
                    ),
                "curve" to
                    Script(
                        "The white-blue curve has a fair number of cheap creatures.",
                        "White-blue has eight two-mana creatures to hold the early turns.",
                        "WU",
                        listOf("Federation Field Medic", "Una Chin-Riley, Number One"),
                        listOf("/pairs/WU/curve/buckets/2/creatures"),
                    ),
                "mana" to
                    Script(
                        "Fixing is below a typical pool.",
                        "The pool has six fixing sources, fewer than a typical pool.",
                        null,
                        listOf("Bajor", "Cardassia Prime"),
                        listOf("/fixingSources/count"),
                    ),
                "interaction" to
                    Script(
                        "Plenty of removal, almost no other interaction.",
                        "Nine removal spells but a single other interaction card.",
                        null,
                        listOf("Command Decision", "Plasma Cascade"),
                        listOf("/removalTotal", "/interactionTotal"),
                    ),
                "bombs" to
                    Script(
                        "One clean bomb-level common and one colourless mythic.",
                        "He's Dead, Jim is the best card in the pool.",
                        null,
                        listOf("He's Dead, Jim", "Crystalline Entity"),
                        listOf("/grades/He's Dead, Jim"),
                    ),
                "archetypes" to
                    Script(
                        "White-black has a signpost but no key common.",
                        "The white-black Borg deck has one of its two signposts.",
                        "WB",
                        listOf("Locutus, the One Who Speaks"),
                        listOf("/archetypes/WB/signpostsPresent"),
                    ),
                "synergy" to
                    Script(
                        "The artifact creature axis has payoffs to build around.",
                        "Four artifact creature payoffs make the Borg cards work together.",
                        null,
                        listOf("Collective Drone", "Crystalline Entity"),
                        listOf("/axes/artifact-creatures/payoffs"),
                    ),
            )

        /** The script a test gives [ProbeAgent]. */
        val PROBE: Script =
            Script(
                "The probe saw the whole pool.",
                "The pool is as large as a sealed pool should be.",
                null,
                listOf("Bajor"),
                listOf("/totalCards"),
            )
    }
}

/** The text of [path] with line ends as `\n`, for comparing files git may check out with CRLF. */
internal fun normalised(path: Path): String = Files.readString(path).replace("\r\n", "\n")
