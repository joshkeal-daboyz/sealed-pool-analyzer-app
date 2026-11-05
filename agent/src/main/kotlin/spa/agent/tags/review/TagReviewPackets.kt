package spa.agent.tags.review

import java.nio.file.Path
import kotlinx.serialization.Serializable
import spa.agent.grader.GradingPacket
import spa.agent.tags.TagFile
import spa.agent.tags.TagPacketSet

/** The tag file a review packet was built from: its [path] and the SHA-256 of its bytes. */
@Serializable data class ReviewSource(val path: String, val sha256: String)

/** Builds and writes the review packet. */
object TagReviewPackets {
    const val INSTRUCTIONS = "instructions.md"
    const val TAG_FILE = "tag-file.json"
    const val REVIEW_SCHEMA = "review.schema.json"
    const val SUGGESTIONS_SCHEMA = "fact-suggestions.schema.json"
    const val SUGGESTIONS = "fact-suggestions.json"

    fun tagsFileName(cardFileName: String): String = TODO()

    fun reviewFileName(cardFileName: String): String = TODO()

    fun sha256(bytes: ByteArray): String = TODO()

    fun build(
        set: TagPacketSet,
        prompt: String,
        tags: TagFile,
        source: ReviewSource,
    ): GradingPacket = TODO()

    fun write(packet: GradingPacket, dir: Path): Unit = TODO()
}
