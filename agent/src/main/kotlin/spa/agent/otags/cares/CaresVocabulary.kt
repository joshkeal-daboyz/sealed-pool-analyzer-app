package spa.agent.otags.cares

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import spa.agent.facts.OtagFactRule
import spa.agent.otags.OtagTagsFile

/** One fact a cares tag may use, with its [description] (null for a Scryfall tag without one). */
@Serializable data class VocabularyEntry(val fact: String, val description: String?)

/**
 * `vocabulary.json` of the cares packet (SPA-055): every fact name a cares tag may use. That is
 * every code fact present in the set, and every Scryfall tag present in it with its ancestors
 * (`otag:<slug>`), with Scryfall's description where it has one. Name tags are left out: the
 * owner's Andromedan Automatons check must stay independent.
 */
object CaresVocabulary {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * The vocabulary of a set whose cards have the code facts [codeFacts], with the Scryfall tags
     * of [tags]; sorted by fact name.
     */
    fun build(codeFacts: Set<String>, tags: OtagTagsFile): List<VocabularyEntry> {
        val names = tags.nameTags()
        val code = codeFacts.map { VocabularyEntry(it, CodeFactDescriptions.of(it)) }
        val otags =
            tags.tags
                .filter { it.slug !in names }
                .map { VocabularyEntry(OtagFactRule.PREFIX + it.slug, it.description) }
        return (code + otags).sortedBy { it.fact }
    }

    /** The vocabulary's text. */
    fun encode(entries: List<VocabularyEntry>): String =
        json.encodeToString(ListSerializer(VocabularyEntry.serializer()), entries) + "\n"

    /** The vocabulary in [text]; anything else is an [IllegalArgumentException]. */
    fun parse(text: String): List<VocabularyEntry> =
        try {
            json.decodeFromString(ListSerializer(VocabularyEntry.serializer()), text)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalArgumentException(e.message, e)
        }
}
