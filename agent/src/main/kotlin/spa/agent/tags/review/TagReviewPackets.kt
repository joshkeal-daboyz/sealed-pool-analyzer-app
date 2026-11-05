package spa.agent.tags.review

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import spa.agent.grader.GradingPacket
import spa.agent.grader.GradingPackets
import spa.agent.grader.PacketFile
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.agent.tags.TagPacketSet
import spa.agent.tags.TagPacketText
import spa.agent.tags.TagPackets

/**
 * The tag file a review packet was built from, `tag-file.json`: its [path] and the SHA-256 of its
 * bytes ([sha256], lowercase hex), so that the import reads that file and only while it is
 * unchanged.
 */
@Serializable data class ReviewSource(val path: String, val sha256: String)

/** A tag file that cannot be reviewed against the set's pool; the message is one line. */
class ReviewPacketException(message: String) : IllegalArgumentException(message)

/**
 * Builds and writes the blind review packet for a set's tag file (pass 3, SPA-052):
 * - `instructions.md`: the review prompt, then the axes (id, name, definition and `suppliedBy`) as
 *   the roles packet lists them, then the format brief and theme counts as the tags packets render
 *   them, then a description of the files;
 * - the card files exactly as [GradingPackets] writes them;
 * - `facts.json`, each card's facts as the v2 tags packets write it;
 * - `tags-NN.json` beside each `cards-NN.json`: the tag file's entries for that file's cards, in
 *   the same order;
 * - `tag-file.json` ([ReviewSource]), `review.schema.json` and `fact-suggestions.schema.json`.
 *
 * The reviewer writes `cards-NN.review.json` beside each card file and, optionally,
 * `fact-suggestions.json`. The same inputs always give the same bytes. Nothing here reads win rates
 * or calls a model.
 */
object TagReviewPackets {
    const val INSTRUCTIONS = "instructions.md"
    const val TAG_FILE = "tag-file.json"
    const val REVIEW_SCHEMA = "review.schema.json"
    const val SUGGESTIONS_SCHEMA = "fact-suggestions.schema.json"
    const val SUGGESTIONS = "fact-suggestions.json"

    private val tagsFilePattern = Regex("tags-[0-9]+\\.json")

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** The tags file beside the card file [cardFileName]: `cards-NN.json` gives `tags-NN.json`. */
    fun tagsFileName(cardFileName: String): String = "tags-" + cardFileName.removePrefix("cards-")

    /** The review file beside [cardFileName]: `cards-NN.review.json`. */
    fun reviewFileName(cardFileName: String): String =
        cardFileName.removeSuffix(".json") + ".review.json"

    /** The SHA-256 of [bytes], as lowercase hex. */
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** The schema text of the resource [name] under `spa/agent/tags/`. */
    fun schemaText(name: String): String {
        val resource = "/spa/agent/tags/$name"
        val stream =
            requireNotNull(TagReviewPackets::class.java.getResourceAsStream(resource)) {
                "Missing $resource on the classpath."
            }
        return stream.use { it.readBytes().decodeToString() }.replace("\r\n", "\n")
    }

    /**
     * The review packet for [tags], read from [source], over [set]'s sealed pool, with the review
     * [prompt]. Every pool card needs an entry in [tags], and [tags] may hold no other card; else
     * [ReviewPacketException].
     */
    fun build(
        set: TagPacketSet,
        prompt: String,
        tags: TagFile,
        source: ReviewSource,
    ): GradingPacket {
        val cardFiles =
            GradingPackets.build(set.index, set.brief).files.filter {
                GradingPackets.isCardFile(it.name)
            }
        val byId = tags.cards.associateBy { it.id }
        val ids = cardFiles.associate { it.name to ids(it.text) }
        val pooled = ids.values.flatten()
        pooled
            .firstOrNull { it !in byId }
            ?.let {
                throw ReviewPacketException("the tag file ${source.path} has no entry for $it")
            }
        (byId.keys - pooled.toSet()).firstOrNull()?.let {
            throw ReviewPacketException(
                "the tag file ${source.path} has an entry for $it, which is not a card of the pool"
            )
        }
        val tagsFiles = cardFiles.map { file ->
            val entries = ids.getValue(file.name).map(byId::getValue)
            PacketFile(
                tagsFileName(file.name),
                json.encodeToString(ListSerializer(TagCard.serializer()), entries) + "\n",
            )
        }
        val names = cardFiles.map { it.name }
        val instructions =
            prompt.replace("\r\n", "\n").trimEnd() +
                "\n\n" +
                TagPacketText.renderAxes(tags.axes) +
                "\n" +
                GradingPackets.renderBrief(set.brief) +
                "\n" +
                set.themes?.let { it.render() + "\n" }.orEmpty() +
                describeFiles(set, names)
        return GradingPacket(
            listOf(
                PacketFile(INSTRUCTIONS, instructions),
                PacketFile(REVIEW_SCHEMA, schemaText(REVIEW_SCHEMA)),
                PacketFile(SUGGESTIONS_SCHEMA, schemaText(SUGGESTIONS_SCHEMA)),
                PacketFile(TAG_FILE, json.encodeToString(ReviewSource.serializer(), source) + "\n"),
            ) + TagPackets.factsFile(set.facts.orEmpty()) + cardFiles + tagsFiles
        )
    }

    /**
     * Writes [packet] into [dir], creating it. Earlier packet files there (the instructions, both
     * schemas, `tag-file.json`, `facts.json`, every card file and every tags file) are removed
     * first; review files and fact suggestions are kept.
     */
    fun write(packet: GradingPacket, dir: Path) {
        val packetFiles =
            setOf(INSTRUCTIONS, REVIEW_SCHEMA, SUGGESTIONS_SCHEMA, TAG_FILE, TagPackets.FACTS)
        Files.createDirectories(dir)
        dir.listDirectoryEntries()
            .filter {
                it.name in packetFiles ||
                    GradingPackets.isCardFile(it.name) ||
                    tagsFilePattern.matches(it.name)
            }
            .forEach(Files::delete)
        packet.files.forEach { Files.writeString(dir.resolve(it.name), it.text) }
    }

    /** The card ids in a card file's [text], in order. */
    internal fun ids(text: String): List<String> =
        Json.parseToJsonElement(text).jsonArray.map {
            it.jsonObject.getValue("id").jsonPrimitive.content
        }

    private fun describeFiles(set: TagPacketSet, cardFiles: List<String>): String = buildString {
        val tagsFiles = cardFiles.map(::tagsFileName)
        val reviews = cardFiles.map(::reviewFileName)
        appendLine("## The files in this folder")
        appendLine()
        appendLine("- `$INSTRUCTIONS`: this file.")
        appendLine(TagPacketText.cardsLine(set.index, cardFiles))
        appendLine("- `${TagPackets.FACTS}`: each card's facts, by card id.")
        appendLine(
            "- ${TagPacketText.range(tagsFiles)}: the current tags of the cards of the card file " +
                "with the same number, in the same order: each card's `id`, `name`, `roles` " +
                "(`axis`, `role` and `strength`) and `partners` (`id` and `strength`)."
        )
        appendLine("- `$TAG_FILE`: the tag file this packet was made from; leave it unchanged.")
        appendLine("- `$REVIEW_SCHEMA`: the JSON schema of one review file.")
        appendLine("- `$SUGGESTIONS_SCHEMA`: the JSON schema of `$SUGGESTIONS`.")
        appendLine(
            "- You write ${TagPacketText.range(reviews)}, one beside each card file, and " +
                "`$SUGGESTIONS` when you have a suggestion."
        )
    }
}
