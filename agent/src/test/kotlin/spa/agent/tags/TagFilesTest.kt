package spa.agent.tags

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/** The tag file format: written, validated against `tags.schema.json`, and read back. */
class TagFilesTest {
    private val axes =
        listOf(
            Axis("tokens", "Tokens", "Makes a token."),
            Axis("sacrifice", "Sacrifice", "Sacrifices a permanent."),
            Axis("plus-one-counters", "+1/+1 counters", "Puts a +1/+1 counter on a creature."),
        )

    private val file =
        TagFile(
            set = "syn",
            provenance = "test, tags prompt v1, 2026-11-05",
            axes = axes,
            cards =
                listOf(
                    TagCard(
                        "syn/1",
                        "Grizzly Bears",
                        listOf(TagRole("tokens", "payoff", 3), TagRole("tokens", "enabler", 1)),
                        listOf(TagPartner("syn/1", 2), TagPartner("syn/2", 1)),
                    ),
                    TagCard("syn/2", "Serra Angel", emptyList(), emptyList()),
                ),
        )

    @Test
    fun `a file writes, validates and reads back equal`() {
        val text = TagFiles.write(file)

        assertEquals(emptyList(), TagFiles.validate(Json.parseToJsonElement(text)))
        assertEquals(file, TagFiles.parse(text))
        assertTrue(text.endsWith("}\n"))
    }

    @Test
    fun `the schema rejects a strength of 4, a bad role and a bad axis id`() {
        val bad =
            file.copy(
                axes = axes.take(2) + Axis("Bad Id", "Bad", "Bad."),
                cards =
                    listOf(
                        TagCard(
                            "syn/1",
                            "Grizzly Bears",
                            listOf(TagRole("tokens", "maker", 4)),
                            emptyList(),
                        )
                    ),
            )

        val problems = TagFiles.validate(Json.parseToJsonElement(TagFiles.write(bad)))

        assertTrue(problems.any { it.startsWith("/axes/2/id") }, "$problems")
        assertTrue(problems.any { it.startsWith("/cards/0/roles/0/role") }, "$problems")
        assertTrue(problems.any { it.startsWith("/cards/0/roles/0/strength") }, "$problems")
        assertFailsWith<TagFileException> { TagFiles.parse(TagFiles.write(bad)) }
    }

    @Test
    fun `the schema rejects six partners and fewer than three axes`() {
        val six = (1..6).map { TagPartner("syn/$it", 1) }
        val bad =
            file.copy(
                axes = axes.take(2),
                cards = listOf(TagCard("syn/1", "Grizzly Bears", emptyList(), six)),
            )

        val problems = TagFiles.validate(Json.parseToJsonElement(TagFiles.write(bad)))

        assertTrue(problems.any { it.startsWith("/axes") }, "$problems")
        assertTrue(problems.any { it.startsWith("/cards/0/partners") }, "$problems")
    }

    @Test
    fun `text that is not JSON is one problem`() {
        val e = assertFailsWith<TagFileException> { TagFiles.parse("not json") }

        assertEquals(1, e.problems.size)
    }
}
