package spa.core.text

/**
 * What a card does, read from its rules text by [RulesTagger]. A tag says what a card does, not how
 * well it does it.
 *
 * @property isInteraction true for the tags that answer or disrupt an opponent's play at instant
 *   speed or in response: [COUNTER], [BOUNCE], and [TRICK].
 */
enum class Tag(val isInteraction: Boolean) {
    /** Destroys, exiles, damages, shrinks, fights, or edicts a target creature or permanent. */
    REMOVAL(false),

    /** Counters a target spell or ability. */
    COUNTER(true),

    /** Returns a target creature or permanent to its owner's hand or library. */
    BOUNCE(true),

    /** A combat trick: an instant, or a card with flash, that pumps a target creature. */
    TRICK(true),

    /** Produces two or more colors of mana. */
    FIXING(false),
}
