package spa.agent.tags

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import spa.agent.grader.GradingPacket
import spa.agent.grader.GradingPackets
import spa.agent.grader.PacketFile
import spa.agent.grader.PacketThemes
import spa.core.brief.FormatBrief
import spa.core.cards.CardIndex

/**
 * What a tagging packet is built from: the set's card [index], its [brief], its theme counts
 * ([themes], or none), and, from tags prompt v2, each card id's [facts] (null for v1).
 */
data class TagPacketSet(
    val index: CardIndex,
    val brief: FormatBrief,
    val themes: PacketThemes?,
    val facts: Map<String, Set<String>>? = null,
)

/**
 * Builds and writes the two blind synergy tagging packets for a set's sealed pool. Both hold the
 * card files exactly as [GradingPackets] writes them (no themes on the cards), and instructions
 * made of the pass's prompt, the format brief as the grading packet renders it, the theme counts as
 * a v2 grading packet carries them, and a description of the files (see [TagPacketText]).
 * - Pass 1, [axesPacket]: the axes prompt and `axes.schema.json`; the tagger writes
 *   `axes.response.json`.
 * - Pass 2, [rolesPacket]: the roles prompt, then the axes, and `response.schema.json`; the tagger
 *   writes `cards-NN.response.json` beside each card file. The axes are also written as
 *   `axes.json`, which `spa tags-import` reads.
 *
 * From tags prompt v2 both passes also carry `facts.json`, each card's facts
 * ([spa.agent.facts.CardFacts]); pass 1 lists the facts with their counts, and pass 2 shows each
 * axis's `suppliedBy`.
 *
 * The same inputs always give the same bytes. Nothing here reads win rates or calls a model.
 */
object TagPackets {
    const val INSTRUCTIONS = "instructions.md"
    const val AXES_SCHEMA = "axes.schema.json"
    const val AXES_RESPONSE = "axes.response.json"
    const val AXES = "axes.json"
    const val RESPONSE_SCHEMA = "response.schema.json"
    const val FACTS = "facts.json"

    /** The most named partners a card may list. */
    const val MAX_PARTNERS = 5

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * Pass 1: the packet in which the tagger names the set's axes. With [facts] (tags prompt v2 on:
     * each card id's facts), the packet adds `facts.json` and a "Card facts" section, and its
     * schema asks for each axis's `suppliedBy`.
     */
    fun axesPacket(
        index: CardIndex,
        brief: FormatBrief,
        themes: PacketThemes?,
        prompt: String,
        facts: Map<String, Set<String>>? = null,
    ): GradingPacket {
        val cardFiles = cardFiles(index, brief)
        val format = if (facts == null) AxesFormat.PLAIN else AxesFormat.SUPPLIED
        val instructions =
            start(prompt) +
                context(brief, themes) +
                facts?.let { TagPacketText.renderFacts(it) + "\n" }.orEmpty() +
                TagPacketText.describeAxesFiles(index, cardFiles.map { it.name }, facts != null)
        return GradingPacket(
            listOf(
                PacketFile(INSTRUCTIONS, instructions),
                PacketFile(AXES_SCHEMA, TagPacketText.axesSchema(format)),
            ) + factsFile(facts) + cardFiles
        )
    }

    /** [axesPacket] for [set]. */
    fun axesPacket(set: TagPacketSet, prompt: String): GradingPacket =
        axesPacket(set.index, set.brief, set.themes, prompt, set.facts)

    /** Pass 2 without card facts (tags prompt v1): [rolesPacket] for a set with no facts. */
    fun rolesPacket(
        index: CardIndex,
        brief: FormatBrief,
        themes: PacketThemes?,
        prompt: String,
        axes: List<Axis>,
    ): GradingPacket = rolesPacket(TagPacketSet(index, brief, themes), prompt, axes)

    /**
     * Pass 2: the packet in which the tagger gives each card's roles on [axes] and partners. With
     * the set's facts (v2 on), the axes show their `suppliedBy` and the packet adds `facts.json`.
     */
    fun rolesPacket(set: TagPacketSet, prompt: String, axes: List<Axis>): GradingPacket {
        val cardFiles = cardFiles(set.index, set.brief)
        val names = cardFiles.map { it.name }
        val instructions =
            start(prompt) +
                TagPacketText.renderAxes(axes) +
                "\n" +
                context(set.brief, set.themes) +
                TagPacketText.describeRolesFiles(set.index, names, set.facts != null)
        return GradingPacket(
            listOf(
                PacketFile(INSTRUCTIONS, instructions),
                PacketFile(RESPONSE_SCHEMA, TagPacketText.responseSchema(axes)),
                PacketFile(
                    AXES,
                    json.encodeToString(ListSerializer(Axis.serializer()), axes) + "\n",
                ),
            ) + factsFile(set.facts) + cardFiles
        )
    }

    /**
     * Writes [packet] into [dir], creating it. Earlier packet files there (the instructions, both
     * schemas, `axes.json`, `facts.json` and every card file) are removed first; response files are
     * kept.
     */
    fun write(packet: GradingPacket, dir: Path) {
        val packetFiles = setOf(INSTRUCTIONS, AXES_SCHEMA, RESPONSE_SCHEMA, AXES, FACTS)
        Files.createDirectories(dir)
        dir.listDirectoryEntries()
            .filter { it.name in packetFiles || GradingPackets.isCardFile(it.name) }
            .forEach(Files::delete)
        packet.files.forEach { Files.writeString(dir.resolve(it.name), it.text) }
    }

    /** `facts.json`: each card id, in pool order, with its sorted facts; none without [facts]. */
    internal fun factsFile(facts: Map<String, Set<String>>?): List<PacketFile> =
        if (facts == null) emptyList()
        else {
            val sorted = facts.mapValues { (_, it) -> it.sorted() }
            val text =
                json.encodeToString(
                    MapSerializer(String.serializer(), ListSerializer(String.serializer())),
                    sorted,
                )
            listOf(PacketFile(FACTS, text + "\n"))
        }

    /** The card files exactly as the grading packet writes them. */
    private fun cardFiles(index: CardIndex, brief: FormatBrief): List<PacketFile> =
        GradingPackets.build(index, brief).files.filter { GradingPackets.isCardFile(it.name) }

    private fun start(prompt: String): String = prompt.replace("\r\n", "\n").trimEnd() + "\n\n"

    private fun context(brief: FormatBrief, themes: PacketThemes?): String =
        GradingPackets.renderBrief(brief) + "\n" + themes?.let { it.render() + "\n" }.orEmpty()
}
