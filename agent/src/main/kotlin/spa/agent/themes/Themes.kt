package spa.agent.themes

import java.util.Locale
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
        fun of(colors: Set<Color>): ColourGroup =
            when (colors.size) {
                0 -> COLOURLESS
                1 -> valueOf(colors.single().name)
                else -> MULTICOLOURED
            }
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
        fun of(cards: List<ThemeCard>): Tally {
            val colours = cards.groupingBy { ColourGroup.of(it.card.colors) }.eachCount()
            val rarities = cards.groupingBy { it.rarity }.eachCount()
            return Tally(
                cards.size,
                ColourGroup.entries.associateWith { colours[it] ?: 0 },
                RARITIES.associateWith { rarities[it] ?: 0 },
            )
        }
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

    private val evergreenKeys = EVERGREEN.map { it.lowercase(Locale.ROOT) }.toSet()

    /**
     * The themes [card] brings: each of its keywords that is not evergreen or is named in
     * [mechanics], and each subtype on any face's type line.
     */
    fun themesOf(card: ThemeCard, mechanics: Collection<String>): Set<Theme> {
        val named = mechanics.map { it.lowercase(Locale.ROOT) }.toSet()
        val keywords =
            card.keywords
                .filter {
                    val key = it.lowercase(Locale.ROOT)
                    key in named || key !in evergreenKeys
                }
                .map { Theme(it, ThemeKind.KEYWORD) }
        return (keywords + subtypes(card).map { Theme(it, ThemeKind.TYPE) }).toSet()
    }

    /**
     * Whether [card] has [theme]: the keyword is in its keywords, or the subtype on a type line.
     */
    fun has(card: ThemeCard, theme: Theme): Boolean =
        when (theme.kind) {
            ThemeKind.KEYWORD -> hasKeyword(card, theme.name)
            ThemeKind.TYPE -> theme.name in subtypes(card)
        }

    /**
     * Whether [card] refers to [theme]: its rules text, without reminder text, its own face names
     * and every `this <theme>`, names the theme as a whole word (any case, optional plural `s`),
     * and the card does not have the theme's name as a keyword.
     */
    fun refersTo(card: ThemeCard, theme: Theme): Boolean {
        if (hasKeyword(card, theme.name)) return false
        val name = Regex.escape(theme.name)
        val text = rulesText(card).replace(Regex("\\bthis $name\\b", RegexOption.IGNORE_CASE), " ")
        return Regex("\\b${name}s?\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    /** Whether [name] is one of [card]'s keywords, in any case. */
    private fun hasKeyword(card: ThemeCard, name: String): Boolean =
        card.keywords.any { it.equals(name, ignoreCase = true) }

    /** The subtypes on [card]'s faces: the words after the dash on each face's type line. */
    private fun subtypes(card: ThemeCard): Set<String> =
        card.card.faces
            .flatMap { face ->
                face.typeLine.substringAfter(DASH, "").split(' ').filter { it.isNotBlank() }
            }
            .toSet()

    /** [card]'s rules text, all faces, with reminder text and the card's own names taken out. */
    private fun rulesText(card: ThemeCard): String {
        val names =
            (card.card.faces.map { it.name } + card.card.name)
                .filter { it.isNotBlank() }
                .sortedByDescending { it.length }
        val text = card.card.faces.joinToString("\n") { it.oracleText }.replace(REMINDER, " ")
        return names.fold(text) { acc, name -> acc.replace(name, " ", ignoreCase = true) }
    }

    private const val DASH = '—'
    private val REMINDER = Regex("\\([^)]*\\)")
}

/** The themes of a pool and their counts. */
object SetThemes {
    /** A theme is kept when at least this many cards have it or refer to it. */
    const val MIN_CARDS = 3

    /**
     * The kept themes of [cards] with their counts, most cards first ("either"), ties by name.
     * [mechanics] are the set brief's mechanic names, which are themes even when evergreen.
     */
    fun count(cards: List<ThemeCard>, mechanics: Collection<String>): List<ThemeCount> =
        cards
            .flatMap { ThemeRules.themesOf(it, mechanics) }
            .distinct()
            .map { countOf(it, cards) }
            .filter { it.either.total >= MIN_CARDS }
            .sortedWith(
                compareByDescending<ThemeCount> { it.either.total }
                    .thenBy { it.theme.name.lowercase(Locale.ROOT) }
                    .thenBy { it.theme.name }
                    .thenBy { it.theme.kind }
            )

    private fun countOf(theme: Theme, cards: List<ThemeCard>): ThemeCount {
        val has = cards.filter { ThemeRules.has(it, theme) }
        val refers = cards.filter { ThemeRules.refersTo(it, theme) }
        val either = cards.filter { ThemeRules.has(it, theme) || ThemeRules.refersTo(it, theme) }
        return ThemeCount(theme, Tally.of(has), Tally.of(refers), Tally.of(either))
    }
}
