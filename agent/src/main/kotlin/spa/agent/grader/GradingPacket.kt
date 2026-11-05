package spa.agent.grader

import java.nio.file.Path
import spa.core.brief.FormatBrief
import spa.core.cards.CardIndex

/** One file of a grading packet: its [name] in the packet directory and its full [text]. */
data class PacketFile(val name: String, val text: String)

/** A grading packet: `instructions.md`, `response.schema.json`, then the card files in order. */
data class GradingPacket(val files: List<PacketFile>)

/**
 * Builds and writes the blind grading packet for a set: the grading prompt and the set's format
 * brief, a schema for one response file, and the sealed pool's cards in files of [CARDS_PER_FILE].
 * The same inputs always give the same bytes.
 */
object GradingPackets {
    const val CARDS_PER_FILE = 50
    const val INSTRUCTIONS = "instructions.md"
    const val RESPONSE_SCHEMA = "response.schema.json"

    /** The version of the grading prompt, `prompt-v1.md`. */
    const val PROMPT_VERSION = "v1"

    /** The classpath location of the grading prompt. */
    const val PROMPT_RESOURCE = "/spa/agent/grader/prompt-v1.md"

    /** The grading prompt's text, with line feeds only. */
    val promptText: String
        get() = TODO()

    /** The schema of one response file. */
    val responseSchema: String
        get() = TODO()

    /** `cards-NN.json` for the [number]th card file, counting from 1. */
    fun cardFileName(number: Int): String = TODO()

    /** The response file a grader writes beside [cardFileName]: `cards-NN.response.json`. */
    fun responseFileName(cardFileName: String): String = TODO()

    /** True when [fileName] is a card file's name, `cards-<digits>.json`. */
    fun isCardFile(fileName: String): Boolean = TODO()

    /** The packet for [index]'s sealed pool and its [brief], [cardsPerFile] cards per card file. */
    fun build(
        index: CardIndex,
        brief: FormatBrief,
        cardsPerFile: Int = CARDS_PER_FILE,
    ): GradingPacket = TODO()

    /** The brief as readable Markdown: speed, mechanics with rules text, and the archetypes. */
    fun renderBrief(brief: FormatBrief): String = TODO()

    /**
     * Writes [packet] into [dir], creating it. Earlier packet files there (the instructions, the
     * schema and every card file) are removed first; response files are kept.
     */
    fun write(packet: GradingPacket, dir: Path): Unit = TODO()
}
