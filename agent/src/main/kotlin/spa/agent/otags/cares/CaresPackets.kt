package spa.agent.otags.cares

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import spa.agent.facts.OtagFactRule
import spa.agent.grader.GradingPacket
import spa.agent.grader.GradingPackets
import spa.agent.grader.PacketFile
import spa.agent.otags.CaresTable
import spa.agent.otags.OtagTagsFile
import spa.agent.tags.TagPacketSet
import spa.agent.tags.TagPacketText
import spa.agent.tags.TagPackets
import spa.core.cards.CardIndex

/** One cares tag in a `cares-NN.json`: the [fact] and its [strength], 1 to 3. */
@Serializable data class PacketCare(val fact: String, val strength: Int)

/** One card of a `cares-NN.json`: its packet [id] and the cares tags it already has. */
@Serializable data class PacketCaresCard(val id: String, val cares: List<PacketCare>)

/**
 * What a cares packet is built from: the set ([set]: its card index, brief, theme counts and each
 * card id's facts, code facts and every `otag:` fact), its Scryfall tags [otags] by card id, the
 * [table] (`cares.json`) and the set's [tags] file.
 */
class CaresPacketSet(
    val set: TagPacketSet,
    val otags: Map<String, List<String>>,
    val table: CaresTable,
    val tags: OtagTagsFile,
)

/**
 * Builds and writes the blind cares-gaps packet for a set's sealed pool (SPA-055):
 * - `instructions.md`: the prompt, then the cares table as a readable list, then the format brief
 *   and theme counts as the tags packets render them, then a description of the files;
 * - the card files exactly as [GradingPackets] writes them;
 * - `facts.json`: each card's code facts and `otag:` facts, name tags left out;
 * - `cares-NN.json` beside each card file: each card's cares tags from the cares table;
 * - `vocabulary.json` ([CaresVocabulary]), `cares-gaps.schema.json` and
 *   `cares-table-suggestions.schema.json`.
 *
 * Fable writes `cards-NN.cares.json` beside each card file and, optionally,
 * `cares-table-suggestions.json`. The same inputs always give the same bytes. Nothing here reads
 * win rates or the owner's checks, or calls a model.
 */
object CaresPackets {
    const val INSTRUCTIONS = "instructions.md"
    const val VOCABULARY = "vocabulary.json"
    const val SUGGESTIONS = "cares-table-suggestions.json"

    private val caresFilePattern = Regex("cares-[0-9]+\\.json")

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * The cares file beside the card file [cardFileName]: `cards-NN.json` gives `cares-NN.json`.
     */
    fun caresFileName(cardFileName: String): String = "cares-" + cardFileName.removePrefix("cards-")

    /** The response beside [cardFileName]: `cards-NN.cares.json`. */
    fun responseFileName(cardFileName: String): String =
        cardFileName.removeSuffix(".json") + ".cares.json"

    /** The card file a response file belongs to: `cards-NN.cares.json` gives `cards-NN.json`. */
    fun cardFileName(responseFileName: String): String =
        responseFileName.removeSuffix(".cares.json") + ".json"

    /** The cares-gaps packet for [set], with the prompt [prompt]. */
    fun build(set: CaresPacketSet, prompt: String): GradingPacket {
        val cardFiles =
            GradingPackets.build(set.set.index, set.set.brief).files.filter {
                GradingPackets.isCardFile(it.name)
            }
        val names = set.tags.nameTags().map { OtagFactRule.PREFIX + it }.toSet()
        val facts = set.set.facts.orEmpty().mapValues { (_, facts) -> facts - names }
        val codeFacts = facts.values.flatten().filterNot { it.startsWith(OtagFactRule.PREFIX) }
        val vocabulary = CaresVocabulary.build(codeFacts.toSet(), set.tags)
        val caresFiles = cardFiles.map { file ->
            val cards = ids(file.text).map { PacketCaresCard(it, caresOf(set, it)) }
            PacketFile(
                caresFileName(file.name),
                json.encodeToString(ListSerializer(PacketCaresCard.serializer()), cards) + "\n",
            )
        }
        val instructions =
            prompt.replace("\r\n", "\n").trimEnd() +
                "\n\n" +
                renderTable(set.table) +
                "\n" +
                GradingPackets.renderBrief(set.set.brief) +
                "\n" +
                set.set.themes?.let { it.render() + "\n" }.orEmpty() +
                describeFiles(set.set.index, cardFiles.map { it.name })
        return GradingPacket(
            listOf(
                PacketFile(INSTRUCTIONS, instructions),
                PacketFile(
                    CaresResources.GAPS_SCHEMA,
                    CaresResources.text(CaresResources.GAPS_SCHEMA),
                ),
                PacketFile(
                    CaresResources.SUGGESTIONS_SCHEMA,
                    CaresResources.text(CaresResources.SUGGESTIONS_SCHEMA),
                ),
                PacketFile(VOCABULARY, CaresVocabulary.encode(vocabulary)),
            ) + TagPackets.factsFile(facts) + cardFiles + caresFiles
        )
    }

