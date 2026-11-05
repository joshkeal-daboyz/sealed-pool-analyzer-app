package spa.agent.tags

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.grader.GraderTestCards.brief
import spa.agent.grader.GraderTestCards.numbered

/**
 * Importing a pass-2 packet for set `xyz`'s five cards, one card file, `cards-01.json` with ids
 * xyz/1 to xyz/5, on the axes `tokens`, `sacrifice` and `lands`.
 */
class TagImportTest {
    @TempDir lateinit var temp: Path

    private val index = numbered(5)
    private val axes =
        listOf(
            Axis("tokens", "Tokens", "Makes a token."),
            Axis("sacrifice", "Sacrifice", "Sacrifices a permanent."),
            Axis("lands", "Lands", "A land."),
        )
    private val file = "cards-01.response.json"

    @BeforeEach
    fun packet() {
        TagPackets.write(TagPackets.rolesPacket(index, brief(), null, "P", axes), temp)
    }

    private fun entry(id: Int, roles: String = "", partners: String = "") =
        """{ "id": "xyz/$id", "roles": [$roles], "partners": [$partners] }"""

    private fun role(axis: String = "tokens", role: String = "enabler", strength: String = "2") =
        """{ "axis": "$axis", "role": "$role", "strength": $strength }"""

    private fun partner(id: Int, strength: String = "1") =
        """{ "id": "xyz/$id", "strength": $strength }"""

    private fun respond(vararg entries: String) {
        Files.writeString(temp.resolve(file), entries.joinToString(",\n", "[\n", "\n]\n"))
    }

    /** Responds with [first] for xyz/1 and plain entries for the other four. */
    private fun respondWith(first: String) =
        respond(first, *(2..5).map { entry(it) }.toTypedArray())

    private fun run(checks: List<TagCheck> = TagChecks.ALL, provenance: String = "test v1") =
        TagImport.run(temp, index, "xyz", provenance, checks)

    private fun assertProblems(vararg problems: String) {
        val result = run()
        assertNull(result.file)
        assertEquals(problems.toList(), result.problems)
    }

    @Test
    fun `valid responses give the tag file in pool order`() {
        respond(
            entry(3),
            entry(1, role() + ", " + role("tokens", "payoff", "3"), partner(1) + ", " + partner(2)),
            entry(2, role("lands", "payoff", "1")),
            entry(5),
            entry(4),
        )

        val result = run()

        assertEquals(emptyList(), result.problems)
        val tags = result.file!!
        assertEquals(TagFile("xyz", "test v1", axes, tags.cards), tags)
        assertEquals((1..5).map { "xyz/$it" }, tags.cards.map { it.id })
        assertEquals(
            TagCard(
                "xyz/1",
                "Card 1",
                listOf(TagRole("tokens", "enabler", 2), TagRole("tokens", "payoff", 3)),
                listOf(TagPartner("xyz/1", 1), TagPartner("xyz/2", 1)),
            ),
            tags.cards.first(),
        )
    }

    @Test
    fun `every check names its problem`() {
        respond(
            entry(1, role("nope") + ", " + role(role = "maker") + ", " + role(strength = "4")),
            entry(1),
            entry(9),
            entry(3, role() + ", " + role(), partner(8, "0")),
            entry(4, partners = (1..5).joinToString(", ") { partner(it) } + ", " + partner(1)),
            entry(5, strengthOnly()),
        )

        assertProblems(
            "$file: xyz/1 is tagged 2 times",
            "$file: xyz/9 is not a card in this packet",
            "$file: xyz/2 is not tagged",
            "$file: xyz/1 axis 'nope' is not one of the packet's axes",
            "$file: xyz/1 role 'maker' on tokens is not enabler or payoff",
            "$file: xyz/1 strength 4 for enabler on tokens is not 1, 2 or 3",
            "$file: xyz/3 partner xyz/8 strength 0 is not 1, 2 or 3",
            "$file: xyz/5 strength 1.0 for enabler on tokens is not 1, 2 or 3",
            "$file: xyz/3 gives enabler on tokens 2 times",
            "$file: xyz/3 partner xyz/8 is not a card in this packet",
            "$file: xyz/4 has 6 partners; give at most 5",
            "$file: xyz/4 names partner xyz/1 2 times",
        )
    }

