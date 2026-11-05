package spa.agent.tags

import java.nio.file.Path
import spa.agent.grader.GradingPacket
import spa.agent.grader.PacketThemes
import spa.core.brief.FormatBrief
import spa.core.cards.CardIndex

/** Builds and writes the two blind tagging packets. */
object TagPackets {
    const val INSTRUCTIONS = "instructions.md"
    const val AXES_SCHEMA = "axes.schema.json"
    const val AXES_RESPONSE = "axes.response.json"
    const val AXES = "axes.json"
    const val RESPONSE_SCHEMA = "response.schema.json"

    fun axesPacket(
        index: CardIndex,
        brief: FormatBrief,
        themes: PacketThemes?,
        prompt: String,
    ): GradingPacket = TODO("SPA-047 $index $brief $themes $prompt")

    fun rolesPacket(
        index: CardIndex,
        brief: FormatBrief,
        themes: PacketThemes?,
        prompt: String,
        axes: List<Axis>,
    ): GradingPacket = TODO("SPA-047 $index $brief $themes $prompt $axes")

    fun write(packet: GradingPacket, dir: Path): Unit = TODO("SPA-047 $packet $dir")
}
