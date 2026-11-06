package spa.cli

import java.nio.file.Files
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.agent.review.AnalysisAgents
import spa.agent.review.ReviewConfig
import spa.core.brief.BriefLoader
import spa.core.cards.CardIndex

/**
 * SPA-070 criteria 10 and 11: the committed review config parses, and no prompt names a set or a
 * card.
 */
class ReviewFixturesTest {
    private val ids = AnalysisAgents.ALL.map { it.id }

    @Test
    fun `the committed review config parses with the agents' ids`() {
        val text = Files.readString(Fixtures.root.resolve("agents/review.json"))

        val config = ReviewConfig.parse(text, ids)

        assertEquals("claude-sonnet-5-5", config.model)
        assertEquals(300_000L, config.maxInputTokens)
        assertEquals(60_000L, config.maxOutputTokens)
        assertEquals(ids, config.agents.map { it.id })
    }

    private fun prompts(): Map<String, String> {
        val names =
            listOf(
                "preamble-v1.md",
                "schema-v1.md",
                "explainer-v1.md",
                "explainer-schema-v1.md",
            ) + ids.map { "$it-v1.md" }
        return names.associateWith { name ->
            val stream =
                ReviewFixturesTest::class.java.getResourceAsStream("/spa/agent/review/$name")
            checkNotNull(stream) { "missing prompt $name" }
                .use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    @Test
    fun `no prompt names a set code`() {
        val codes = Regex("""(?i)(?<![\p{L}\p{N}])(trk|msh|hob|fra|sds)(?![\p{L}\p{N}])""")

        val found =
            prompts().mapNotNull { (name, text) -> codes.find(text)?.let { "$name: ${it.value}" } }

        assertTrue(found.isEmpty(), "set codes in prompts: $found")
    }

    @Test
    fun `no prompt names a set`() {
        val names =
            listOf("trk", "msh", "hob", "fra").map {
                BriefLoader.parse(Files.readString(Fixtures.root.resolve("briefs/$it.json")))
                    .setName
            }

        val found =
            prompts().flatMap { (name, text) ->
                names.filter { wholeWord(text, it, ignoreCase = true) }.map { "$name: $it" }
            }

        assertTrue(found.isEmpty(), "set names in prompts: $found")
    }

    @Test
    fun `no prompt names a card of the four sets`() {
        val cards =
            listOf("trk", "msh", "hob", "fra")
                .flatMap { set ->
                    val index = CardIndex.load(Fixtures.root.resolve("scryfall"), set)
                    index.printings(set).map { it.card }.filter { !it.isLand }
                }
                .flatMap { card -> card.faces.map { it.name } + card.name }
                .filter { it.trim().contains(' ') }
                .toSet()

        val text = prompts()
        val found = text.flatMap { (name, body) ->
            cards.filter { wholeWord(body, it, ignoreCase = false) }.map { "$name: $it" }
        }

        assertTrue(cards.size > 100, "read ${cards.size} card names")
        assertTrue(found.isEmpty(), "card names in prompts: $found")
    }

    private fun wholeWord(text: String, word: String, ignoreCase: Boolean): Boolean {
        val flags = if (ignoreCase) Pattern.CASE_INSENSITIVE else 0
        val pattern =
            Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])",
                flags,
            )
        return pattern.matcher(text).find()
    }
}
