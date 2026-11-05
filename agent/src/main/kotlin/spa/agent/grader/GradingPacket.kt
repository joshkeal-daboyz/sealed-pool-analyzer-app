package spa.agent.grader

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import spa.core.brief.FormatBrief
import spa.core.cards.CardIndex

/** One file of a grading packet: its [name] in the packet directory and its full [text]. */
data class PacketFile(val name: String, val text: String)

/** A grading packet: `instructions.md`, `response.schema.json`, then the card files in order. */
data class GradingPacket(val files: List<PacketFile>)

/**
 * Builds and writes the blind grading packet for a set: the grading prompt and the set's format
 * brief, a schema for one response file, and the sealed pool's cards in files of [CARDS_PER_FILE].
 * The same inputs always give the same bytes. Nothing here reads win rates or calls a model.
 */
object GradingPackets {
    const val CARDS_PER_FILE = 50
    const val INSTRUCTIONS = "instructions.md"
    const val RESPONSE_SCHEMA = "response.schema.json"

    /** The version of the grading prompt, `prompt-v1.md`. */
    const val PROMPT_VERSION = "v1"

    /** The classpath location of the grading prompt. */
    const val PROMPT_RESOURCE = "/spa/agent/grader/prompt-v1.md"

    private val cardFilePattern = Regex("cards-[0-9]+\\.json")

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** The grading prompt's text, with line feeds only. */
    val promptText: String by lazy {
        val stream =
            requireNotNull(GradingPackets::class.java.getResourceAsStream(PROMPT_RESOURCE)) {
                "Missing $PROMPT_RESOURCE on the classpath."
            }
        stream.use { it.readBytes().decodeToString() }.replace("\r\n", "\n")
    }

    /** The schema of one response file. */
    val responseSchema: String =
        """
        |{
        |  "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
        |  "title": "Grading response",
        |  "description": "One response file, cards-NN.response.json: exactly one entry for each card id in cards-NN.json, and no other.",
        |  "type": "array",
        |  "items": {
        |    "type": "object",
        |    "additionalProperties": false,
        |    "required": ["id", "score", "confidence"],
        |    "properties": {
        |      "id": {
        |        "description": "The card's id, copied exactly from the card file.",
        |        "type": "string"
        |      },
        |      "score": {
        |        "description": "0.0 to 5.0 in steps of 0.5, relative to this set.",
        |        "type": "number",
        |        "minimum": 0,
        |        "maximum": 5,
        |        "multipleOf": 0.5
        |      },
        |      "confidence": {
        |        "description": "How sure the grader is of the score, 0 to 1.",
        |        "type": "number",
        |        "minimum": 0,
        |        "maximum": 1
        |      }
        |    }
        |  }
        |}
        |"""
            .trimMargin()

    /** `cards-NN.json` for the [number]th card file, counting from 1. */
    fun cardFileName(number: Int): String = String.format(Locale.ROOT, "cards-%02d.json", number)

    /** The response file a grader writes beside [cardFileName]: `cards-NN.response.json`. */
    fun responseFileName(cardFileName: String): String =
        cardFileName.removeSuffix(".json") + ".response.json"

    /** True when [fileName] is a card file's name, `cards-<digits>.json`. */
    fun isCardFile(fileName: String): Boolean = cardFilePattern.matches(fileName)

    /** The packet for [index]'s sealed pool and its [brief], [cardsPerFile] cards per card file. */
    fun build(
        index: CardIndex,
        brief: FormatBrief,
        cardsPerFile: Int = CARDS_PER_FILE,
    ): GradingPacket {
        require(cardsPerFile > 0) { "cardsPerFile must be positive, got $cardsPerFile" }
        val cards = GradingPool.printings(index).map(PacketCard::of)
        val chunks = cards.chunked(cardsPerFile)
        val cardFiles = chunks.mapIndexed { i, chunk ->
            PacketFile(
                cardFileName(i + 1),
                json.encodeToString(ListSerializer(PacketCard.serializer()), chunk) + "\n",
            )
        }
        val instructions =
            promptText.trimEnd() +
                "\n\n" +
                renderBrief(brief) +
                "\n" +
                describeFiles(cards.size, cardFiles.map { it.name }, cardsPerFile)
        return GradingPacket(
            listOf(
                PacketFile(INSTRUCTIONS, instructions),
                PacketFile(RESPONSE_SCHEMA, responseSchema),
            ) + cardFiles
        )
    }

    /** The brief as readable Markdown: speed, mechanics with rules text, and the archetypes. */
    fun renderBrief(brief: FormatBrief): String = buildString {
        appendLine("## The format: ${brief.setName}")
        appendLine()
        appendLine(
            "This is the format description for the set you are grading. It was written from the " +
                "cards alone, before the set was played."
        )
        appendLine()
        appendLine("### Speed")
        appendLine()
        appendLine(
            "This format is expected to play ${brief.speed.name.lowercase()}. ${brief.speedNotes}"
        )
        appendLine()
        appendLine("### Mechanics")
        appendLine()
        brief.mechanics.forEach { mechanic ->
            val reminder = mechanic.reminderText?.let { " Reminder text: $it" }.orEmpty()
            appendLine("- **${mechanic.name}.** ${mechanic.rulesText}$reminder")
        }
        appendLine()
        appendLine("### Archetypes")
        appendLine()
        appendLine(
            "Each archetype is a color pair with a plan. Its signpost uncommons are the cards " +
                "that show the plan."
        )
        appendLine()
        brief.archetypes.forEach { archetype ->
            appendLine(
                "- **${archetype.colors}, ${archetype.name}.** ${archetype.plan} " +
                    "Signpost uncommons: ${archetype.signpostUncommons.joinToString("; ")}."
            )
        }
    }

    private fun describeFiles(cardCount: Int, cardFiles: List<String>, cardsPerFile: Int): String {
        val range =
            if (cardFiles.size <= 1) "`${cardFiles.firstOrNull() ?: cardFileName(1)}`"
            else "`${cardFiles.first()}` to `${cardFiles.last()}`"
        val responses = cardFiles.map(::responseFileName)
        val responseRange =
            if (responses.size <= 1)
                "`${responses.firstOrNull() ?: responseFileName(cardFileName(1))}`"
            else "`${responses.first()}` to `${responses.last()}`"
        return buildString {
            appendLine("## The files in this packet")
            appendLine()
            appendLine("- `$INSTRUCTIONS`: this file.")
            appendLine(
                "- $range: the $cardCount cards to grade, $cardsPerFile per file except the last, " +
                    "in collector order. Each card has `id`, `name`, `manaCost`, `typeLine`, " +
                    "`rarity`, `oracleText`, `power` and `toughness`. A card with two faces has " +
                    "both faces in each field, separated by ` // `."
            )
            appendLine("- `$RESPONSE_SCHEMA`: the JSON schema of one response file.")
            appendLine("- You write $responseRange, one beside each card file.")
        }
    }

    /**
     * Writes [packet] into [dir], creating it. Earlier packet files there (the instructions, the
     * schema and every card file) are removed first; response files are kept.
     */
    fun write(packet: GradingPacket, dir: Path) {
        Files.createDirectories(dir)
        dir.listDirectoryEntries()
            .filter { it.name == INSTRUCTIONS || it.name == RESPONSE_SCHEMA || isCardFile(it.name) }
            .forEach(Files::delete)
        packet.files.forEach { Files.writeString(dir.resolve(it.name), it.text) }
    }
}
