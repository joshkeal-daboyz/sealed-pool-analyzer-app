package spa.agent.tags

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * SPA-051 criterion 3: both v2 prompts, word for word as SPA-051's Background gives them, with a
 * final line feed, and what each version's axes files look like.
 */
class TagPromptsV2Test {
    @Test
    fun `version 2 of the axes prompt is v1's text with 20 axes and the facts paragraph`() {
        assertEquals(AXES_V2, TagPrompts.load(TagPrompts.Pass.AXES, "v2"))
    }

    @Test
    fun `version 2 of the roles prompt is v1's text with the supply sentence`() {
        assertEquals(ROLES_V2, TagPrompts.load(TagPrompts.Pass.ROLES, "v2"))
    }

    @Test
    fun `the v2 texts differ from v1 only where the ticket says`() {
        val axesV1 = TagPrompts.load(TagPrompts.Pass.AXES, "v1")!!
        val rolesV1 = TagPrompts.load(TagPrompts.Pass.ROLES, "v1")!!
        val use = "Use only the files in this folder."

        assertEquals(
            axesV1
                .replace("between 3 and 15 axes", "between 3 and 20 axes")
                .replaceFirst(" $use", "\n\n$FACTS_PARAGRAPH\n\n$use"),
            AXES_V2,
        )
        val both = "\n\nA card can be both"
        assertEquals(rolesV1.replaceFirst(both, "\n\n$SUPPLY_SENTENCE$both"), ROLES_V2)
    }

    @Test
    fun `the default version is v2, and v1 alone keeps the plain axes format`() {
        assertEquals("v2", TagPrompts.DEFAULT_VERSION)
        assertEquals(AxesFormat.PLAIN, TagPrompts.format("v1"))
        assertEquals(AxesFormat.SUPPLIED, TagPrompts.format("v2"))
        assertEquals(15, AxesFormat.PLAIN.maxAxes)
        assertEquals(20, AxesFormat.SUPPLIED.maxAxes)
    }
}

private const val FACTS_PARAGRAPH =
    "Each card also has facts, listed with their counts under \"Card facts\" below and per card " +
        "in `facts.json`: what it is (a card type, legendary, its creature types), its keywords " +
        "(`kw:`), whether it is cheap (mana value 2 or less), and whether it has its own enters " +
        "trigger, dies trigger or makes tokens. For each axis, list under `suppliedBy` the facts " +
        "that supply it just by a card having them, each with a strength from 1 to 3: a fact " +
        "supplies an axis when every card with that fact feeds the axis by being played " +
        "normally, such as every land feeding an axis about lands entering, or every creature " +
        "feeding an axis about creatures dying. Leave `suppliedBy` empty when no fact supplies " +
        "the axis on its own; cards that supply it in other ways are tagged in the next pass."

private const val SUPPLY_SENTENCE =
    "Each axis's `suppliedBy` facts already count as supply for every card that has them, so " +
        "tag a card as an enabler only when it supplies the axis beyond its facts, or more " +
        "strongly than they do."

private const val AXES_V2 =
    "You are reading a Magic: The Gathering set you have never seen played, to find its " +
        "synergies for Sealed. Read every card file in this folder first. Then name the set's " +
        "synergy axes: the things some cards supply and other cards reward, such as a kind of " +
        "token, a counter, a card type, a creature type, sacrificing, a graveyard or a mechanic " +
        "of this set. Give between 3 and 20 axes. Include a creature type or card type only if " +
        "at least three cards reward it. Leave out general strengths that every set has, such " +
        "as removal, card draw or big creatures; they are not synergy. For each axis give an id " +
        "(lowercase words joined by hyphens), a short name, and a one-line definition of what " +
        "counts as supplying it.\n" +
        "\n" +
        FACTS_PARAGRAPH +
        "\n" +
        "\n" +
        "Use only the files in this folder. " +
        "Do not use the web or any tool other than reading these files and writing " +
        "`axes.response.json`, which must match `axes.schema.json`.\n"

private const val ROLES_V2 =
    "You are tagging every card of a Magic: The Gathering set you have never seen played, for " +
        "Sealed synergy. The set's axes are listed below. Read every card file in this folder " +
        "first. For each card, list its role on each axis it touches.\n" +
        "- **enabler**: it supplies the axis. Strength 1 is incidental, 2 is a clear supplier, " +
        "and 3 is a core supplier (repeatable, or several at once).\n" +
        "- **payoff**: it rewards the axis. Strength 1 is a small bonus, 2 means it is clearly " +
        "better with the axis, and 3 means it is built around the axis.\n" +
        "\n" +
        SUPPLY_SENTENCE +
        "\n" +
        "\n" +
        "A card can be both on the same axis. Most cards touch no axis or one; do not stretch. " +
        "Then list up to 5 named partners: specific cards in this set that this card works with " +
        "beyond the shared axes, such as a card that names another, or two cards that form a " +
        "combo, each with a strength from 1 to 3. A card may name itself when its copies help " +
        "each other beyond the axes. Give every card exactly once, in its own file's response, " +
        "matching `response.schema.json`. Use only the files in this folder. Do not use the web " +
        "or any tool other than reading these files and writing the response files.\n"
