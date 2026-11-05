package spa.agent.tags.review

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.grader.GraderTestCards.brief
import spa.agent.grader.GraderTestCards.numbered
import spa.agent.tags.Axis
import spa.agent.tags.FactSupply
import spa.agent.tags.TagCard
import spa.agent.tags.TagCorrection
import spa.agent.tags.TagFile
import spa.agent.tags.TagFiles
import spa.agent.tags.TagPacketSet
import spa.agent.tags.TagReview
import spa.agent.tags.TagRole

/**
 * SPA-052 criterion 3 on a hand-made set of 60 cards in two card files: the import across both
 * files, a card named in the other file's review, and each way the packet or tag file stops it.
 */
class TagReviewImportTest {
    @TempDir lateinit var temp: Path

    private val index = numbered(60)

    private val facts = (1..60).associate { "xyz/$it" to setOf("creature") }

    private val tags =
        TagFile(
            "xyz",
            "test, tags prompt v2, 2026-11-05",
            listOf(
                Axis("fliers", "Fliers", "Flying creatures.", listOf(FactSupply("creature", 1))),
                Axis("tokens", "Tokens", "Makes a token.", listOf()),
                Axis("legends", "Legends", "Legendary cards.", listOf()),
            ),
            (1..60).map {
                val roles = if (it == 55) listOf(TagRole("tokens", "enabler", 2)) else listOf()
                TagCard("xyz/$it", "Card $it", roles, listOf())
            },
        )

    private lateinit var dir: Path
    private lateinit var tagPath: Path

    private fun write(file: TagFile) = Files.writeString(tagPath, TagFiles.write(file))

    @BeforeEach
    fun setUp() {
        tagPath = temp.resolve("xyz.json")
        write(tags)
        dir = temp.resolve("packet")
        packet()
    }

    /** Writes the packet for [tags], recording the tag file's bytes as they are now. */
    private fun packet() {
        val source =
            ReviewSource(tagPath.toString(), TagReviewPackets.sha256(Files.readAllBytes(tagPath)))
        val set = TagPacketSet(index, brief(), null, facts)
        TagReviewPackets.write(TagReviewPackets.build(set, "Review.", tags, source), dir)
    }

    private fun review(file: String, text: String) = Files.writeString(dir.resolve(file), text)

    private fun run() = TagReviewImport.run(dir, "xyz", facts, "review test")

    private fun problems() = run().also { assertNull(it.file) }.problems

    @Test
    fun `corrections in both files apply, and the file keeps its set, axes and provenance`() {
        review(
            "cards-01.review.json",
            """[{ "id": "xyz/3", "action": "add-role", "axis": "fliers", "role": "payoff",
                  "strength": 2, "reason": "Rewards fliers." }]""",
        )
        review(
            "cards-02.review.json",
            """[{ "id": "xyz/55", "action": "set-strength", "axis": "tokens", "role": "enabler",
                  "strength": 3, "reason": "Makes two." }]""",
        )

        val result = run()

        assertEquals(emptyList(), result.problems)
        val file = result.file!!
        assertEquals(tags.copy(cards = file.cards, review = file.review), file)
        assertEquals(listOf(TagRole("fliers", "payoff", 2)), file.cards[2].roles)
        assertEquals(listOf(TagRole("tokens", "enabler", 3)), file.cards[54].roles)
        assertEquals(
            TagReview(
                "review test",
                linkedMapOf(
                    "add-role" to 1,
                    "remove-role" to 0,
                    "set-strength" to 1,
                    "add-partner" to 0,
                    "remove-partner" to 0,
                ),
                listOf(
                    TagCorrection(
                        "xyz/3",
                        "add-role",
                        "fliers",
                        "payoff",
                        null,
                        2,
                        "Rewards fliers.",
                    ),
                    TagCorrection(
                        "xyz/55",
                        "set-strength",
                        "tokens",
                        "enabler",
                        null,
                        3,
                        "Makes two.",
                    ),
                ),
            ),
            file.review,
        )
        assertEquals(emptyList(), result.suggestions)
    }

