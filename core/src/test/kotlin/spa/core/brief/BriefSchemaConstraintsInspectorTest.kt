package spa.core.brief

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Added by the Test Inspector. Each case makes one change to `valid-minimal.json` that breaks one
 * field constraint named in criteria 1 to 3, and asserts exactly the pointers it fails with.
 */
class BriefSchemaConstraintsInspectorTest {
    private class Case(
        val name: String,
        val from: String,
        val to: String,
        val pointers: List<String>,
    )

    private val cases =
        listOf(
            Case(
                "setCode too short",
                "\"setCode\": \"tst\"",
                "\"setCode\": \"ts\"",
                listOf("/setCode"),
            ),
            Case(
                "setCode too long",
                "\"setCode\": \"tst\"",
                "\"setCode\": \"tstabc\"",
                listOf("/setCode"),
            ),
            Case("version zero", "\"version\": 1", "\"version\": 0", listOf("/version")),
            Case(
                "speed not in enum",
                "\"speed\": \"fast\"",
                "\"speed\": \"brisk\"",
                listOf("/speed"),
            ),
            Case(
                "mechanic without rulesText",
                "\"name\": \"Tether\",\n      \"rulesText\": \"Tether rules.\"",
                "\"name\": \"Tether\"",
                listOf("/mechanics/1"),
            ),
            Case(
                "colors with a letter outside WUBRG",
                "\"colors\": \"WU\"",
                "\"colors\": \"WX\"",
                listOf("/archetypes/0/colors", "/archetypes"),
            ),
            Case(
                "empty signpostUncommons",
                "\"signpostUncommons\": [\n        \"Signpost 0\"\n      ]",
                "\"signpostUncommons\": []",
                listOf("/archetypes/0/signpostUncommons"),
            ),
            Case(
                "generic archetype without description",
                "\"genericArchetypes\": []",
                "\"genericArchetypes\": [{\"name\": \"Five-color\"}]",
                listOf("/genericArchetypes/0"),
            ),
            Case(
                "boosters zero",
                "\"boosters\": 6",
                "\"boosters\": 0",
                listOf("/kitTemplate/boosters"),
            ),
            Case(
                "slot count zero",
                "\"name\": \"Rare or mythic\",\n        \"count\": 1",
                "\"name\": \"Rare or mythic\",\n        \"count\": 0",
                listOf("/kitTemplate/slots/2/count", "/kitTemplate/slots"),
            ),
            Case(
                "foil not boolean",
                "\"name\": \"Land\",",
                "\"name\": \"Land\",\n        \"foil\": \"yes\",",
                listOf("/kitTemplate/slots/4/foil"),
            ),
            Case(
                "weight key outside the seven kinds",
                "\"commonLand\": 1",
                "\"nonbasicLand\": 1",
                listOf("/kitTemplate/slots/4/weights/nonbasicLand"),
            ),
            Case(
                "promo without weights",
                "\"promo\": null",
                "\"promo\": {}",
                listOf("/kitTemplate/promo"),
            ),
            Case(
                "promo weight of zero",
                "\"promo\": null",
                "\"promo\": {\"weights\": {\"rare\": 0}}",
                listOf("/kitTemplate/promo/weights/rare"),
            ),
            Case(
                "basicLandsSupplied not boolean",
                "\"basicLandsSupplied\": true",
                "\"basicLandsSupplied\": \"yes\"",
                listOf("/kitTemplate/basicLandsSupplied"),
            ),
        )

    @TestFactory
    fun `each broken field constraint fails at its own pointer`(): List<DynamicTest> {
        val base = briefResource("valid-minimal")
        return cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                assertEquals(1, base.split(case.from).size - 1, "'${case.from}' occurs once")
                val errors = loadErrors(base.replace(case.from, case.to))
                assertEquals(case.pointers, errors.map { it.pointer }, errors.joinToString())
            }
        }
    }

    @Test
    fun `a promo with weights loads`() {
        val text =
            briefResource("valid-minimal")
                .replace(
                    "\"promo\": null",
                    "\"promo\": {\"weights\": {\"rare\": 6, \"mythic\": 1}}",
                )
        val promo = BriefLoader.parse(text).kitTemplate.promo
        assertEquals(mapOf(SlotKind.RARE to 6.0, SlotKind.MYTHIC to 1.0), promo?.weights)
    }
}
