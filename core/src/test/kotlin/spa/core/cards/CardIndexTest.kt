package spa.core.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonPrimitive

class CardIndexTest {
    private val syn = CardIndex.load(fixturesDir, "syn")

    @Test
    fun `trk loads with its companion sds and holds every printing of both`() {
        val trk = CardIndex.load(fixturesDir, "trk")

        assertEquals(listOf("trk", "sds"), trk.setCodes)
        val cardCounts = trk.setCodes.sumOf { ScryfallCache.load(fixturesDir, it).cardCount }
        assertEquals(cardCounts, trk.printings.size)
        assertEquals(611, trk.printings.size)
        assertEquals(581, trk.printings("trk").size)
        assertEquals(30, trk.printings("sds").size)
        assertTrue(trk.printings.none { it.setCode in setOf("trc", "ttrk", "atrk") })
    }

    @Test
    fun `printings of one card share a single Card instance`() {
        val trk = CardIndex.load(fixturesDir, "trk")

        trk.printings
            .groupBy { it.card.oracleId }
            .values
            .forEach { printings ->
                printings.forEach { assertSame(printings.first().card, it.card) }
            }
        assertEquals(trk.printings.map { it.card.oracleId }.distinct().size, trk.cards.size)
        assertTrue(trk.cards.size < trk.printings.size, "trk has variant printings")
    }

    @Test
    fun `syn pins its card count and layout counts`() {
        assertEquals(listOf("syn"), syn.setCodes)
        val cardCount = ScryfallCache.load(fixturesDir, "syn").cardCount
        assertEquals(25, cardCount)
        assertEquals(cardCount, syn.printings.size)
        assertEquals(25, syn.cards.size)
        assertEquals(
            mapOf(
                "normal" to 18,
                "transform" to 2,
                "modal_dfc" to 1,
                "adventure" to 2,
                "split" to 2,
            ),
            syn.cards.groupingBy { it.layout }.eachCount(),
        )
        assertEquals(5, syn.cards.count { it.isBasicLand })
        assertEquals(2, syn.cards.count { it.isLand && !it.isBasicLand })
        assertEquals(
            mapOf(Color.W to 4, Color.U to 4, Color.B to 4, Color.R to 5, Color.G to 5),
            Color.entries.associateWith { color -> syn.cards.count { color in it.colors } },
        )
        assertEquals((1..25).map(Int::toString), syn.printings.map { it.collectorNumber })
    }

    @Test
    fun `syn keeps Scryfall's ids and only rewrites set, set name and collector number`() {
        val raw = ScryfallCache.load(fixturesDir, "syn").cards
        val bears = resourceCard("grizzly-bears")

        assertTrue(raw.all { it["set"]!!.jsonPrimitive.content == "syn" })
        assertTrue(raw.all { it["set_name"]!!.jsonPrimitive.content == "Synthetic Test Set" })
        val synBears = raw.first()
        assertEquals(
            bears.keys
                .filterNot { it in setOf("set", "set_name", "collector_number") }
                .associateWith { bears[it] },
            synBears.keys
                .filterNot { it in setOf("set", "set_name", "collector_number") }
                .associateWith { synBears[it] },
        )
    }

    @Test
    fun `byScryfallId finds a printing or returns null`() {
        val id = resourceCard("grizzly-bears")["id"]!!.jsonPrimitive.content

        assertEquals("Grizzly Bears", syn.byScryfallId(id)?.card?.name)
        assertEquals("1", syn.byScryfallId(id)?.collectorNumber)
        assertNull(syn.byScryfallId("00000000-0000-0000-0000-000000000000"))
    }

    @Test
    fun `byName matches full names and single face names, ignoring case and whitespace`() {
        val delver = assertNotNull(syn.byName("Delver of Secrets // Insectile Aberration"))

        assertSame(delver, syn.byName("Delver of Secrets"))
        assertSame(delver, syn.byName("  delver of SECRETS "))
        assertSame(delver, syn.byName("Insectile Aberration"))
        assertEquals("Fire // Ice", syn.byName("ice")?.name)
        assertEquals("Bonecrusher Giant // Stomp", syn.byName("Stomp")?.name)
        assertEquals("Grizzly Bears", syn.byName("grizzly bears")?.name)
    }

    @Test
    fun `byName maps curly apostrophes to straight ones`() {
        val agadeem = assertNotNull(syn.byName("Agadeem's Awakening"))

        assertSame(agadeem, syn.byName("Agadeem’s Awakening"))
        assertSame(agadeem, syn.byName("Agadeem‘s Awakening"))
    }

    @Test
    fun `byName is exact, so a misspelling is null`() {
        assertNull(syn.byName("Grizly Bears"))
        assertNull(syn.byName("Delver of Secret"))
        assertNull(syn.byName("Delver"))
        assertNull(syn.byName(""))
    }

    @Test
    fun `a full name wins over another card's face name`() {
        val fire = json(cardJson("fire").replace("\"Card fire\"", "\"Fire\""))
        val fireIce = resourceCard("wear-tear").toString().replace("Wear", "Fire").let(::json)
        val index = CardIndex(listOf("xyz"), CardNormalizer.printings(listOf(fireIce, fire)))

        assertEquals("Fire", index.byName("fire")?.name)
        assertEquals("Fire // Tear", index.byName("tear")?.name)
    }

    @Test
    fun `byCollectorNumber finds a printing by set and number`() {
        assertEquals("Forest", syn.byCollectorNumber("syn", "25")?.card?.name)
        assertEquals("Forest", syn.byCollectorNumber("SYN", "25")?.card?.name)
        assertNull(syn.byCollectorNumber("syn", "26"))
        assertNull(syn.byCollectorNumber("trk", "1"))
    }

    @Test
    fun `printings lists one set and is empty for a set not loaded`() {
        assertEquals(25, syn.printings("syn").size)
        assertEquals(syn.printings, syn.printings("syn"))
        assertEquals(emptyList(), syn.printings("trk"))
    }
}