    @Test
    fun `a card of the other card file is outside its file`() {
        review(
            "cards-01.review.json",
            """[{ "id": "xyz/55", "action": "remove-role", "axis": "tokens", "role": "enabler",
                  "reason": "Wrong file." }]""",
        )
        review("cards-02.review.json", "[]")

        assertEquals(
            listOf("cards-01.review.json: correction 1: xyz/55 is not a card in cards-01.json"),
            problems(),
        )
    }

    @Test
    fun `every card file needs its review file`() {
        review("cards-01.review.json", "[]")

        assertEquals(
            listOf(
                "cards-02.review.json: missing; write one for cards-02.json, an empty array when " +
                    "no card needs a correction"
            ),
            problems(),
        )
    }

    @Test
    fun `a second review replaces the first review block`() {
        write(tags.copy(review = TagReview("old", linkedMapOf("add-role" to 0), listOf())))
        packet()
        review("cards-01.review.json", "[]")
        review("cards-02.review.json", "[]")

        assertEquals("review test", run().file!!.review!!.provenance)
    }

    @Test
    fun `a changed, missing, invalid or other set's tag file stops the import with one line`() {
        val again = "run 'spa tags-review-packet xyz' again"
        write(tags.copy(provenance = "changed"))
        assertEquals(
            listOf("the tag file $tagPath changed since the packet was built; $again"),
            problems(),
        )

        Files.delete(tagPath)
        assertEquals(
            listOf("the tag file $tagPath the packet was built from is missing"),
            problems(),
        )

        Files.writeString(tagPath, "{}")
        packet()
        val invalid = problems()
        assertTrue(
            invalid.isNotEmpty() && invalid.all { it.startsWith("tag file $tagPath: ") },
            "$invalid",
        )

        write(tags.copy(set = "abc"))
        packet()
        assertEquals(listOf("the tag file $tagPath is for set 'abc', not 'xyz'"), problems())
    }

    @Test
    fun `a packet without its record or card files, or with a broken card file, is one line`() {
        val again = "run 'spa tags-review-packet xyz' to write the packet"
        Files.writeString(dir.resolve("cards-02.json"), "{")
        assertEquals(listOf("cards-02.json: not a card file; $again"), problems())

        Files.writeString(dir.resolve("tag-file.json"), "[]")
        assertEquals(listOf("tag-file.json: not a tag file record; $again"), problems())

        Files.delete(dir.resolve("tag-file.json"))
        assertEquals(listOf("tag-file.json: missing; $again"), problems())

        val empty = temp.resolve("empty")
        Files.createDirectories(empty)
        packet()
        Files.copy(dir.resolve("tag-file.json"), empty.resolve("tag-file.json"))
        assertEquals(
            listOf("$empty: no cards-NN.json files; $again"),
            TagReviewImport.run(empty, "xyz", facts, "p").problems,
        )
    }

    @Test
    fun `a result the checks miss still has to pass the tag schema`() {
        review(
            "cards-01.review.json",
            """[{ "id": "xyz/3", "action": "add-role", "axis": "fliers", "role": "payoff",
                  "strength": 4, "reason": "Too strong." }]""",
        )
        review("cards-02.review.json", "[]")

        val result = TagReviewImport.run(dir, "xyz", facts, "p", checks = emptyList())

        assertNull(result.file)
        assertEquals(
            listOf(
                "tag file: /cards/2/roles/0/strength: element is not in enum",
                "tag file: /review/corrections/0/strength: element is not in enum",
            ),
            result.problems,
        )
    }

    @Test
    fun `fact suggestions come back with the file`() {
        review("cards-01.review.json", "[]")
        review("cards-02.review.json", "[]")
        review(
            "fact-suggestions.json",
            """[{ "fact": "flies", "rule": "Has flying.", "examples": ["xyz/1"], "reason": "r" }]""",
        )

        assertEquals(
            listOf(FactSuggestion("flies", "Has flying.", listOf("xyz/1"), "r")),
            run().suggestions,
        )
    }
}
