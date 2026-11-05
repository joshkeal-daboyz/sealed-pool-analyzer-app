package spa.agent.tags

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import spa.agent.grader.GradingPacket
import spa.agent.grader.GradingPackets
import spa.agent.grader.PacketFile
import spa.agent.grader.PacketThemes
import spa.core.brief.FormatBrief
import spa.core.cards.CardIndex

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
 * The same inputs always give the same bytes. Nothing here reads win rates or calls a model.
 */
object TagPackets {
    const val INSTRUCTIONS = "instructions.md"
    const val AXES_SCHEMA = "axes.schema.json"
    const val AXES_RESPONSE = "axes.response.json"
    const val AXES = "axes.json"
    const val RESPONSE_SCHEMA = "response.schema.json"

    /** The most named partners a card may list. */
    const val MAX_PARTNERS = 5

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** Pass 1: the packet in which the tagger names the set's axes. */
    fun axesPacket(
        index: CardIndex,
        brief: FormatBrief,
        themes: PacketThemes?,
        prompt: String,
    ): GradingPacket {
        val cardFiles = cardFiles(index, brief)
        val instructions =
            start(prompt) +
                context(brief, themes) +
                TagPacketText.describeAxesFiles(index, cardFiles.map { it.name })
        return GradingPacket(
            listOf(
                PacketFile(INSTRUCTIONS, instructions),
                PacketFile(AXES_SCHEMA, TagPacketText.axesSchema),
            ) + cardFiles
        )
    }

    /** Pass 2: the packet in which the tagger gives each card's roles on [axes] and partners. */
    fun rolesPacket(
        index: CardIndex,
        brief: FormatBrief,
        themes: PacketThemes?,
        prompt: String,
        axes: List<Axis>,
    ): GradingPacket {
        val cardFiles = cardFiles(index, brief)
        val instructions =
            start(prompt) +
                TagPacketText.renderAxes(axes) +
                "\n" +
                context(brief, themes) +
                TagPacketText.describeRolesFiles(index, cardFiles.map { it.name })
        return GradingPacket(
            listOf(
                PacketFile(INSTRUCTIONS, instructions),
                PacketFile(RESPONSE_SCHEMA, TagPacketText.responseSchema(axes)),
                PacketFile(
                    AXES,
                    json.encodeToString(ListSerializer(Axis.serializer()), axes) + "\n",
                ),
            ) + cardFiles
        )
    }

    /**
     * Writes [packet] into [dir], creating it. Earlier packet files there (the instructions, both
     * schemas, `axes.json` and every card file) are removed first; response files are kept.
     */
    fun write(packet: GradingPacket, dir: Path) {
        val packetFiles = setOf(INSTRUCTIONS, AXES_SCHEMA, RESPONSE_SCHEMA, AXES)
        Files.createDirectories(dir)
        dir.listDirectoryEntries()
            .filter { it.name in packetFiles || GradingPackets.isCardFile(it.name) }
            .forEach(Files::delete)
        packet.files.forEach { Files.writeString(dir.resolve(it.name), it.text) }
    }

    /** The card files exactly as the grading packet writes them. */
    private fun cardFiles(index: CardIndex, brief: FormatBrief): List<PacketFile> =
        GradingPackets.build(index, brief).files.filter { GradingPackets.isCardFile(it.name) }

    private fun start(prompt: String): String = prompt.replace("\r\n", "\n").trimEnd() + "\n\n"

    private fun context(brief: FormatBrief, themes: PacketThemes?): String =
        GradingPackets.renderBrief(brief) + "\n" + themes?.let { it.render() + "\n" }.orEmpty()
}
