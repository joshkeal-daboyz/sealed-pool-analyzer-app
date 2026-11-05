package spa.agent.tags

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/** SPA-051 criterion 3: the v2 (supplied) axes file, and the three supply checks. */
class AxesFileV2Test {
    private fun axis(id: String, supply: String = "[]") =
        """{ "id": "$id", "name": "N $id", "definition": "D $id.", "suppliedBy": $supply }"""

    private fun plain(id: String) = """{ "id": "$id", "name": "N $id", "definition": "D $id." }"""

    private fun axes(vararg entries: String) = entries.joinToString(",", "[", "]")

    private fun many(n: Int, entry: (String) -> String) =
        axes(*(1..n).map { entry("x$it") }.toTypedArray())

    private fun supplied(text: String) = AxesFile.parse(text, "a.json", AxesFormat.SUPPLIED)

    private fun problems(text: String): List<String> {
        val result = supplied(text)
        assertNull(result.axes)
        return result.problems
    }

    @Test
    fun `a v2 file gives its axes with their supply`() {
        val text =
            axes(
                axis("landfall", """[{ "fact": "land", "strength": 3 }]"""),
                axis("tokens"),
                axis(
                    "deaths",
                    """[{ "fact": "creature", "strength": 1 }, { "fact": "kw:deathtouch", "strength": 2 }]""",
                ),
            )

        val result = supplied(text)

        assertEquals(emptyList(), result.problems)
        assertEquals(
            Axis("landfall", "N landfall", "D landfall.", listOf(FactSupply("land", 3))),
            result.axes?.first(),
        )
        assertEquals(emptyList(), result.axes?.get(1)?.suppliedBy)
        assertEquals(
            listOf(FactSupply("creature", 1), FactSupply("kw:deathtouch", 2)),
            result.axes?.get(2)?.suppliedBy,
        )
    }

    @Test
    fun `16 to 20 axes are accepted under v2 and rejected under v1`() {
        (16..20).forEach { n ->
            assertEquals(n, supplied(many(n) { axis(it) }).axes?.size, "v2 $n")
            val v1 = AxesFile.parse(many(n) { plain(it) }, "a.json")
            assertEquals(listOf("a.json: $n axes; give 3 to 15"), v1.problems, "v1 $n")
            val v1Format = AxesFile.parse(many(n) { plain(it) }, "a.json", AxesFormat.PLAIN)
            assertEquals(listOf("a.json: $n axes; give 3 to 15"), v1Format.problems, "v1 $n")
        }
        assertEquals(listOf("a.json: 21 axes; give 3 to 20"), problems(many(21) { axis(it) }))
        assertEquals(listOf("a.json: 2 axes; give 3 to 20"), problems(many(2) { axis(it) }))
    }

    @Test
    fun `v2 needs suppliedBy on every axis, and v1 allows it on none`() {
        assertEquals(
            listOf("a.json: entry 2: suppliedBy must be an array"),
            problems(axes(axis("a"), plain("b"), axis("c"))),
        )
        assertEquals(
            listOf("a.json: entry 1: unexpected field 'suppliedBy'"),
            AxesFile.parse(axes(axis("a"), plain("b"), plain("c")), "a.json").problems,
        )
    }

    @Test
    fun `each malformed suppliedBy entry is named`() {
        val text =
            axes(
                axis("a", "[7]"),
                axis("b", """[{ "strength": 1 }]"""),
                axis("c", """[{ "fact": "land", "strength": "2" }]"""),
                axis("d", """[{ "fact": "land", "strength": 1.5 }]"""),
                axis("e", """[{ "fact": "land", "strength": 1, "why": "x" }]"""),
                axis("f", "{}"),
            )

        assertEquals(
            listOf(
                "a.json: entry 1: a suppliedBy entry is not an object",
                "a.json: entry 2: a suppliedBy entry's fact must be a string",
                "a.json: entry 3: a suppliedBy entry's strength must be a whole number",
                "a.json: entry 4: a suppliedBy entry's strength must be a whole number",
                "a.json: entry 5: a suppliedBy entry has unexpected field 'why'",
                "a.json: entry 6: suppliedBy must be an array",
            ),
            problems(text),
        )
    }

    @Test
    fun `the format of axes json is read from the file itself`() {
        val withSupply = axes(axis("a"), axis("b"), axis("c"))
        val without = axes(plain("a"), plain("b"), plain("c"))

        assertEquals(
            listOf(emptyList(), emptyList(), emptyList()),
            AxesFile.parseDetected(withSupply, "a.json").axes?.map { it.suppliedBy },
        )
        assertEquals(
            listOf(null, null, null),
            AxesFile.parseDetected(without, "a.json").axes?.map { it.suppliedBy },
        )
        assertEquals(
            listOf("a.json: 16 axes; give 3 to 15"),
            AxesFile.parseDetected(many(16) { plain(it) }, "a.json").problems,
        )
        assertEquals(20, AxesFile.parseDetected(many(20) { axis(it) }, "a.json").axes?.size)
    }

    private fun check(axes: List<Axis>, facts: Set<String> = setOf("land", "creature")) =
        TagChecks.ALL.flatMap {
            it.problems(TagCheckInput(emptyList(), axes, emptyList(), facts, "axes.json"))
        }

    private fun supply(id: String, vararg supply: Pair<String, Int>) =
        Axis(id, id, "D.", supply.map { FactSupply(it.first, it.second) })

    @Test
    fun `a known fact at strength 1 to 3, once each, passes every check`() {
        assertEquals(
            emptyList(),
            check(
                listOf(
                    supply("a", "land" to 1, "creature" to 3),
                    supply("b"),
                    supply("c", "land" to 2),
                )
            ),
        )
        assertEquals(emptyList(), check(listOf(Axis("a", "a", "D."))))
    }

    @Test
    fun `an unknown fact, strength 0 or 4, and a repeated fact are one line each`() {
        val axes =
            listOf(
                supply("a", "kw:flying" to 1),
                supply("b", "land" to 0, "creature" to 4),
                supply("c", "land" to 2, "land" to 3),
            )

        assertEquals(
            listOf(
                "axes.json: axis 'a' is supplied by 'kw:flying', which no card of the set has",
                "axes.json: axis 'b' strength 0 for 'land' is not 1, 2 or 3",
                "axes.json: axis 'b' strength 4 for 'creature' is not 1, 2 or 3",
                "axes.json: axis 'c' gives 'land' 2 times",
            ),
            check(axes),
        )
    }

    @Test
    fun `the supply checks are three classes at the end of the import list`() {
        assertEquals(
            listOf(SupplyFactCheck, SupplyStrengthCheck, RepeatedSupplyCheck),
            TagChecks.SUPPLY,
        )
        assertEquals(TagChecks.SUPPLY, TagChecks.ALL.takeLast(3))
        assertEquals(11, TagChecks.ALL.size)
    }
}
