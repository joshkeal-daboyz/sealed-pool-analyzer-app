package spa.core.cards

/** A printing's rarity, named as Scryfall names it. */
enum class Rarity(val scryfallName: String) {
    COMMON("common"),
    UNCOMMON("uncommon"),
    RARE("rare"),
    MYTHIC("mythic"),
    SPECIAL("special"),
    BONUS("bonus");

    companion object {
        /** The rarity Scryfall calls [name]; an unknown name is an error, not a guess. */
        fun fromScryfall(name: String): Rarity =
            requireNotNull(entries.firstOrNull { it.scryfallName == name }) {
                "Unknown Scryfall rarity '$name'."
            }
    }
}
