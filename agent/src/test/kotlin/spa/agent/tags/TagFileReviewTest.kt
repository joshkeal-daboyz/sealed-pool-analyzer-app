package spa.agent.tags

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * SPA-052 criterion 4: a v2 tag file with 20 axes, and one with a `review` block, validate against
 * `tags.schema.json` and read back equal; a file with no review writes no `review` key.
 */
class TagFileReviewTest {
    private val twenty =
        (1..20).map {
            Axis(
                "axis-$it",
                "Axis $it",
                "Supplies axis $it.",
                listOf(FactSupply("creature", 1 + it % 3)),
            )
        }

    private val cards =
        listOf(
            TagCard("syn/1", "Grizzly Bears", listOf(TagRole("axis-1", "enabler", 2)), listOf()),
            TagCard("syn/2", "Serra Angel", listOf(), listOf(TagPartner("syn/1", 1))),
        )

    private val v2 = TagFile("syn", "test, tags prompt v2, 2026-11-05", twenty, cards)

    private val review =
        TagReview(
            provenance = "test, tags review prompt v1, 2026-11-05",
            counts =
                linkedMapOf(
                    "add-role" to 1,
                    "remove-role" to 0,
                    "set-strength" to 0,
                    "add-partner" to 1,
                    "remove-partner" to 0,
                ),
            corrections =
                listOf(
                    TagCorrection("syn/1", "add-role", "axis-1", "enabler", null, 2, "A bear."),
                    TagCorrection(
                        "syn/2",
                        "add-partner",
                        partner = "syn/1",
                        strength = 1,
                        reason = "x",
                    ),
                ),
        )

    private fun roundTrip(file: TagFile) {
        val text = TagFiles.write(file)

        assertEquals(emptyList(), TagFiles.validate(Json.parseToJsonElement(text)))
        assertEquals(file, TagFiles.parse(text))
    }

    @Test
    fun `a v2 file with 20 axes validates and reads back equal`() {
        roundTrip(v2)
    }

    @Test
    fun `21 axes do not validate`() {
        val more = v2.copy(axes = twenty + Axis("axis-21", "Axis 21", "One more.", listOf()))

        assertFailsWith<TagFileException> { TagFiles.parse(TagFiles.write(more)) }
    }

    @Test
    fun `a file with a review block validates and reads back equal, the block last`() {
        val reviewed = v2.copy(review = review)
        val text = TagFiles.write(reviewed)

        roundTrip(reviewed)
        assertTrue(text.indexOf("\"review\"") > text.indexOf("\"cards\""), text)
        assertTrue("\"remove-role\": 0" in text, text)
    }

    @Test
    fun `a file with no review writes no review key`() {
        assertFalse("review" in TagFiles.write(v2))
    }

    @Test
    fun `the schema rejects a review without provenance, a bad count, and a correction without reason`() {
        val text = TagFiles.write(v2.copy(review = review))
        val bad =
            listOf(
                text.replace(
                    "\"provenance\": \"test, tags review prompt v1, 2026-11-05\"",
                    "\"provenance\": \" \"",
                ),
                text.replace("\"add-role\": 1", "\"add-role\": -1"),
                text.replace(",\n        \"reason\": \"A bear.\"", ""),
                text.replace("\"action\": \"add-role\"", "\"action\": \"Add Role\""),
                text.replace(
                    "\"strength\": 2,\n        \"reason\"",
                    "\"strength\": 4,\n        \"reason\"",
                ),
            )

        bad.forEach {
            assertTrue(it != text, "the replacement applies")
            assertFailsWith<TagFileException>(it) { TagFiles.parse(it) }
        }
    }
}
