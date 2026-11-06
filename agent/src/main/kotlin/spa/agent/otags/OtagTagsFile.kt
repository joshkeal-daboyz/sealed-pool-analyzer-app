package spa.agent.otags

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * One Scryfall tag of a set's tags file: its [slug], Scryfall's [description] (null when it has
 * none) and the slugs of its [parents], sorted.
 */
@Serializable
data class OtagTagInfo(val slug: String, val description: String?, val parents: List<String>)

/**
 * A set's tags file, `fixtures/otags/<code>.tags.json` (SPA-055): the [set], the bulk file it came
 * from ([source], [updatedAt]), Scryfall's [attribution], and one entry in [tags] for every tag in
 * the set's otags file (ancestors included), sorted by slug. It holds what the otags file leaves
 * out: each tag's description and its place in Scryfall's tag hierarchy.
 */
@Serializable
data class OtagTagsFile(
    val set: String,
    val source: String,
    val updatedAt: String,
    val attribution: String,
    val tags: List<OtagTagInfo>,
) {
    /** The number of tags with a description. */
    val described: Int
        get() = tags.count { it.description != null }

    /**
     * [root] and every tag whose parents lead to it, found through `parents` and never by a list of
     * names; none when [root] is not in the file.
     */
    fun descendantsOf(root: String): Set<String> {
        if (tags.none { it.slug == root }) return emptySet()
        val found = mutableSetOf(root)
        var grew = true
        while (grew) {
            val children = tags.filter { tag ->
                tag.slug !in found && tag.parents.any { it in found }
            }
            found += children.map { it.slug }
            grew = children.isNotEmpty()
        }
        return found
    }

    /** The name tags: `name-matters` and every tag below it ([descendantsOf]). */
    fun nameTags(): Set<String> = descendantsOf(NAME_ROOT)

    /** The file's text: two-space indents, one tag a line, a final line feed. */
    fun encode(): String = buildString {
        appendLine("{")
        appendLine("  \"set\": ${quote(set)},")
        appendLine("  \"source\": ${quote(source)},")
        appendLine("  \"updatedAt\": ${quote(updatedAt)},")
        appendLine("  \"attribution\": ${quote(attribution)},")
        appendLine("  \"tags\": [")
        tags.forEachIndexed { i, tag ->
            val description = tag.description?.let { quote(it) } ?: "null"
            val parents = tag.parents.joinToString(", ", "[", "]") { quote(it) }
            val comma = if (i < tags.size - 1) "," else ""
            appendLine(
                "    { \"slug\": ${quote(tag.slug)}, \"description\": $description, " +
                    "\"parents\": $parents }$comma"
            )
        }
        appendLine("  ]")
        appendLine("}")
    }

    companion object {
        /** The root of the name tags, which the cares packet keeps out of the vocabulary. */
        const val NAME_ROOT = "name-matters"

        /**
         * The tags file of [otags], from the [bulk] file it was made from: the description and
         * parents of every distinct tag the otags file holds.
         */
        fun build(otags: OtagFile, bulk: OtagBulk): OtagTagsFile =
            OtagTagsFile(
                otags.set,
                otags.source,
                otags.updatedAt,
                otags.attribution,
                bulk.tagInfos(otags.cards.flatMap { it.otags }.toSet()),
            )

        /** The tags file in [text]; anything else is an [IllegalArgumentException]. */
        fun parse(text: String): OtagTagsFile =
            try {
                Json.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                throw IllegalArgumentException(e.message, e)
            }

        private fun quote(text: String): String = Json.encodeToString(String.serializer(), text)
    }
}