    /**
     * Writes [packet] into [dir], creating it. Earlier packet files there (the instructions, both
     * schemas, `facts.json`, `vocabulary.json`, every card file and every cares file) are removed
     * first; response files and suggestions are kept.
     */
    fun write(packet: GradingPacket, dir: Path) {
        val packetFiles =
            setOf(
                INSTRUCTIONS,
                CaresResources.GAPS_SCHEMA,
                CaresResources.SUGGESTIONS_SCHEMA,
                VOCABULARY,
                TagPackets.FACTS,
            )
        Files.createDirectories(dir)
        dir.listDirectoryEntries()
            .filter {
                it.name in packetFiles ||
                    GradingPackets.isCardFile(it.name) ||
                    caresFilePattern.matches(it.name)
            }
            .forEach(Files::delete)
        packet.files.forEach { Files.writeString(dir.resolve(it.name), it.text) }
    }

    /** The card ids in a card file's [text], in order. */
    internal fun ids(text: String): List<String> =
        Json.parseToJsonElement(text).jsonArray.map {
            it.jsonObject.getValue("id").jsonPrimitive.content
        }

    private fun caresOf(set: CaresPacketSet, id: String): List<PacketCare> =
        set.table.of(set.otags[id].orEmpty()).toSortedMap().map { (fact, strength) ->
            PacketCare(fact, strength)
        }

    private fun renderTable(table: CaresTable): String = buildString {
        appendLine("## The cares table")
        appendLine()
        appendLine(
            "Each Scryfall tag below means caring about the facts listed, with the strength " +
                "given. A card with the tag already cares about those facts."
        )
        appendLine()
        table.cares.forEach { (tag, entries) ->
            appendLine("- `$tag`: " + entries.joinToString(", ") { "`${it.fact}` ${it.strength}" })
        }
    }

    private fun describeFiles(index: CardIndex, cardFiles: List<String>): String = buildString {
        val caresFiles = cardFiles.map(::caresFileName)
        val responses = cardFiles.map(::responseFileName)
        appendLine("## The files in this folder")
        appendLine()
        appendLine("- `$INSTRUCTIONS`: this file.")
        appendLine(TagPacketText.cardsLine(index, cardFiles))
        appendLine("- `${TagPackets.FACTS}`: each card's facts, by card id.")
        appendLine(
            "- `$VOCABULARY`: every fact name a cares tag may use, each as `fact` with its " +
                "`description`."
        )
        appendLine(
            "- ${TagPacketText.range(caresFiles)}: the cares tags of the cards of the card file " +
                "with the same number, in the same order: each card's `id` and `cares` " +
                "(`fact` and `strength`)."
        )
        appendLine("- `${CaresResources.GAPS_SCHEMA}`: the JSON schema of one response file.")
        appendLine("- `${CaresResources.SUGGESTIONS_SCHEMA}`: the JSON schema of `$SUGGESTIONS`.")
        appendLine(
            "- You write ${TagPacketText.range(responses)}, one beside each card file, and " +
                "`$SUGGESTIONS` when you have a suggestion."
        )
    }
}
