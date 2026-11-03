package spa.core.brief

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class BriefLoaderTest {
    @Test
    fun `syn json loads as a complete brief`() {
        val brief = BriefLoader.load(synBriefPath)
        assertEquals("syn", brief.setCode)
        assertEquals(1, brief.version)
        assertEquals(FormatSpeed.MEDIUM, brief.speed)
        assertTrue(brief.mechanics.size >= 3, "at least 3 mechanics")
        assertTrue(brief.mechanics.all { it.name.isNotBlank() && it.rulesText.isNotBlank() })
        assertEquals(10, brief.archetypes.size)
        assertEquals(
            BriefRules.colorPairs,
            brief.archetypes.map { BriefRules.normalizePair(it.colors) }.toSet(),
        )
        assertTrue(
            brief.archetypes.all { it.name.isNotBlank() && it.signpostUncommons.isNotEmpty() }
        )
        assertTrue(brief.genericArchetypes.size >= 2, "at least 2 generic archetypes")
    }

    @Test
    fun `syn json models a Play Booster prerelease kit`() {
        val kit = BriefLoader.load(synBriefPath).kitTemplate
        assertEquals(6, kit.boosters)
        assertEquals(14, kit.cardsPerBooster)
        assertEquals(14, kit.slots.sumOf { it.count })
        assertTrue(kit.basicLandsSupplied)
        val promo = assertNotNull(kit.promo)
        assertEquals(setOf(SlotKind.RARE, SlotKind.MYTHIC), promo.weights.keys)

        val slots = kit.slots.associateBy { it.name }
        assertEquals(mapOf(SlotKind.COMMON to 1.0), slots.getValue("Common").weights)
        assertEquals(6, slots.getValue("Common").count)

        val bonus = slots.getValue("Common or bonus sheet")
        assertEquals(1, bonus.count)
        assertEquals(setOf(SlotKind.COMMON, SlotKind.BONUS_SHEET), bonus.weights.keys)
        assertTrue(share(bonus, SlotKind.BONUS_SHEET) < SMALL_FRACTION)

        assertEquals(3, slots.getValue("Uncommon").count)
        assertEquals(mapOf(SlotKind.UNCOMMON to 1.0), slots.getValue("Uncommon").weights)

        val rare = slots.getValue("Rare or mythic")
        assertEquals(1, rare.count)
        assertEquals(1.0 / 7, share(rare, SlotKind.MYTHIC), 1e-9)

        val wildcard = slots.getValue("Wildcard")
        val foilWildcard = slots.getValue("Foil wildcard")
        val anyRarity = setOf(SlotKind.COMMON, SlotKind.UNCOMMON, SlotKind.RARE, SlotKind.MYTHIC)
        assertEquals(anyRarity, wildcard.weights.keys)
        assertEquals(anyRarity, foilWildcard.weights.keys)
        assertTrue(!wildcard.foil && foilWildcard.foil)
        assertEquals(listOf(1, 1), listOf(wildcard.count, foilWildcard.count))

        val land = slots.getValue("Land")
        assertEquals(1, land.count)
        assertEquals(setOf(SlotKind.BASIC_LAND, SlotKind.COMMON_LAND), land.weights.keys)
        assertTrue(share(land, SlotKind.BASIC_LAND) > HALF, "basic land most of the time")

        assertEquals(7, kit.slots.size, "no slot beyond the published composition")
        assertEquals(listOf(foilWildcard), kit.slots.filter { it.foil })
    }

    @Test
    fun `the compact base of the invalid examples is itself valid`() {
        val brief = BriefLoader.parse(briefResource("valid-minimal"))
        assertNull(brief.kitTemplate.promo)
        assertEquals(emptyList(), brief.genericArchetypes)
        assertEquals(emptyList(), brief.companionSetCodes)
        assertEquals("(Kindle reminder.)", brief.mechanics[0].reminderText)
        assertNull(brief.mechanics[1].reminderText)
    }

    @Test
    fun `a missing required field fails at the object that lacks it`() {
        val errors = loadErrors(briefResource("missing-required"))
        assertEquals(listOf("/archetypes/3"), errors.map { it.pointer })
        assertTrue("plan" in errors.single().message, errors.single().message)
    }

    @Test
    fun `an unknown property fails at that property`() {
        val errors = loadErrors(briefResource("unknown-property"))
        assertEquals(listOf("/mechanics/1/reminderTxt"), errors.map { it.pointer })
    }

    @Test
    fun `a duplicate color pair written in the other order fails at the second archetype`() {
        val errors = loadErrors(briefResource("duplicate-color-pair"))
        assertEquals(listOf("/archetypes/7/colors", "/archetypes"), errors.map { it.pointer })
        assertTrue("/archetypes/0/colors" in errors[0].message, errors[0].message)
        assertTrue("BG" in errors[1].message, errors[1].message)
    }

    @Test
    fun `a colors value with a repeated letter fails at that value`() {
        val errors = loadErrors(briefResource("repeated-letter"))
        // The schema rejects BB itself; the pair rule then finds BR has no archetype.
        assertEquals(listOf("/archetypes/2/colors", "/archetypes"), errors.map { it.pointer })
        assertTrue("BR" in errors[1].message, errors[1].message)
    }

    @Test
    fun `slot counts that miss cardsPerBooster fail at the slots`() {
        val errors = loadErrors(briefResource("slot-sum"))
        assertEquals(listOf("/kitTemplate/slots"), errors.map { it.pointer })
        assertEquals(
            "slot counts sum to 13 but cardsPerBooster is 14",
            errors.single().message,
        )
    }

    @Test
    fun `a weight of zero fails at that weight`() {
        val errors = loadErrors(briefResource("zero-weight"))
        assertEquals(listOf("/kitTemplate/slots/2/weights/mythic"), errors.map { it.pointer })
    }

    @Test
    fun `nine archetypes fail at the archetypes array`() {
        val errors = loadErrors(briefResource("nine-archetypes"))
        // The schema counts nine; the pair rule names the pair left out.
        assertEquals(listOf("/archetypes", "/archetypes"), errors.map { it.pointer })
        // The dropped archetype was GU, reported in WUBRG order.
        assertTrue(errors.any { "UG" in it.message }, errors.joinToString())
    }

    @Test
    fun `a document with several problems lists every one`() {
        val errors = loadErrors(briefResource("multiple-errors"))
        assertEquals(
            setOf(
                "/setCode",
                "/mechanics/1/reminderTxt",
                "/kitTemplate/slots/2/weights/mythic",
                "/archetypes/7/colors",
                "/archetypes",
                "/kitTemplate/slots",
            ),
            errors.map { it.pointer }.toSet(),
        )
        assertEquals(6, errors.size, errors.joinToString("\n"))
        assertTrue(errors.all { it.message.isNotBlank() && it.message != "invalid" })
    }

    @Test
    fun `the exception message names every error`() {
        val e =
            assertFailsWith<BriefValidationException> {
                BriefLoader.parse(briefResource("multiple-errors"))
            }
        assertTrue(e.message!!.startsWith("Format brief has 6 error(s):"), e.message)
        for (error in e.errors) assertTrue(error.toString() in e.message!!, error.toString())
    }

    @Test
    fun `color pairs in the other order still cover all ten pairs`() {
        val reversed =
            briefResource("valid-minimal").replace(Regex("\"colors\": \"(.)(.)\"")) {
                "\"colors\": \"${it.groupValues[2]}${it.groupValues[1]}\""
            }
        val brief = BriefLoader.parse(reversed)
        assertEquals("UW", brief.archetypes[0].colors)
        assertEquals(10, brief.archetypes.size)
    }

    @Test
    fun `a companion set code that is not lowercase fails at that code`() {
        val text =
            briefResource("valid-minimal")
                .replace("\"setName\"", "\"companionSetCodes\": [\"spg\", \"TRK\"],\n  \"setName\"")
        assertEquals(listOf("/companionSetCodes/1"), loadErrors(text).map { it.pointer })
    }

    @Test
    fun `text that is not JSON fails at the document`() {
        val error = loadErrors("{ not json").single()
        assertEquals("", error.pointer)
        assertTrue(error.message.startsWith("not JSON:"), error.message)
        assertEquals("(document): ${error.message}", error.toString())
    }

    @Test
    fun `an integer the schema accepts but Kotlin cannot hold fails with a message`() {
        val text = briefResource("valid-minimal").replace("\"boosters\": 6", "\"boosters\": 6.0")
        val error = loadErrors(text).single()
        assertEquals("", error.pointer)
        assertTrue(error.message.startsWith("cannot decode:"), error.message)
    }

    @Test
    fun `load reads a brief from a file`(@TempDir dir: Path) {
        val file = dir.resolve("brief.json")
        file.writeText(briefResource("valid-minimal"))
        assertEquals("tst", BriefLoader.load(file).setCode)
        assertFailsWith<java.nio.file.NoSuchFileException> {
            BriefLoader.load(dir.resolve("absent.json"))
        }
    }

    @Test
    fun `load reports a bad file's errors like parse does`(@TempDir dir: Path) {
        val file = Files.writeString(dir.resolve("bad.json"), briefResource("zero-weight"))
        val e = assertFailsWith<BriefValidationException> { BriefLoader.load(file) }
        assertEquals(listOf("/kitTemplate/slots/2/weights/mythic"), e.errors.map { it.pointer })
    }

    @Test
    fun `syn json round-trips through encodeToString to an equal brief`() {
        val brief = BriefLoader.load(synBriefPath)
        val again = BriefLoader.parse(BriefLoader.encodeToString(brief))
        assertEquals(brief, again)
    }

    @Test
    fun `a brief with a null promo and optional fields round-trips`() {
        val brief = BriefLoader.parse(briefResource("valid-minimal"))
        val encoded = BriefLoader.encodeToString(brief)
        assertTrue("\"promo\": null" in encoded, encoded)
        assertEquals(brief, BriefLoader.parse(encoded))
    }

    private fun share(slot: BoosterSlot, kind: SlotKind): Double =
        slot.weights.getValue(kind) / slot.weights.values.sum()

    private companion object {
        const val SMALL_FRACTION = 0.1
        const val HALF = 0.5
    }
}
