package spa.agent.otags

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * SPA-055 criteria 6 and 7: the tags file built from a hand-built bulk file, its text, and the name
 * tags found through `parents`.
 *
 * | Tag            | Type         | Parents                  | Cards |
 * |----------------|--------------|--------------------------|-------|
 * | card-advantage | oracle       | none                     | none  |
 * | draw           | oracle       | card-advantage           | o2    |
 * | bear-art       | illustration | none                     | none  |
 * | bear-theme     | oracle       | bear-art, card-advantage | o1    |
 * | name-matters   | oracle       | none                     | o3    |
 * | names-child    | oracle       | name-matters             | o3    |
 * | names-grand    | oracle       | names-child              | o3    |
 */
class OtagTagsFileTest {
    private fun tag(
        slug: String,
        type: String = "oracle",
        parents: List<String> = emptyList(),
        cards: List<String> = emptyList(),
        description: String? = null,
    ): String {
        val parentIds = parents.joinToString(",") { "\"t-$it\"" }
        val taggings = cards.joinToString(",") { "{\"oracle_id\":\"$it\"}" }
        val text = description?.let { "\"$it\"" } ?: "null"
        return "{\"id\":\"t-$slug\",\"slug\":\"$slug\",\"type\":\"$type\",\"description\":$text," +
            "\"parent_ids\":[$parentIds],\"taggings\":[$taggings]}"
    }

    private val bulk =
        OtagBulk.parse(
            sequenceOf(
                tag("card-advantage", description = "Gains cards."),
                tag("draw", parents = listOf("card-advantage"), cards = listOf("o2")),
                tag("bear-art", type = "illustration", description = "Art."),
                tag(
                    "bear-theme",
                    parents = listOf("bear-art", "card-advantage"),
                    cards = listOf("o1"),
                ),
                tag("name-matters", cards = listOf("o3"), description = "Names \\\"matter\\\"."),
                tag("names-child", parents = listOf("name-matters"), cards = listOf("o3")),
                tag("names-grand", parents = listOf("names-child"), cards = listOf("o3")),
            )
        )

    private val otags =
        OtagFile(
            "xyz",
            "hand-built",
            "2026-11-05T22:00:00Z",
            OtagFile.ATTRIBUTION,
            listOf("o1", "o2", "o3", "o4").mapIndexed { i, o ->
                OtagCard("xyz/${i + 1}", bulk.otags(o))
            },
        )

    private val file = OtagTagsFile.build(otags, bulk)

    @Test
    fun `it holds each tag of the otags file, sorted, with its description and oracle parents`() {
        assertEquals(
            listOf(
                OtagTagInfo("bear-theme", null, listOf("card-advantage")),
                OtagTagInfo("card-advantage", "Gains cards.", emptyList()),
                OtagTagInfo("draw", null, listOf("card-advantage")),
                OtagTagInfo("name-matters", "Names \"matter\".", emptyList()),
                OtagTagInfo("names-child", null, listOf("name-matters")),
                OtagTagInfo("names-grand", null, listOf("names-child")),
            ),
            file.tags,
        )
        assertEquals(2, file.described)
        assertEquals(
            listOf("xyz", "hand-built", "2026-11-05T22:00:00Z"),
            listOf(file.set, file.source, file.updatedAt),
        )
    }

    @Test
    fun `the text is one tag a line, and reads back to the same file`() {
        val text = file.encode()

        assertEquals(
            "    { \"slug\": \"name-matters\", \"description\": \"Names \\\"matter\\\".\", \"parents\": [] },",
            text.lines().single { "\"slug\": \"name-matters\"" in it },
        )
        assertTrue("\"description\": null, \"parents\": [\"card-advantage\"] }," in text)
        assertTrue(text.endsWith("  ]\n}\n"))
        assertEquals(file, OtagTagsFile.parse(text))
        assertEquals(text, OtagTagsFile.parse(text).encode())
    }

    @Test
    fun `anything else is an IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { OtagTagsFile.parse("{ nope") }
    }

    @Test
    fun `the name tags are name-matters and every tag whose parents lead to it`() {
        assertEquals(setOf("name-matters", "names-child", "names-grand"), file.nameTags())
        assertEquals(
            setOf("card-advantage", "draw", "bear-theme"),
            file.descendantsOf("card-advantage"),
        )
    }

    @Test
    fun `a file without name-matters has no name tags`() {
        val without = file.copy(tags = file.tags.filter { it.slug == "draw" })

        assertEquals(emptySet(), without.nameTags())
    }
}
