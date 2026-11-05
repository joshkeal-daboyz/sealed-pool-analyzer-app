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
