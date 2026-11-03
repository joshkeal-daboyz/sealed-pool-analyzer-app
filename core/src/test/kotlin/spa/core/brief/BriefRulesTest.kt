package spa.core.brief

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class BriefRulesTest {
    @Test
    fun `there are ten color pairs, each in WUBRG order`() {
        assertEquals(
            setOf("WU", "WB", "WR", "WG", "UB", "UR", "UG", "BR", "BG", "RG"),
            BriefRules.colorPairs,
        )
    }

    @Test
    fun `a pair normalizes to WUBRG order whichever way it is written`() {
        assertEquals("WU", BriefRules.normalizePair("UW"))
        assertEquals("WU", BriefRules.normalizePair("WU"))
        assertEquals("UG", BriefRules.normalizePair("GU"))
        assertEquals("BR", BriefRules.normalizePair("RB"))
    }

    @Test
    fun `a value that is not two distinct WUBRG letters is not a pair`() {
        assertNull(BriefRules.normalizePair("WW"))
        assertNull(BriefRules.normalizePair("W"))
        assertNull(BriefRules.normalizePair("WUB"))
        assertNull(BriefRules.normalizePair("WX"))
        assertNull(BriefRules.normalizePair("wu"))
    }

    @Test
    fun `parts with the wrong shape are left to the schema`() {
        val doc =
            Json.parseToJsonElement(
                """{"archetypes": "none", "kitTemplate": {"cardsPerBooster": 14, "slots": [{"count": "7"}]}}"""
            )
        assertEquals(emptyList(), BriefRules.check(doc))
        assertEquals(emptyList(), BriefRules.check(Json.parseToJsonElement("[]")))
        assertEquals(
            emptyList(),
            BriefRules.check(Json.parseToJsonElement("""{"kitTemplate": {"slots": []}}""")),
        )
    }

    @Test
    fun `an archetype without usable colors counts toward no pair`() {
        val doc =
            Json.parseToJsonElement(
                """{"archetypes": [{"colors": 12}, {"name": "x"}, "y", {"colors": "WU"}]}"""
            )
        val errors = BriefRules.check(doc)
        assertEquals(listOf("/archetypes"), errors.map { it.pointer })
        assertEquals(
            "no archetype for color pair(s) WB, WR, WG, UB, UR, UG, BR, BG, RG",
            errors.single().message,
        )
    }

    @Test
    fun `slot counts that match cardsPerBooster pass`() {
        val doc =
            Json.parseToJsonElement(
                """{"kitTemplate": {"cardsPerBooster": 3, "slots": [{"count": 1}, {"count": 2}]}}"""
            )
        assertEquals(emptyList(), BriefRules.check(doc))
    }
}
