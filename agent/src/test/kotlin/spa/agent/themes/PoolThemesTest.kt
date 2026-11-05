package spa.agent.themes

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.core.cards.Card
import spa.core.cards.CardIndex
import spa.core.cards.Face
import spa.core.cards.Printing
import spa.core.cards.Rarity

/** The pool's keywords come from the Scryfall cache by oracle id; the pool is SPA-033's. */
class PoolThemesTest {
    @TempDir lateinit var temp: Path

    private fun cache(code: String, cards: String) {
        val dir = Files.createDirectories(temp.resolve(code))
        Files.writeString(dir.resolve("set.json"), """{"code":"$code"}""")
        Files.writeString(dir.resolve("cards.json"), cards)
        Files.writeString(dir.resolve("fetched-at.txt"), "2026-11-04T00:00:00Z")
    }

    private fun card(name: String, type: String, basic: Boolean = false): Card {
        val face = Face(name, "{1}", 1.0, emptySet(), type, "", null, null)
        return Card(
            "oracle-$name",
            name,
            "normal",
            listOf(face),
            emptySet(),
            emptySet(),
            1.0,
            type,
            false,
            basic,
            basic,
            Rarity.COMMON,
        )
    }

    private fun printing(card: Card, set: String, number: String, rarity: Rarity) =
        Printing("id-$set-$number", set, number, rarity, emptyList(), card)

    @Test
    fun `keywords are read by oracle id, the first set holding one winning`() {
        cache(
            "xyz",
            """[{"oracle_id":"o1","keywords":["Station","Flying"]},
               {"card_faces":[{"oracle_id":"o2"},{}],"keywords":["Surveil"]},
               {"oracle_id":"o3"}]""",
        )
        cache(
            "abc",
            """[{"oracle_id":"o1","keywords":["Other"]},{"oracle_id":"o4","keywords":[]}]""",
        )
        assertEquals(
            mapOf(
                "o1" to listOf("Station", "Flying"),
                "o2" to listOf("Surveil"),
                "o3" to emptyList(),
                "o4" to emptyList(),
            ),
            PoolThemes.keywords(temp, listOf("xyz", "abc")),
        )
    }

    @Test
    fun `the pool's cards carry their printing's rarity and keywords, basics left out`() {
        val ship = card("Ship", "Artifact — Spacecraft")
        val index =
            CardIndex(
                listOf("xyz"),
                listOf(
                    printing(ship, "xyz", "1", Rarity.RARE),
                    printing(ship, "xyz", "300", Rarity.MYTHIC),
                    printing(
                        card("Plains", "Basic Land — Plains", basic = true),
                        "xyz",
                        "2",
                        Rarity.COMMON,
                    ),
                    printing(card("Bear", "Creature — Bear"), "xyz", "3", Rarity.COMMON),
                ),
            )
        val cards = PoolThemes.cards(index, mapOf("oracle-Ship" to listOf("Station")))
        assertEquals(listOf("Ship", "Bear"), cards.map { it.card.name })
        assertEquals(listOf(Rarity.RARE, Rarity.COMMON), cards.map { it.rarity })
        assertEquals(listOf(listOf("Station"), emptyList()), cards.map { it.keywords })
    }

    @Test
    fun `count reads the cache and counts the pool's themes`() {
        val ships = (1..3).map { card("Ship $it", "Artifact — Spacecraft") }
        cache(
            "xyz",
            ships.joinToString(",", "[", "]") {
                """{"oracle_id":"${it.oracleId}","keywords":["Station"]}"""
            },
        )
        val index =
            CardIndex(
                listOf("xyz"),
                ships.mapIndexed { i, c -> printing(c, "xyz", "${i + 1}", Rarity.COMMON) },
            )
        val counts = PoolThemes.count(temp, index, emptyList())
        assertEquals(
            listOf(Theme("Spacecraft", ThemeKind.TYPE), Theme("Station", ThemeKind.KEYWORD)),
            counts.map { it.theme },
        )
        assertEquals(listOf(3, 3), counts.map { it.has.total })
    }
}