    private fun strengthOnly() = role(strength = "1.0")

    @Test
    fun `malformed entries are named by position and stop the checks`() {
        respond(
            "1",
            """{ "roles": [], "partners": [] }""",
            """{ "id": "xyz/1", "partners": [] }""",
            """{ "id": "xyz/2", "roles": [] }""",
            """{ "id": "xyz/3", "roles": [], "partners": [], "x": 1 }""",
            """{ "id": "xyz/4", "roles": [1], "partners": [] }""",
            """{ "id": "xyz/4", "roles": [{ "axis": 1, "role": "r", "strength": 1 }], "partners": [] }""",
            """{ "id": "xyz/4", "roles": [{ "axis": "a", "role": "r", "strength": "1" }], "partners": [] }""",
            """{ "id": "xyz/4", "roles": [], "partners": [{ "id": "xyz/1", "strength": 1, "y": 2 }] }""",
        )

        assertProblems(
            "$file: entry 1: not an object",
            "$file: entry 2: id must be a string",
            "$file: entry 3: roles must be an array",
            "$file: entry 4: partners must be an array",
            "$file: entry 5: unexpected field 'x'",
            "$file: entry 6: a role is not an object",
            "$file: entry 7: a role's axis must be a string",
            "$file: entry 8: a role's strength must be a number",
            "$file: entry 9: a partner has unexpected field 'y'",
        )
    }

    @Test
    fun `a response file that is missing, not JSON or not an array`() {
        assertProblems("$file: missing; tag every card in cards-01.json")
        Files.writeString(temp.resolve(file), "{")
        assertEquals(true, run().problems.single().startsWith("$file: not JSON: "))
        Files.writeString(temp.resolve(file), "{}")
        assertProblems("$file: not a JSON array of {id, roles, partners}")
    }

    @Test
    fun `a broken packet is named before any response is checked`() {
        respondWith(entry(1))
        Files.writeString(temp.resolve("axes.json"), "[]")
        Files.writeString(temp.resolve("cards-02.json"), "{}")

        assertProblems(
            "axes.json: 0 axes; give 3 to 15",
            "cards-02.json: not a card file",
            "cards-02.response.json: missing; tag every card in cards-02.json",
        )
    }

    @Test
    fun `card files that do not hold the pool, or no card files`() {
        respondWith(entry(1))
        val again = "run 'spa tags-packet xyz --axes <file>' to write the packet"

        val other = TagImport.run(temp, numbered(6), "xyz", "p", TagChecks.ALL)

        assertEquals(
            listOf("the card files do not hold the sealed pool of xyz; $again"),
            other.problems,
        )
        Files.delete(temp.resolve("cards-01.json"))
        Files.delete(temp.resolve("axes.json"))
        assertProblems("axes.json: missing; $again", "$temp: no cards-NN.json files; $again")
    }

    @Test
    fun `a tag file that breaks the schema is named`() {
        respondWith(entry(1))

        val result = run(provenance = " ")

        assertNull(result.file)
        assertEquals(1, result.problems.size)
        assertEquals(true, result.problems.single().startsWith("tag file: /provenance"))
    }

    @Test
    fun `an extra check in the list runs with the rest`() {
        respondWith(entry(1))
        val extra = TagCheck { input ->
            listOf("${input.cardFiles.size} file, ${input.poolIds.size} cards, ${input.axes.size}")
        }

        assertEquals(listOf("1 file, 5 cards, 3"), run(TagChecks.ALL + extra).problems)
        assertEquals(emptyList(), run().problems)
    }
}
