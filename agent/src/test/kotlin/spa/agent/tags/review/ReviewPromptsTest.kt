package spa.agent.tags.review

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/** SPA-052 criterion 1: the review prompt v1 is the Architect's text word for word. */
class ReviewPromptsTest {
    @Test
    fun `review prompt v1 matches the given text exactly, with a final line feed`() {
        assertEquals("$REVIEW_V1\n", ReviewPrompts.load("v1"))
    }

    @Test
    fun `v1 is the default, and a version with no file or a bad name gives null`() {
        assertEquals("v1", ReviewPrompts.DEFAULT_VERSION)
        assertEquals("/spa/agent/tags/tags-review-v1.md", ReviewPrompts.resource("v1"))
        assertNull(ReviewPrompts.load("v9"))
        assertNull(ReviewPrompts.load("../tags-roles-v1"))
    }
}

/** The text of SPA-052's Background, "The prompt", copied word for word. */
internal const val REVIEW_V1 =
    "You are reviewing the synergy tags of a Magic: The Gathering set you have never seen " +
        "played. The set's axes, and the facts that supply each axis, are listed below; each " +
        "card's facts are in `facts.json`, and the current tags for each card file are in the " +
        "`tags-NN.json` beside it. Read every card file and its tags. For each card, check its " +
        "roles and partners against its rules text: add a role the card clearly has but lacks, " +
        "remove a role it does not have, change a strength that is clearly wrong, and add or " +
        "remove a named partner. A card's facts already supply the axes listed for them, so do " +
        "not add an enabler role that only repeats a fact. Make a correction only when you are " +
        "confident; most cards need none. Give each correction a one-line reason. If several " +
        "cards should share a fact that the fact list lacks, describe it in " +
        "`fact-suggestions.json`, with example cards. Write a `cards-NN.review.json` for every " +
        "card file, even when it is empty. Use only the files in this folder. Do not use the " +
        "web or any tool other than reading these files and writing the response files."
