package spa.agent.otags

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

/** SPA-054 criteria 1, 2 and 5: the cares table, on hand-built tables. */
class CaresTableTest {
    private val text =
        """
        {
          "comment": "hand-built",
          "cares": {
            "legends-matter": [{ "fact": "legendary", "strength": 3 }],
            "historic-matters": [
              { "fact": "legendary", "strength": 2 },
              { "fact": "artifact", "strength": 2 }
            ],
            "draw-matters": [{ "fact": "otag:draw", "strength": 1 }]
          }
        }
        """

    private val table = CaresTable.parse(text)

    @Test
    fun `a card gets cares tags from the table for each of its otags`() {
        assertEquals(mapOf("otag:draw" to 1), table.of(listOf("draw-matters", "flying")))
        assertEquals(emptyMap(), table.of(listOf("flying")))
    }

    @Test
    fun `two tags giving the same fact keep the largest strength`() {
        assertEquals(
            mapOf("legendary" to 3, "artifact" to 2),
            table.of(listOf("historic-matters", "legends-matter")),
        )
        assertEquals(
            mapOf("legendary" to 3, "artifact" to 2),
            table.of(listOf("legends-matter", "historic-matters")),
        )
    }

    @Test
    fun `pool gives each card's cares tags by id`() {
        assertEquals(
            mapOf("x/1" to mapOf("legendary" to 3), "x/2" to emptyMap()),
            table.pool(mapOf("x/1" to listOf("legends-matter"), "x/2" to listOf("burn"))),
        )
    }

    @Test
    fun `a new tag is a new entry, with no code change`() {
        val added =
            CaresTable.parse(
                """{ "cares": { "test-only-matters": [{ "fact": "cheap", "strength": 2 }] } }"""
            )

        assertEquals(mapOf("cheap" to 2), added.of(listOf("test-only-matters")))
    }

    @Test
    fun `every fact that is not a code fact or a tag of the bulk file is named, one line each`() {
        val odd =
            CaresTable.parse(
                """
                { "cares": {
                  "a-matters": [{ "fact": "legendary", "strength": 1 }, { "fact": "nonsense", "strength": 1 }],
                  "b-matters": [{ "fact": "otag:draw", "strength": 1 }, { "fact": "otag:missing", "strength": 2 }],
                  "c-matters": [{ "fact": "kw:station", "strength": 1 }, { "fact": "type:human", "strength": 1 }],
                  "d-matters": [{ "fact": "kw:", "strength": 1 }]
                } }
                """
            )

        assertEquals(
            listOf(
                "tag 'a-matters': unknown fact 'nonsense'",
                "tag 'b-matters': unknown fact 'otag:missing'",
                "tag 'd-matters': unknown fact 'kw:'",
            ),
            odd.unknown(setOf("draw")),
        )
        assertEquals(emptyList(), table.unknown(setOf("draw")))
        assertEquals(
            listOf("tag 'draw-matters': unknown fact 'otag:draw'"),
            table.unknown(emptySet()),
        )
    }

    @Test
    fun `a table that is not a cares table is an error`() {
        for (bad in
            listOf(
                "not json",
                "[]",
                """{ "comment": "no cares" }""",
                """{ "cares": [] }""",
                """{ "cares": { "x": { "fact": "land" } } }""",
                """{ "cares": { "x": [{ "fact": "land" }] } }""",
                """{ "cares": { "x": [{ "fact": "land", "strength": 0 }] } }""",
                """{ "cares": { "x": [{ "fact": "land", "strength": 4 }] } }""",
                """{ "cares": { "x": [{ "fact": "land", "strength": "2" }] } }""",
            )) {
            assertFailsWith<IllegalArgumentException>(bad) { CaresTable.parse(bad) }
        }
    }

    @Test
    fun `the empty table gives no cares tags`() {
        assertEquals(emptyMap(), CaresTable.EMPTY.of(listOf("legends-matter")))
    }
}
