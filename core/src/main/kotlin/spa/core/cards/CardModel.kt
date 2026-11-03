package spa.core.cards

/**
 * One physical printing of a card in one set. Every printing of the same oracle card shares one
 * [Card] instance.
 */
data class Printing(
    val scryfallId: String,
    val setCode: String,
    val collectorNumber: String,
    val rarity: Rarity,
    val promoTypes: List<String>,
    val card: Card,
)

/**
 * A card as a game object, independent of printing.
 *
 * [name] is Scryfall's full name, with ` // ` between faces for multi-face cards. [faces] always
 * has at least one entry; for a single-faced card it is the card itself. [rarity] is the rarity of
 * the first printing loaded, which is the main set's when an index loads a set and its companions.
 */
data class Card(
    val oracleId: String,
    val name: String,
    val layout: String,
    val faces: List<Face>,
    val colors: Set<Color>,
    val colorIdentity: Set<Color>,
    val manaValue: Double,
    val typeLine: String,
    val isCreature: Boolean,
    val isLand: Boolean,
    val isBasicLand: Boolean,
    val rarity: Rarity,
)

/**
 * One face of a card. [manaCost] and [oracleText] are empty when the face has none; [power] and
 * [toughness] are null when it has none, and are strings because they may be `*` or `1+*`.
 */
data class Face(
    val name: String,
    val manaCost: String,
    val manaValue: Double,
    val colors: Set<Color>,
    val typeLine: String,
    val oracleText: String,
    val power: String?,
    val toughness: String?,
)
