package spa.agent.otags.cares

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.grader.GraderTestCards.brief
import spa.agent.grader.GraderTestCards.numbered
import spa.agent.otags.CaresTable
import spa.agent.otags.OtagTagInfo
import spa.agent.otags.OtagTagsFile
import spa.agent.synergy.CaresTerm
import spa.agent.tags.TagPacketSet

/**
 * SPA-055 criteria 3 and 5 on the library: [CaresImport] over a packet written for a hand-made set
 * of 60 cards, `xyz/2` caring about `otag:draw` from Scryfall's tag, with each check's case.
 */
class CaresImportTest {
    @TempDir lateinit var temp: Path

    private lateinit var dir: Path

    private val ids = (1..60).map { "xyz/$it" }

    private val set = CaresImportSet("xyz", ids, setOf("legends-matter"))

    @BeforeEach
    fun packet() {
        dir = temp.resolve("packet")
        val facts =
            ids.associateWith { setOf("creature") } +
                mapOf("xyz/1" to setOf("creature", "otag:draw"), "xyz/2" to setOf("creature"))
        val tags =
            OtagTagsFile(
                "xyz",
                "hand-built",
                "2026-11-05T22:00:00Z",
                "a",
                listOf(
                    OtagTagInfo("cantrip", null, emptyList()),
                    OtagTagInfo("draw", "Draws.", emptyList()),
                    OtagTagInfo("legends-matter", "Legends.", emptyList()),
                ),
            )
        val table =
            CaresTable.parse(
                """{ "cares": { "legends-matter": [{ "fact": "otag:draw", "strength": 2 }] } }"""
            )
        val packet =
            CaresPacketSet(
                TagPacketSet(numbered(60), brief(), null, facts),
                mapOf("xyz/2" to listOf("legends-matter")),
                table,
                tags,
            )
        CaresPackets.write(CaresPackets.build(packet, "Prompt."), dir)
        respond(
            1,
            """[ { "id": "xyz/3", "add": [{ "fact": "creature", "strength": 2, "reason": "r" }] } ]""",
        )
        respond(2, "[]")
    }

    private fun respond(n: Int, text: String) =
        Files.writeString(dir.resolve("cards-%02d.cares.json".format(n)), text)

    private fun run(checks: List<CaresCheck> = CaresChecks.ALL) =
        CaresImport.run(dir, set, "provenance", checks)

    private fun problems(): List<String> {
        val result = run()
        assertNull(result.file)
        assertEquals(0, result.additions)
        return result.problems
    }

    @Test
    fun `valid responses give the Fable file, in collector order, with the suggestions`() {
        respond(
            2,
            """[ { "id": "xyz/51", "add": [{ "fact": "otag:cantrip", "strength": 3, "reason": "r2" }] } ]""",
        )
        respond(
            1,
            """
            [ { "id": "xyz/3", "add": [{ "fact": "creature", "strength": 2, "reason": "r" }] },
              { "id": "xyz/2", "add": [{ "fact": "creature", "strength": 1, "reason": "r3" }] },
              { "id": "xyz/4", "add": [] } ]
            """,
        )
        Files.writeString(
            dir.resolve("cares-table-suggestions.json"),
            """[ { "tag": "cantrip", "facts": [{ "fact": "creature", "strength": 1 }], "reason": "s" } ]""",
        )
        val result = run()

        val file = assertNotNull(result.file)
        assertEquals(emptyList(), result.problems)
        assertEquals(3, result.additions)
        assertEquals(listOf("xyz/2", "xyz/3", "xyz/51"), file.cards.map { it.id })
        assertEquals("provenance", file.provenance)
        assertEquals(
            mapOf("xyz/51" to mapOf("otag:cantrip" to 3)),
            file.byId().filterKeys { it == "xyz/51" },
        )
        assertEquals(listOf("cantrip"), result.suggestions.map { it.tag })
        assertEquals(file, FableCaresFile.parse(file.encode()))
    }

    @Test
    fun `each check rejects its case, one line each`() {
        fun case(text: String, vararg contains: String) {
            respond(1, text)
            val lines = problems()
            assertEquals(1, lines.size, "$lines")
            contains.forEach { assertTrue(it in lines.single(), "'$it' not in ${lines.single()}") }
        }
        val add = { fact: String, strength: String, reason: String ->
            """[ { "id": "xyz/3", "add": [{ "fact": "$fact", "strength": $strength, "reason": "$reason" }] } ]"""
        }

        case(add("nonsense", "2", "r"), "xyz/3", "'nonsense'", "vocabulary.json")
        case(add("creature", "0", "r"), "strength 0")
        case(add("creature", "4", "r"), "strength 4")
        case(add("creature", "1.5", "r"), "strength 1.5")
        case(add("otag:draw", "2", ""), "empty reason")
        case(
            """[ { "id": "xyz/2", "add": [{ "fact": "otag:draw", "strength": 2, "reason": "r" }] } ]""",
            "already cares",
        )
        case(
            """[ { "id": "xyz/3", "add": [
                 { "fact": "creature", "strength": 2, "reason": "r" },
                 { "fact": "creature", "strength": 3, "reason": "r" }] } ]""",
            "twice",
        )
        case("""[ { "id": "xyz/99", "add": [] } ]""", "xyz/99", "not a card of cards-01.json")
        case("""[ { "id": "xyz/55", "add": [] } ]""", "xyz/55", "not a card of cards-01.json")
    }

