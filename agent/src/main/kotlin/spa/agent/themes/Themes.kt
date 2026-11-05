package spa.agent.themes

import spa.core.cards.Card
import spa.core.cards.Rarity
import spa.core.text.Color

/** What a theme is named after: a Scryfall keyword, or a subtype on a type line. */
enum class ThemeKind(val label: String) {
    KEYWORD("keyword"),
    TYPE("type"),
}

/** A theme: a non-evergreen keyword or a subtype, by its name as the cards spell it. */
data class Theme(val name: String, val kind: ThemeKind)

/** The colour groups the counts are split by, from [Card.colors]. */
enum class ColourGroup(val label: String) {
    W("W"),
    U("U"),
    B("B"),
    R("R"),
    G("G"),
    MULTICOLOURED("M"),
    COLOURLESS("C");

    companion object {
        /** The group of a card whose colours are [colors]. */
        fun of(colors: Set<Color>): ColourGroup = TODO()
    }
}

/**
 * One card of a pool, with its rarity in the pool and its Scryfall `keywords`, which `core`'s
 * [Card] does not keep.
 */
data class ThemeCard(val card: Card, val rarity: Rarity, val keywords: List<String>)

/**
 * A number of cards, split by colour group and by rarity. Both maps hold every key, zeros included:
 * every [ColourGroup], and [Rarity.COMMON], [Rarity.UNCOMMON], [Rarity.RARE] and [Rarity.MYTHIC]. A
 * card of any other rarity counts in [total] and its colour group only.
 */
data class Tally(
    val total: Int,
    val byColour: Map<ColourGroup, Int>,
    val byRarity: Map<Rarity, Int>,
) {
    companion object {
        /** The rarities a [Tally] splits by. */
        val RARITIES = listOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.MYTHIC)

        /** The tally of [cards]. */
        fun of(cards: List<ThemeCard>): Tally = TODO()
    }
}

/** A theme's counts in a pool: the cards that have it, that refer to it, and that do either. */
data class ThemeCount(val theme: Theme, val has: Tally, val refers: Tally, val either: Tally)

/** The rules that tag a card with themes. */
object ThemeRules {
    /** Keywords that are never themes unless the set's brief names them as mechanics. */
    val EVERGREEN: Set<String> =
        setOf(
            "Deathtouch",
            "Defender",
            "Double strike",
            "Enchant",
            "Equip",
            "First strike",
            "Flash",
            "Flying",
            "Haste",
            "Hexproof",
            "Indestructible",
            "Lifelink",
            "Menace",
            "Protection",
            "Reach",
            "Trample",
            "Vigilance",
            "Ward",
            "Scry",
            "Mill",
            "Fight",
        )

    /**
     * The themes [card] brings: each of its keywords that is not evergreen or is named in
     * [mechanics], and each subtype on any face's type line.
     */
    fun themesOf(card: ThemeCard, mechanics: Collection<String>): Set<Theme> = TODO()

    /**
     * Whether [card] has [theme]: the keyword is in its keywords, or the subtype on a type line.
     */
    fun has(card: ThemeCard, theme: Theme): Boolean = TODO()

    /**
     * Whether [card] refers to [theme]: its rules text, without reminder text, its own face names
     * and every `this <theme>`, names the theme as a whole word (any case, optional plural `s`),
     * and the card does not have the theme's name as a keyword.
     */
    fun refersTo(card: ThemeCard, theme: Theme): Boolean = TODO()
}

/** The themes of a pool and their counts. */
object SetThemes {
    /** A theme is kept when at least this many cards have it or refer to it. */
    const val MIN_CARDS = 3

    /**
     * The kept themes of [cards] with their counts, most cards first ("either"), ties by name.
     * [mechanics] are the set brief's mechanic names, which are themes even when evergreen.
     */
    fun count(cards: List<ThemeCard>, mechanics: Collection<String>): List<ThemeCount> = TODO()
}
