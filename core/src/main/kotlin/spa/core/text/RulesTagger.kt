package spa.core.text

import java.util.Locale

/**
 * One named rules-text pattern in [RulesTagger.patterns].
 *
 * @property name a stable name for the pattern, unique within [RulesTagger.patterns].
 * @property tag the tag the pattern sets.
 * @property text matched against oracle text after [normalizeRulesText].
 * @property typeLine when not null, must also match the type line after [normalizeRulesText].
 */
data class TagPattern(
    val name: String,
    val tag: Tag,
    val text: Regex,
    val typeLine: Regex? = null,
) {
    /**
     * Whether this pattern matches. Both arguments must already be normalized by
     * [normalizeRulesText]: lower case, with reminder text removed.
     */
    fun matches(normalizedTypeLine: String, normalizedText: String): Boolean =
        text.containsMatchIn(normalizedText) &&
            (typeLine == null || typeLine.containsMatchIn(normalizedTypeLine))
}

/**
 * Tags a card from its Scryfall `type_line` and `oracle_text` with what it does, using a table of
 * named regular expressions plus [ManaProduction] for [Tag.FIXING].
 */
object RulesTagger {
    private const val FIXING_MIN_COLORS = 2

    // Up to three words between "target" and the noun: "target attacking or blocking creature".
    private const val MODIFIERS = "(?:[a-z,'-]+ ){0,3}?"
    private const val COUNT = "(?:up to (?:one|two|three|x) )?(?:another )?"

    // A target creature or permanent on the battlefield, never a card in a graveyard or hand.
    private const val TARGET = "${COUNT}target $MODIFIERS(?:creature|permanent)s?\\b(?! cards?\\b)"

    // As TARGET, but not one of your own: "exile target creature you control" is a flicker.
    private const val THEIR_TARGET = "$TARGET(?! you control\\b)"
    private const val TARGET_CREATURE = "\\btarget ${MODIFIERS}creatures? (?:[^.\\n]*? )?"
    private const val SHRINK = "(?:[2-9]|[1-9]\\d+|x)"
    private const val PUMP = "(?:[1-9]\\d*|x)"
    private const val PUMP_OR_ZERO = "(?:\\d+|x)"
    private const val NOT_SORCERY = "^(?!.*\\bsorcery\\b)"

    private fun rx(pattern: String) = Regex(pattern)

    /** The tagging table, in a fixed order. Every [TagPattern.name] is unique. */
    val patterns: List<TagPattern> =
        listOf(
            TagPattern("destroy", Tag.REMOVAL, rx("\\bdestroys? $THEIR_TARGET")),
            TagPattern("exile", Tag.REMOVAL, rx("\\bexiles? $THEIR_TARGET")),
            TagPattern(
                "damage",
                Tag.REMOVAL,
                rx("\\bdeals? [^.\\n]*?\\bdamage\\b[^.\\n]*?\\bto (?:any target|$THEIR_TARGET)"),
            ),
            TagPattern("shrink", Tag.REMOVAL, rx("${TARGET_CREATURE}gets? -$SHRINK/-$SHRINK\\b")),
            TagPattern(
                "fight",
                Tag.REMOVAL,
                rx("\\bfights? $COUNT(?:target $MODIFIERS)creatures?\\b|\\bfight each other\\b"),
            ),
            TagPattern(
                "bite",
                Tag.REMOVAL,
                rx("\\bdeals? damage equal to (?:its|that creature's) power to $THEIR_TARGET"),
            ),
            TagPattern(
                "sacrifice",
                Tag.REMOVAL,
                rx(
                    "\\btarget ${MODIFIERS}creature's controller sacrifices (?:it|that creature)\\b" +
                        "|\\bcontroller of target ${MODIFIERS}creature sacrifices it\\b"
                ),
            ),
            TagPattern(
                "counter",
                Tag.COUNTER,
                rx("\\bcounter (?:up to one )?target $MODIFIERS(?:spell|ability)\\b"),
            ),
            TagPattern(
                "return-to-hand",
                Tag.BOUNCE,
                rx("\\breturn $TARGET[^.\\n]*? to (?:its|their) owner(?:'s|s') hands?\\b"),
            ),
            TagPattern(
                "top-or-bottom",
                Tag.BOUNCE,
                rx(
                    "\\bputs? $TARGET[^.\\n]*? on (?:the )?(?:top|bottom)(?: or bottom)? " +
                        "of (?:its|their) owner(?:'s|s') librar(?:y|ies)\\b"
                ),
            ),
            TagPattern(
                "instant-pump",
                Tag.TRICK,
                rx("${TARGET_CREATURE}gets? \\+$PUMP/\\+$PUMP_OR_ZERO\\b"),
                typeLine = rx("$NOT_SORCERY.*\\binstant\\b"),
            ),
            TagPattern(
                "flash-pump",
                Tag.TRICK,
                rx("(?:\\A|\\n)flash\\b[\\s\\S]*${TARGET_CREATURE}gets? \\+$PUMP/\\+$PUMP\\b"),
                typeLine = rx(NOT_SORCERY),
            ),
        )

    /**
     * The tags for a card with this Scryfall `type_line` and `oracle_text`, in enum order. Matching
     * ignores case and ignores text inside parentheses (reminder text). The card's own name may
     * appear in the text. The same input always gives the same set.
     */
    fun tag(typeLine: String, oracleText: String): Set<Tag> {
        val found = matchingPatterns(typeLine, oracleText).map { it.tag }.toMutableSet()
        if (ManaProduction.colors(typeLine, oracleText).size >= FIXING_MIN_COLORS) {
            found += Tag.FIXING
        }
        return Tag.entries.filter { it in found }.toSet()
    }

    /**
     * The entries of [patterns] that match this Scryfall `type_line` and `oracle_text`, in table
     * order, under the same rules as [tag]. [Tag.FIXING] has no pattern here; it comes from
     * [ManaProduction.colors].
     */
    fun matchingPatterns(typeLine: String, oracleText: String): List<TagPattern> {
        val type = normalizeRulesText(typeLine)
        val text = normalizeRulesText(oracleText)
        return patterns.filter { it.matches(type, text) }
    }
}

private val parenthetical = Regex("\\([^()]*\\)")

/**
 * Rules text as the patterns expect it: text inside parentheses removed (innermost first, so nested
 * parentheses go too), the Unicode minus sign and curly apostrophe replaced with their ASCII forms,
 * and everything lower-cased. Line breaks are kept.
 */
fun normalizeRulesText(text: String): String {
    var current = text
    var previous: String
    do {
        previous = current
        current = parenthetical.replace(current, "")
    } while (current != previous)
    return current.replace('−', '-').replace('’', '\'').lowercase(Locale.ROOT)
}
