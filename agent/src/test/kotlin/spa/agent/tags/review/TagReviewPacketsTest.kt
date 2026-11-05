package spa.agent.tags.review

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.grader.GraderTestCards.brief
import spa.agent.grader.GraderTestCards.numbered
import spa.agent.grader.GradingPacket
import spa.agent.grader.GradingPackets
import spa.agent.tags.Axis
import spa.agent.tags.FactSupply
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.agent.tags.TagPacketSet
import spa.agent.tags.TagPackets
import spa.agent.tags.TagPartner
import spa.agent.tags.TagRole

/**
 * SPA-052 criterion 1 on a hand-made set of 60 cards (two card files): the review packet's files,
 * its tags files, instructions and record of the tag file, and how it is written.
 */
class TagReviewPacketsTest {
    @TempDir lateinit var temp: Path

    private val index = numbered(60)

    private val facts = (1..60).associate { "xyz/$it" to setOf("creature") }

    private val set = TagPacketSet(index, brief(), null, facts)

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
                val roles = if (it % 7 == 0) listOf(TagRole("tokens", "enabler", 2)) else listOf()
                val partners = if (it == 51) listOf(TagPartner("xyz/1", 3)) else listOf()
                TagCard("xyz/$it", "Card $it", roles, partners)
            },
        )

    private val source = ReviewSource("/somewhere/xyz.json", TagReviewPackets.sha256(byteArrayOf()))

    private fun packet(file: TagFile = tags) =
        TagReviewPackets.build(set, "Review the tags.\n", file, source)

    private fun text(packet: GradingPacket, name: String) =
        packet.files.single { it.name == name }.text

    @Test
    fun `the packet holds the ticket's files, the card files as the grading packet writes them`() {
        val packet = packet()

        assertEquals(
            listOf(
                "instructions.md",
                "review.schema.json",
                "fact-suggestions.schema.json",
                "tag-file.json",
                "facts.json",
                "cards-01.json",
                "cards-02.json",
                "tags-01.json",
                "tags-02.json",
            ),
            packet.files.map { it.name },
        )
        val grading =
            GradingPackets.build(index, brief()).files.filter { GradingPackets.isCardFile(it.name) }
        assertEquals(grading, packet.files.filter { GradingPackets.isCardFile(it.name) })
        assertEquals(
            TagPackets.factsFile(facts).single().text,
            text(packet, "facts.json"),
        )
    }

    @Test
    fun `each tags file holds its card file's entries, in order`() {
        val packet = packet()
        val serializer = ListSerializer(TagCard.serializer())

        assertEquals(
            tags.cards.take(50),
            Json.decodeFromString(serializer, text(packet, "tags-01.json")),
        )
        assertEquals(
            tags.cards.drop(50),
            Json.decodeFromString(serializer, text(packet, "tags-02.json")),
        )
        assertTrue(text(packet, "tags-02.json").endsWith("]\n"))
    }

    @Test
    fun `the instructions are the prompt, the axes with supply, the brief and the files`() {
        val text = text(packet(), "instructions.md")

        assertTrue(
            text.startsWith(
                "Review the tags.\n\n## The set's synergy axes\n\nUse these axes only, each by its " +
                    "id.\n\n- `fliers`, Fliers: Flying creatures. Supplied by: `creature` 1.\n"
            ),
            text,
        )
        assertTrue("- `tokens`, Tokens: Makes a token. Supplied by: no fact.\n- `legends`" in text)
        assertTrue("Legendary cards. Supplied by: no fact.\n\n## The format: Test Set" in text)
        assertTrue(
            text.endsWith(
                "- You write `cards-01.review.json` to `cards-02.review.json`, one beside each " +
                    "card file, and `fact-suggestions.json` when you have a suggestion.\n"
            ),
            text,
        )
        assertTrue("- `tags-01.json` to `tags-02.json`: the current tags" in text)
        assertTrue("- `tag-file.json`: the tag file this packet was made from" in text)
    }

    @Test
    fun `tag-file json records the source, and the same inputs give the same bytes`() {
        assertEquals(
            "{\n  \"path\": \"/somewhere/xyz.json\",\n  \"sha256\": " +
                "\"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\"\n}\n",
            text(packet(), "tag-file.json"),
        )
        assertEquals(packet(), packet())
    }

    @Test
    fun `a tag file missing a pool card, or holding another card, cannot be reviewed`() {
        val missing =
            assertFailsWith<ReviewPacketException> { packet(tags.copy(cards = tags.cards.drop(1))) }
        val extra =
            assertFailsWith<ReviewPacketException> {
                packet(
                    tags.copy(cards = tags.cards + TagCard("xyz/61", "Card 61", listOf(), listOf()))
                )
            }

        assertEquals("the tag file /somewhere/xyz.json has no entry for xyz/1", missing.message)
        assertEquals(
            "the tag file /somewhere/xyz.json has an entry for xyz/61, which is not a card of the pool",
            extra.message,
        )
    }

    @Test
    fun `the file names and the hash`() {
        assertEquals("tags-07.json", TagReviewPackets.tagsFileName("cards-07.json"))
        assertEquals("cards-07.review.json", TagReviewPackets.reviewFileName("cards-07.json"))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            TagReviewPackets.sha256("abc".toByteArray()),
        )
    }

    @Test
    fun `writing replaces earlier packet files and keeps the review files`() {
        val dir = temp.resolve("packet")
        Files.createDirectories(dir)
        listOf(
                "cards-03.json",
                "tags-03.json",
                "tag-file.json",
                "cards-01.review.json",
                "fact-suggestions.json",
                "notes.txt",
            )
            .forEach { Files.writeString(dir.resolve(it), "old") }

        TagReviewPackets.write(packet(), dir)

        val names = dir.listDirectoryEntries().map { it.name }.toSet()
        assertFalse(dir.resolve("cards-03.json").exists())
        assertFalse(dir.resolve("tags-03.json").exists())
        assertTrue(
            setOf("cards-01.review.json", "fact-suggestions.json", "notes.txt").all { it in names }
        )
        assertEquals("old", dir.resolve("cards-01.review.json").readText())
        assertEquals(text(packet(), "tag-file.json"), dir.resolve("tag-file.json").readText())
        assertTrue(names.containsAll(packet().files.map { it.name }))
    }
}