    @Test
    fun `a missing response file is named`() {
        Files.delete(dir.resolve("cards-02.cares.json"))

        assertEquals(1, problems().size)
        assertTrue("cards-02.cares.json: missing" in problems().single())
    }

    @Test
    fun `responses that are not JSON, not an array or not in shape are named`() {
        respond(1, "{ nope")
        assertTrue("not JSON" in problems().single())
        respond(1, """{ "id": "xyz/3" }""")
        assertTrue("not a JSON array" in problems().single())
        respond(
            1,
            """[ 7, { "id": "xyz/3" }, { "id": "xyz/3", "add": [ { "fact": "creature" } ] } ]""",
        )
        assertEquals(3, problems().size)
    }

    @Test
    fun `table suggestions are checked`() {
        val file = dir.resolve("cares-table-suggestions.json")
        fun suggest(tag: String, fact: String) =
            Files.writeString(
                file,
                """[ { "tag": "$tag", "facts": [{ "fact": "$fact", "strength": 1 }], "reason": "r" } ]""",
            )

        suggest("legends-matter", "creature")
        assertTrue("already in cares.json" in problems().single())
        suggest("name-matters", "creature")
        assertTrue("not a Scryfall tag of this set" in problems().single())
        suggest("cantrip", "nonsense")
        assertTrue("fact 'nonsense' is not in vocabulary.json" in problems().single())
        Files.writeString(file, "{ nope")
        assertTrue("not JSON" in problems().single())
        Files.writeString(file, """{ "tag": "cantrip" }""")
        assertTrue("not a JSON array" in problems().single())
        Files.writeString(
            file,
            """[ { "tag": "cantrip", "facts": [ { "fact": "creature" } ], "reason": "r" }, 5 ]""",
        )
        assertEquals(2, problems().size)
    }

    @Test
    fun `an incomplete packet is named, and no check runs`() {
        val ran = mutableListOf<String>()
        val spy = CaresCheck {
            ran += "ran"
            emptyList()
        }

        Files.delete(dir.resolve("vocabulary.json"))
        assertTrue("vocabulary.json: missing" in run(listOf(spy)).problems.single())
        Files.writeString(dir.resolve("vocabulary.json"), "{ nope")
        assertTrue("not a vocabulary file" in run(listOf(spy)).problems.single())
        Files.delete(dir.resolve("cares-02.json"))
        Files.writeString(dir.resolve("cares-01.json"), "nope")
        assertEquals(3, run(listOf(spy)).problems.size)
        Files.delete(dir.resolve("cards-02.json"))
        Files.writeString(dir.resolve("cards-01.json"), """[ { "name": "no id" } ]""")
        val bad = run(listOf(spy)).problems
        assertTrue(bad.any { "cards-01.json: not a card file" in it }, "$bad")
        assertEquals(emptyList(), ran)
    }

    @Test
    fun `card files that are not the pool, or none, are named`() {
        val other = CaresImportSet("xyz", ids.drop(1), emptySet())
        val mismatch = CaresImport.run(dir, other, "p")
        assertTrue(
            mismatch.problems.any { "do not hold the sealed pool of xyz" in it },
            "${mismatch.problems}",
        )

        val empty = temp.resolve("empty")
        Files.createDirectories(empty)
        assertTrue("no cards-NN.json files" in CaresImport.run(empty, set, "p").problems.first())
    }

    @Test
    fun `a check is one object in the list`() {
        val only = CaresCheck { listOf("always") }

        assertEquals(listOf("always"), run(listOf(only)).problems)
        assertEquals(8, CaresChecks.ALL.size)
    }

    @Test
    fun `the Fable file refuses a strength out of range, and the term merges cares`() {
        assertFailsWith<IllegalArgumentException> {
            FableCaresFile.parse(
                """{ "set": "xyz", "provenance": "p", "cards": [ { "id": "xyz/1",
                   "cares": [ { "fact": "land", "strength": 4, "reason": "r" } ] } ] }"""
            )
        }
        assertFailsWith<IllegalArgumentException> { FableCaresFile.parse("{ nope") }

        val scryfall =
            mapOf("xyz/1" to mapOf("land" to 2, "artifact" to 1), "xyz/2" to mapOf("land" to 1))
        val fable =
            mapOf("xyz/1" to mapOf("land" to 1, "artifact" to 3), "xyz/9" to mapOf("land" to 2))
        assertEquals(
            mapOf(
                "xyz/1" to mapOf("land" to 2, "artifact" to 3),
                "xyz/2" to mapOf("land" to 1),
                "xyz/9" to mapOf("land" to 2),
            ),
            CaresTerm.merged(scryfall, fable),
        )
        assertEquals(scryfall, CaresTerm.merged(scryfall, emptyMap()))
    }
}
