package spa.agent.otags

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One card tagged with a tag in the bulk file: its Scryfall [oracleId] and the tag's [weight]. */
@Serializable
data class OtagTagging(@SerialName("oracle_id") val oracleId: String, val weight: String? = null)

/**
 * One line of Scryfall's Oracle Tags bulk file: a Tagger tag, with its [id], [slug], [type]
 * (`oracle` for the tags this tool reads), its [description] (null when Scryfall has none), its
 * parents' ids and the cards it tags.
 */
@Serializable
data class OtagTag(
    val id: String,
    val slug: String,
    val type: String,
    @SerialName("parent_ids") val parentIds: List<String> = emptyList(),
    val taggings: List<OtagTagging> = emptyList(),
    val description: String? = null,
)

/**
 * Scryfall's Oracle Tags bulk file (SPA-054): one tag per line. A card's otags are the oracle tags
 * that tag it, plus every ancestor of those through `parent_ids` that is an oracle tag; a tag of
 * another type is left out, and so are its ancestors through it.
 */
class OtagBulk(val tags: List<OtagTag>) {
    /** The slug of every tag in the file, of any type. */
    val slugs: Set<String> = tags.map { it.slug }.toSet()

    private val oracle: Map<String, OtagTag> =
        tags.filter { it.type == ORACLE }.associateBy { it.id }

    /** The ids of the oracle tags that tag each card, by oracle id. */
    private val direct: Map<String, List<String>> =
        oracle.values
            .flatMap { tag -> tag.taggings.map { it.oracleId to tag.id } }
            .groupBy({ it.first }, { it.second })

    /** The oracle tags of the card with [oracleId], with every ancestor, sorted. */
    fun otags(oracleId: String): List<String> {
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque(direct[oracleId].orEmpty())
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (seen.add(id)) stack.addAll(oracle.getValue(id).parentIds.filter { it in oracle })
        }
        return seen.map { oracle.getValue(it).slug }.sorted()
    }

    /**
     * The oracle tags whose slugs are in [slugs], sorted by slug, each with its description and the
     * slugs of its parents that are oracle tags too, sorted (the parents [otags] follows).
     */
    fun tagInfos(slugs: Set<String>): List<OtagTagInfo> =
        oracle.values
            .filter { it.slug in slugs }
            .map { tag ->
                OtagTagInfo(
                    tag.slug,
                    tag.description,
                    tag.parentIds.mapNotNull { oracle[it]?.slug }.sorted(),
                )
            }
            .sortedBy { it.slug }

    companion object {
        /** The tag type this tool reads. */
        const val ORACLE = "oracle"

        private const val GZIP_MAGIC = 0x8b1f

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * The bulk file in [lines], one tag per non-blank line. A line that is not a tag is an
         * [IllegalArgumentException] naming its line number.
         */
        fun parse(lines: Sequence<String>): OtagBulk =
            OtagBulk(
                lines
                    .withIndex()
                    .filter { it.value.isNotBlank() }
                    .map { (i, line) ->
                        try {
                            json.decodeFromString(OtagTag.serializer(), line)
                        } catch (e: SerializationException) {
                            throw IllegalArgumentException("line ${i + 1}: ${e.message}", e)
                        }
                    }
                    .toList()
            )

        /** The bulk file at [path], plain JSON lines or gzipped (told apart by its first bytes). */
        fun read(path: Path): OtagBulk =
            BufferedInputStream(Files.newInputStream(path)).use { raw ->
                raw.mark(2)
                val magic = raw.read() or (raw.read() shl Byte.SIZE_BITS)
                raw.reset()
                val input: InputStream = if (magic == GZIP_MAGIC) GZIPInputStream(raw) else raw
                input.bufferedReader(StandardCharsets.UTF_8).useLines { parse(it) }
            }
    }
}
