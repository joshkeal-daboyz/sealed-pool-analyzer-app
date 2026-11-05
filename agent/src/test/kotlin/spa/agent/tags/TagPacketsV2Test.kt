package spa.agent.tags

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.grader.GraderTestCards.brief
import spa.agent.grader.GraderTestCards.numbered
import spa.agent.grader.GradingPacket
import spa.agent.grader.GradingPackets

/** SPA-051 criterion 3, on a hand-made set of 60 cards: the v2 packets carry the card facts. */
class TagPacketsV2Test {
    @TempDir lateinit var temp: Path

    private val index = numbered(60)

    /** Every card has `creature`; even cards are `legendary`, every third card `kw:flying`. */
    private val facts: Map<String, Set<String>> =
        (1..60).associate { n ->
            "xyz/$n" to
                buildSet {
                    add("creature")
                    if (n % 2 == 0) add("legendary")
                    if (n % 3 == 0) add("kw:flying")
                    if (n % 3 == 0) add("cheap")
                }
        }

    private val axes =
        listOf(
            Axis("legends", "Legends", "Legendary cards.", listOf(FactSupply("legendary", 2))),
            Axis(
                "fliers",
                "Fliers",
                "Flying creatures.",
                listOf(FactSupply("kw:flying", 3), FactSupply("creature", 1)),
            ),
            Axis("tokens", "Tokens", "Makes a token.", emptyList()),
        )

    private fun text(packet: GradingPacket, name: String) =
        packet.files.single { it.name == name }.text

    private fun pass1() = TagPackets.axesPacket(index, brief(), null, "Axes v2.", facts)

    @Test
    fun `pass 1 adds facts json and keeps the grading card files`() {
        val packet = pass1()

        assertEquals(
            listOf(
                "instructions.md",
                "axes.schema.json",
                "facts.json",
                "cards-01.json",
                "cards-02.json",
            ),
            packet.files.map { it.name },
        )
        val grading =
            GradingPackets.build(index, brief()).files.filter { GradingPackets.isCardFile(it.name) }
        assertEquals(grading, packet.files.drop(3))
    }

    @Test
    fun `facts json maps each card id to its sorted facts, in pool order`() {
        val json = Json.parseToJsonElement(text(pass1(), "facts.json")).jsonObject

        assertEquals((1..60).map { "xyz/$it" }, json.keys.toList())
        assertEquals(
            listOf("cheap", "creature", "kw:flying", "legendary"),
            json.getValue("xyz/6").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("creature"),
            json.getValue("xyz/1").jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(text(pass1(), "facts.json").endsWith("}\n"))
    }

    @Test
    fun `the card facts section lists each fact with its count, most first, then by name`() {
        val instructions = text(pass1(), "instructions.md")

        val section =
            "## Card facts\n\n" +
                "Each fact a card of the set has, with the number of cards that have it, most " +
                "first. `facts.json` gives each card's facts by its id.\n\n" +
                "- `creature`: 60\n" +
                "- `legendary`: 30\n" +
                "- `cheap`: 20\n" +
                "- `kw:flying`: 20\n\n"
        assertTrue(section in instructions, instructions)
        assertTrue(instructions.indexOf("## The format: ") < instructions.indexOf(section))
        assertTrue(
            instructions.indexOf(section) < instructions.indexOf("## The files in this folder")
        )
        assertTrue("- `facts.json`: each card's facts, by card id." in instructions)
    }

    @Test
    fun `the pass 1 schema asks for suppliedBy and allows 20 axes`() {
        val schema = Json.parseToJsonElement(text(pass1(), "axes.schema.json")).jsonObject

        assertEquals("20", schema.getValue("maxItems").jsonPrimitive.content)
        val items = schema.getValue("items").jsonObject
        assertEquals(
            listOf("id", "name", "definition", "suppliedBy"),
            items.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue("suppliedBy" in items.getValue("properties").jsonObject)
    }

    @Test
    fun `pass 2 shows each axis's suppliedBy and writes it to axes json`() {
        val packet = TagPackets.rolesPacket(index, brief(), null, "Roles v2.", axes, facts)

        assertEquals(
            listOf(
                "instructions.md",
                "response.schema.json",
                "axes.json",
                "facts.json",
                "cards-01.json",
                "cards-02.json",
            ),
            packet.files.map { it.name },
        )
        val instructions = text(packet, "instructions.md")
        assertTrue(
            instructions.startsWith(
                "Roles v2.\n\n## The set's synergy axes\n\nUse these axes only, each by its " +
                    "id.\n\n" +
                    "- `legends`, Legends: Legendary cards. Supplied by: `legendary` 2.\n" +
                    "- `fliers`, Fliers: Flying creatures. Supplied by: `kw:flying` 3, " +
                    "`creature` 1.\n" +
                    "- `tokens`, Tokens: Makes a token. Supplied by: no fact.\n\n" +
                    "## The format: "
            ),
            instructions,
        )
        assertTrue("- `facts.json`: each card's facts, by card id." in instructions)
        assertEquals(
            axes,
            AxesFile.parse(text(packet, "axes.json"), "axes.json", AxesFormat.SUPPLIED).axes,
        )
        assertEquals(text(pass1(), "facts.json"), text(packet, "facts.json"))
    }

    @Test
    fun `without facts the packets are the v1 packets`() {
        val v1 = TagPackets.axesPacket(index, brief(), null, "Axes.")
        val plain = axes.map { Axis(it.id, it.name, it.definition) }

        assertFalse(v1.files.any { it.name == "facts.json" })
        assertEquals(TagPacketText.axesSchema, text(v1, "axes.schema.json"))
        assertFalse("Card facts" in text(v1, "instructions.md"))
        val roles = TagPackets.rolesPacket(index, brief(), null, "Roles.", plain)
        assertFalse(roles.files.any { it.name == "facts.json" })
        assertFalse("Supplied by" in text(roles, "instructions.md"))
        assertFalse("suppliedBy" in text(roles, "axes.json"))
    }

    @Test
    fun `writing a v1 packet over a v2 one removes facts json`() {
        val dir = temp.resolve("p")
        TagPackets.write(pass1(), dir)
        assertTrue(dir.resolve("facts.json").exists())

        TagPackets.write(TagPackets.axesPacket(index, brief(), null, "Axes."), dir)

        assertFalse(dir.resolve("facts.json").exists())
        assertTrue(Files.list(dir).use { s -> s.anyMatch { it.name == "instructions.md" } })
    }
}
