package spa.agent.tags

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** The prompt version is a parameter: each version is a resource file, found by name. */
class TagPromptsTest {
    @Test
    fun `version 1 of both prompts is on the classpath`() {
        val axes = assertNotNull(TagPrompts.load(TagPrompts.Pass.AXES, "v1"))
        val roles = assertNotNull(TagPrompts.load(TagPrompts.Pass.ROLES, "v1"))

        assertTrue(axes.startsWith("You are reading a Magic: The Gathering set you have never"))
        assertTrue(roles.startsWith("You are tagging every card of a Magic: The Gathering set"))
        assertTrue('\r' !in axes && '\r' !in roles)
    }

    /**
     * SPA-048 criterion 5 (an SPA-047 follow-up): both v1 prompts, word for word as SPA-047's
     * Background quotes them, with a final line feed.
     */
    @Test
    fun `version 1 of both prompts is the full text SPA-047 gives`() {
        assertEquals(AXES_V1, TagPrompts.load(TagPrompts.Pass.AXES, "v1"))
        assertEquals(ROLES_V1, TagPrompts.load(TagPrompts.Pass.ROLES, "v1"))
    }

    @Test
    fun `a version with no file gives null`() {
        assertNull(TagPrompts.load(TagPrompts.Pass.AXES, "v9"))
        assertNull(TagPrompts.load(TagPrompts.Pass.ROLES, "v9"))
    }

    @Test
    fun `a version that is not a plain name gives null`() {
        assertNull(TagPrompts.load(TagPrompts.Pass.AXES, "../grader/prompt-v1"))
        assertEquals(
            "/spa/agent/tags/tags-roles-v2.md",
            TagPrompts.resource(TagPrompts.Pass.ROLES, "v2"),
        )
    }
}

private const val AXES_V1 =
    "You are reading a Magic: The Gathering set you have never seen played, to find its " +
        "synergies for Sealed. Read every card file in this folder first. Then name the set's " +
        "synergy axes: the things some cards supply and other cards reward, such as a kind of " +
        "token, a counter, a card type, a creature type, sacrificing, a graveyard or a mechanic " +
        "of this set. Give between 3 and 15 axes. Include a creature type or card type only if " +
        "at least three cards reward it. Leave out general strengths that every set has, such " +
        "as removal, card draw or big creatures; they are not synergy. For each axis give an id " +
        "(lowercase words joined by hyphens), a short name, and a one-line definition of what " +
        "counts as supplying it. Use only the files in this folder. Do not use the web or any " +
        "tool other than reading these files and writing `axes.response.json`, which must " +
        "match `axes.schema.json`.\n"

private const val ROLES_V1 =
    "You are tagging every card of a Magic: The Gathering set you have never seen played, for " +
        "Sealed synergy. The set's axes are listed below. Read every card file in this folder " +
        "first. For each card, list its role on each axis it touches.\n" +
        "- **enabler**: it supplies the axis. Strength 1 is incidental, 2 is a clear supplier, " +
        "and 3 is a core supplier (repeatable, or several at once).\n" +
        "- **payoff**: it rewards the axis. Strength 1 is a small bonus, 2 means it is clearly " +
        "better with the axis, and 3 means it is built around the axis.\n" +
        "\n" +
        "A card can be both on the same axis. Most cards touch no axis or one; do not stretch. " +
        "Then list up to 5 named partners: specific cards in this set that this card works with " +
        "beyond the shared axes, such as a card that names another, or two cards that form a " +
        "combo, each with a strength from 1 to 3. A card may name itself when its copies help " +
        "each other beyond the axes. Give every card exactly once, in its own file's response, " +
        "matching `response.schema.json`. Use only the files in this folder. Do not use the web " +
        "or any tool other than reading these files and writing the response files.\n"
