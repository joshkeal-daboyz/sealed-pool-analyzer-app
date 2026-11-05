package spa.agent.themes

import java.nio.file.Path
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import spa.agent.grader.GradingPool
import spa.core.cards.CardIndex
import spa.core.cards.ScryfallCache

/** A set's sealed pool as [ThemeCard]s, with keywords read from the Scryfall cache. */
object PoolThemes {
    /**
     * Each card's Scryfall `keywords` by oracle id, from the cached `cards.json` of each of
     * [setCodes] under [scryfallDir]. The first set holding an oracle id wins.
     */
    fun keywords(scryfallDir: Path, setCodes: List<String>): Map<String, List<String>> {
        val byOracleId = mutableMapOf<String, List<String>>()
        setCodes.forEach { code ->
            ScryfallCache.load(scryfallDir, code).cards.forEach { card ->
                val oracleId = oracleId(card)
                if (oracleId != null && oracleId !in byOracleId) {
                    byOracleId[oracleId] = keywordsOf(card)
                }
            }
        }
        return byOracleId
    }

    /**
     * The sealed pool of [index] ([spa.agent.grader.GradingPool]) as [ThemeCard]s, each with its
     * printing's rarity and its keywords from [keywords] (none when absent).
     */
    fun cards(index: CardIndex, keywords: Map<String, List<String>>): List<ThemeCard> =
        GradingPool.printings(index).map {
            ThemeCard(it.card, it.rarity, keywords[it.card.oracleId].orEmpty())
        }

    /** The pool's kept themes: [cards] of the index loaded from [scryfallDir], then counted. */
    fun count(
        scryfallDir: Path,
        index: CardIndex,
        mechanics: Collection<String>,
    ): List<ThemeCount> =
        SetThemes.count(cards(index, keywords(scryfallDir, index.setCodes)), mechanics)

    /** The card's oracle id, on the card or, for some multi-face layouts, on its first face. */
    private fun oracleId(card: JsonObject): String? =
        string(card["oracle_id"])
            ?: (card["card_faces"] as? JsonArray)?.firstNotNullOfOrNull {
                string((it as? JsonObject)?.get("oracle_id"))
            }

    private fun keywordsOf(card: JsonObject): List<String> =
        (card["keywords"] as? JsonArray)?.mapNotNull { string(it) }.orEmpty()

    private fun string(element: Any?): String? = (element as? JsonPrimitive)?.contentOrNull
}
