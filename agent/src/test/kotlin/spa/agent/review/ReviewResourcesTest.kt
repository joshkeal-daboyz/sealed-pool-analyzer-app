package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewResourcesTest {
    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/spa/agent/review/$name")) { "no $name" }
            .readBytes()
            .toString(Charsets.UTF_8)

    @Test
    fun `the preamble file holds the specified text`() {
        val expected =
            """
            You are one specialist in a team reviewing a Magic: The Gathering limited card pool for a player. You answer one question, set out below. You work only from the pool bundle and the number table you are given. You do not know the other specialists' answers.

            Rules:
            1. Cite cards. Every finding names the pool cards it is about, by their exact names as written in the bundle. A finding with no card, or a card not in the pool, is thrown away.
            2. Quote numbers by key. When a finding rests on a number, give the number's key from the number table and the value the table shows. Do not write a number from memory or compute one yourself; if the table has no number for it, say it in words without a number. A finding whose key or value does not match the table is thrown away.
            3. Compare with the set. The table shows each number beside what a typical pool from this set holds. Say whether the pool is above, below or typical for the set, and do not call a number high or low without that comparison.
            4. Be specific and short. At most 8 findings. Each claim is one or two sentences a player can act on. Prefer the findings that would change what the player builds.
            5. Say how sure you are. `confidence` is 0 to 1 for the whole answer; lower it when the pool or the numbers give you little to go on.
            6. Answer with one JSON object and nothing else, in the schema below.
            """
                .trimIndent() + "\n"
        assertEquals(expected, resource("preamble-v1.md"))
    }

    @Test
    fun `the schema file holds the specified text`() {
        val expected =
            """
            Answer with one JSON object and nothing else:
            {
              "agent": "<your agent id>",
              "confidence": <number from 0 to 1>,
              "summary": "<two sentences at most>",
              "findings": [
                {
                  "claim": "<one or two sentences>",
                  "pair": "<two colour letters such as WU, or null>",
                  "severity": "info" | "warn" | "key",
                  "cards": ["<exact card name>", ...],
                  "numbers": [ { "key": "<key from the number table>", "value": <the value the table shows> } ]
                }
              ]
            }
            severity: key = changes what the player should build, warn = a problem or risk, info = a fact worth knowing.
            """
                .trimIndent() + "\n"
        assertEquals(expected, resource("schema-v1.md"))
    }

    @Test
    fun `the explainer prompt file holds the specified text`() {
        val expected =
            """
            You are the Explainer for a team of specialists who reviewed a Magic: The Gathering limited card pool for a player. You are given the three best decks that code built from the pool, ranked by code, and the specialists' findings. Write the review's summary for the player.

            Rules:
            1. Do not change the ranking. The decks are ranked by a score that code computed. Explain it; do not reorder or replace a deck.
            2. Restate, do not invent. Every claim you make rests on a finding you were given or on the decks as printed. Do not add numbers; if you quote one, copy it from a finding.
            3. Cite cards. Every note names the pool cards it is about, by their exact names. A note with no card, or a card not in the pool, is thrown away.
            4. The verdict is for a player at a table: say which deck to register and why, in at most four sentences, then the one or two things to watch.
            5. Report disagreements. When two specialists' findings pull against each other (one wants a color, another says it is thin), list the disagreement with both agents' ids, the cards involved and what decides it. Do not average them away. If there is no disagreement, return an empty list.
            6. Answer with one JSON object and nothing else, in the schema below.
            """
                .trimIndent() + "\n"
        assertEquals(expected, resource("explainer-v1.md"))
    }

    @Test
    fun `the explainer schema file holds the specified text`() {
        val expected =
            """
            Answer with one JSON object and nothing else:
            {
              "confidence": <number from 0 to 1>,
              "verdict": "<at most four sentences>",
              "decks": [ { "rank": <1, 2 or 3>, "note": "<one or two sentences>", "cards": ["<exact card name>", ...] } ],
              "disagreements": [ { "topic": "<short>", "agents": ["<agent id>", "<agent id>"], "cards": ["<exact card name>", ...], "note": "<one or two sentences>" } ]
            }
            """
                .trimIndent() + "\n"
        assertEquals(expected, resource("explainer-schema-v1.md"))
    }

    @Test
    fun `the agent list holds the seven analysts`() {
        assertEquals(
            listOf(
                "colour-depth",
                "curve",
                "mana",
                "interaction",
                "bombs",
                "archetypes",
                "synergy",
            ),
            AnalysisAgents.ALL.map { it.id },
        )
    }
}
