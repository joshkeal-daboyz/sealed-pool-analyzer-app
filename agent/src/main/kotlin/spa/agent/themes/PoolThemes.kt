package spa.agent.themes

import java.nio.file.Path
import spa.core.cards.CardIndex

/** A set's sealed pool as [ThemeCard]s, with keywords read from the Scryfall cache. */
object PoolThemes {
    /**
     * Each card's Scryfall `keywords` by oracle id, from the cached `cards.json` of each of
     * [setCodes] under [scryfallDir]. The first set holding an oracle id wins.
     */
    fun keywords(scryfallDir: Path, setCodes: List<String>): Map<String, List<String>> = TODO()

    /**
     * The sealed pool of [index] ([spa.agent.grader.GradingPool]) as [ThemeCard]s, each with its
     * printing's rarity and its keywords from [keywords] (none when absent).
     */
    fun cards(index: CardIndex, keywords: Map<String, List<String>>): List<ThemeCard> = TODO()

    /** The pool's kept themes: [cards] of the index loaded from [scryfallDir], then counted. */
    fun count(
        scryfallDir: Path,
        index: CardIndex,
        mechanics: Collection<String>,
    ): List<ThemeCount> = TODO()
}
