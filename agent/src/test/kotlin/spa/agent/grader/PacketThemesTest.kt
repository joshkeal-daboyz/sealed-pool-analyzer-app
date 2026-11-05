package spa.agent.grader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import spa.agent.grader.GraderTestCards.brief
import spa.agent.grader.GraderTestCards.card
import spa.agent.grader.GraderTestCards.face
import spa.agent.grader.GraderTestCards.printing
import spa.agent.themes.ThemeCard
import spa.core.cards.CardIndex
import spa.core.cards.Rarity

/** SPA-036 criteria 2 and 3 on hand-made cards: the prompt option and the packet's themes. */
class PacketThemesTest {
    private val bears = (1..3).map { card("Bear $it", face("Bear $it", type = "Creature — Bear")) }
    private val caller =
        card(
            "Caller",
            face("Caller", type = "Creature — Elf", text = "Bears you control get +1/+1."),
        )
    private val mama =
        card(
            "Mama",
            face("Mama", type = "Creature — Bear", text = "Other Bears you control have reach."),
        )
    private val rock =
        card("Rock", face("Rock", type = "Artifact", text = "Tap: draw.", stats = null))
    private val all = bears + caller + mama + rock
    private val index =
        CardIndex(listOf("xyz"), all.mapIndexed { i, c -> printing(c, "xyz", "${i + 1}") })

    private fun themes(): PacketThemes =
        PacketThemes.of(all.map { ThemeCard(it, Rarity.COMMON, emptyList()) }, emptyList())

    @Test
    fun `a card has a theme, refers to one, or has none, and having beats referring`() {
        val themes = themes()

        assertEquals(listOf(PacketTheme("Bear", PacketThemes.HAS)), themes.of("oracle-Bear 1"))
        assertEquals(listOf(PacketTheme("Bear", PacketThemes.REFERS)), themes.of("oracle-Caller"))
        assertEquals(listOf(PacketTheme("Bear", PacketThemes.HAS)), themes.of("oracle-Mama"))
        assertEquals(emptyList(), themes.of("oracle-Rock"))
        assertEquals(emptyList(), themes.of("oracle-unknown"))
    }

    @Test
    fun `only kept themes are listed`() {
        val themes = themes()

        assertEquals(listOf("Bear"), themes.counts.map { it.theme.name })
        assertTrue(themes.byOracleId.values.flatten().none { it.name == "Elf" })
    }

    @Test
    fun `the section has one line per kept theme with kind, has, refers and colours`() {
        val text = themes().render()

        assertTrue(text.startsWith("## Themes in this set\n\n"), text)
        assertTrue(text.endsWith("\n"))
        assertTrue(
            "- Bear (type): has 4, refers 2; colours W=0 U=0 B=0 R=0 G=0 M=0 C=5\n" in text,
            text,
        )
        assertEquals(1, text.lines().count { it.startsWith("- ") })
    }

    @Test
    fun `a packet with themes gives every card its themes and the section after the brief`() {
        val packet = GradingPackets.build(index, brief(), themes = themes())
        val instructions = packet.files.first().text
        val cards = Json.parseToJsonElement(packet.files[2].text).jsonArray

        val at = instructions.indexOf("## Themes in this set")
        assertTrue(at > instructions.indexOf("### Archetypes"))
        assertTrue(at < instructions.indexOf("## The files in this packet"))
        assertTrue(cards.all { "themes" in it.jsonObject })
        assertEquals("[]", cards.last().jsonObject["themes"].toString())
        assertEquals(
            "[{\"name\":\"Bear\",\"role\":\"refers\"}]",
            cards[3].jsonObject["themes"].toString(),
        )
    }

    @Test
    fun `a packet without themes has no themes and a given prompt replaces v1`() {
        val plain = GradingPackets.build(index, brief())
        val custom = GradingPackets.build(index, brief(), prompt = "Custom.\r\nTwo.\n\n")

        assertFalse(plain.files.any { "themes" in it.text || "Themes in this set" in it.text })
        val text = custom.files.first().text
        assertTrue(text.startsWith("Custom.\nTwo.\n\n## The format"), text.take(80))
        assertEquals(
            plain.files.first().text.removePrefix(GradingPackets.promptText.trimEnd()),
            text.removePrefix("Custom.\nTwo."),
        )
        assertEquals(plain.files.drop(1), custom.files.drop(1))
    }
}
