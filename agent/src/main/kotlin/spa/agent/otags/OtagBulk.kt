package spa.agent.otags

import java.nio.file.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One card tagged with a tag in the bulk file: its Scryfall [oracleId] and the tag's [weight]. */
@Serializable
data class OtagTagging(
    @SerialName("oracle_id") val oracleId: String,
    val weight: String? = null,
)

/** One line of Scryfall's Oracle Tags bulk file: a Tagger tag. */
@Serializable
data class OtagTag(
    val id: String,
    val slug: String,
    val type: String,
    @SerialName("parent_ids") val parentIds: List<String> = emptyList(),
    val taggings: List<OtagTagging> = emptyList(),
)

/** Scryfall's Oracle Tags bulk file (SPA-054). */
class OtagBulk(val tags: List<OtagTag>) {
    /** The slug of every tag in the file, of any type. */
    val slugs: Set<String> = emptySet()

    /** The oracle tags of the card with [oracleId], with every ancestor, sorted. */
    fun otags(oracleId: String): List<String> = emptyList()

    companion object {
        /** The tag type this tool reads. */
        const val ORACLE = "oracle"

        /** The bulk file in [lines], one tag per non-blank line. */
        fun parse(lines: Sequence<String>): OtagBulk = OtagBulk(emptyList())

        /** The bulk file at [path], plain JSON lines or gzipped. */
        fun read(path: Path): OtagBulk = OtagBulk(emptyList())
    }
}
