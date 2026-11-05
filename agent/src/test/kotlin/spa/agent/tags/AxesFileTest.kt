package spa.agent.tags

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/** Reading and checking an axes file, one problem line each, in entry, repeat, count order. */
class AxesFileTest {
    private fun axis(id: String) = """{ "id": "$id", "name": "N $id", "definition": "D $id." }"""

    private fun axes(vararg entries: String) = entries.joinToString(",", "[", "]")

    private fun problems(text: String): List<String> {
        val result = AxesFile.parse(text, "a.json")
        assertNull(result.axes)
        return result.problems
    }

    @Test
    fun `a valid file gives its axes in order`() {
        val result = AxesFile.parse(axes(axis("b"), axis("a-2"), axis("c")), "a.json")

        assertEquals(listOf("b", "a-2", "c"), result.axes?.map { it.id })
        assertEquals(Axis("a-2", "N a-2", "D a-2."), result.axes?.get(1))
        assertEquals(emptyList(), result.problems)
    }

    @Test
    fun `fifteen axes are allowed`() {
        val text = axes(*(1..15).map { axis("x$it") }.toTypedArray())

        assertEquals(15, AxesFile.parse(text, "a.json").axes?.size)
    }

    @Test
    fun `text that is not JSON or not an array`() {
        assertEquals(1, problems("[").size)
        assertEquals(true, problems("[").single().startsWith("a.json: not JSON: "))
        assertEquals(listOf("a.json: not a JSON array of {id, name, definition}"), problems("3"))
    }

    @Test
    fun `each malformed entry is named`() {
        val text =
            axes(
                "7",
                """{ "name": "N", "definition": "D" }""",
                """{ "id": "ok", "definition": "D" }""",
                """{ "id": "ok2", "name": " ", "definition": "D" }""",
                """{ "id": "ok3", "name": "N", "definition": "D", "extra": 1 }""",
            )

        assertEquals(
            listOf(
                "a.json: entry 1: not an object",
                "a.json: entry 2: id must be a string",
                "a.json: entry 3: name must be a non-empty string",
                "a.json: entry 4: name must be a non-empty string",
                "a.json: entry 5: unexpected field 'extra'",
            ),
            problems(text),
        )
    }

    @Test
    fun `repeats and the count`() {
        assertEquals(
            listOf("a.json: id 'a' is given 2 times", "a.json: 2 axes; give 3 to 15"),
            problems(axes(axis("a"), axis("a"))),
        )
        assertEquals(listOf("a.json: 0 axes; give 3 to 15"), problems("[]"))
    }
}
