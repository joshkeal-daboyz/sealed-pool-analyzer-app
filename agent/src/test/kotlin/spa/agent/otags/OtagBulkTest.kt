package spa.agent.otags

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-054 criterion 2: reading Scryfall's Oracle Tags bulk file, on a hand-built one.
 *
 * | Tag            | Type         | Parents        | Cards |
 * |----------------|--------------|----------------|-------|
 * | card-advantage | oracle       | none           | none  |
 * | draw           | oracle       | card-advantage | o2    |
 * | cantrip        | oracle       | draw           | o1    |
 * | bear-art       | illustration | none           | o1    |
 * | under-art      | oracle       | bear-art       | o3    |
 * | cycle-x        | oracle       | draw, missing  | o3    |
 */
class OtagBulkTest {
    @TempDir lateinit var temp: Path

    private val lines =
        listOf(
            tag("t1", "card-advantage"),
            tag("t2", "draw", parents = listOf("t1"), cards = listOf("o2")),
            tag("t3", "cantrip", parents = listOf("t2"), cards = listOf("o1")),
            tag("t4", "bear-art", type = "illustration", cards = listOf("o1")),
            tag("t5", "under-art", parents = listOf("t4"), cards = listOf("o3")),
            tag("t6", "cycle-x", parents = listOf("t2", "t-missing"), cards = listOf("o3")),
        )

    private val bulk = OtagBulk.parse(lines.asSequence())

    @Test
    fun `a card's tags include every ancestor through parent_ids, sorted`() {
        assertEquals(listOf("cantrip", "card-advantage", "draw"), bulk.otags("o1"))
        assertEquals(listOf("card-advantage", "draw"), bulk.otags("o2"))
    }

    @Test
    fun `tags that are not oracle tags are left out, as tagged and as parents`() {
        assertEquals(listOf("card-advantage", "cycle-x", "draw", "under-art"), bulk.otags("o3"))
    }

    @Test
    fun `a card no tag tags has none`() {
        assertEquals(emptyList(), bulk.otags("o9"))
    }

    @Test
    fun `every slug of the file is known, of any type`() {
        assertEquals(
            setOf("card-advantage", "draw", "cantrip", "bear-art", "under-art", "cycle-x"),
            bulk.slugs,
        )
    }

    @Test
    fun `read takes plain and gzipped files, and skips blank lines`() {
        val text = lines.joinToString("\n", postfix = "\n\n")
        val plain = temp.resolve("tags.jsonl")
        Files.writeString(plain, text)
        val gz = temp.resolve("tags.jsonl.gz")
        GZIPOutputStream(Files.newOutputStream(gz)).use { it.write(text.toByteArray()) }

        assertEquals(listOf("cantrip", "card-advantage", "draw"), OtagBulk.read(plain).otags("o1"))
        assertEquals(listOf("cantrip", "card-advantage", "draw"), OtagBulk.read(gz).otags("o1"))
    }

    @Test
    fun `a line that is not a tag is an error naming its line`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                OtagBulk.parse(sequenceOf(lines[0], "{\"id\": 1}"))
            }
        assertEquals(true, error.message!!.startsWith("line 2:"), error.message)
    }

    companion object {
        /** One bulk-file line. */
        fun tag(
            id: String,
            slug: String,
            type: String = "oracle",
            parents: List<String> = emptyList(),
            cards: List<String> = emptyList(),
        ): String {
            val parentIds = parents.joinToString(",") { "\"$it\"" }
            val taggings =
                cards.joinToString(",") { "{\"oracle_id\":\"$it\",\"weight\":\"median\"}" }
            return "{\"object\":\"tag\",\"id\":\"$id\",\"label\":\"$slug\",\"slug\":\"$slug\"," +
                "\"type\":\"$type\",\"description\":null,\"parent_ids\":[$parentIds]," +
                "\"child_ids\":[],\"aliases\":[],\"taggings\":[$taggings]}"
        }
    }
}
