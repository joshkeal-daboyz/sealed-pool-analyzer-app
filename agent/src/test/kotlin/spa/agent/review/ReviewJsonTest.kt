package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** SPA-069 criterion 5: the full structured review as JSON. */
class ReviewJsonTest {
    private val topKeys =
        listOf(
            "set",
            "setName",
            "pool",
            "decks",
            "verdict",
            "agents",
            "disagreements",
            "missing",
            "usage",
            "model",
            "attribution",
        )

    private val expected =
        """
        {
          "set": "xyz",
          "setName": "Test Set",
          "pool": {
            "totalCards": 45,
            "distinct": 30,
            "unresolved": 2,
            "kitWarnings": ["Card count 45 is outside 80 to 100.", "Too many rares."]
          },
          "decks": [
            {
              "rank": 1,
              "colours": "WU",
              "sameAs": ["WB"],
              "score": 12.5,
              "spells": [{"name": "Bear", "count": 2}, {"name": "Envoy", "count": 1}],
              "landCount": 17,
              "lands": [
                {"name": "Dual Land", "count": 1},
                {"name": "Plains", "count": 8},
                {"name": "Island", "count": 8}
              ],
              "why": "grades +10.00: strong; curve -1.50: light"
            },
            {
              "rank": 2,
              "colours": "WB",
              "sameAs": [],
              "score": 9.0,
              "spells": [{"name": "Bear", "count": 1}],
              "landCount": 17,
              "lands": [{"name": "Plains", "count": 9}, {"name": "Swamp", "count": 8}],
              "why": ""
            }
          ],
          "verdict": {
            "status": "explained",
            "text": "Register deck one.",
            "confidence": 0.7,
            "deckNotes": [{"rank": 1, "note": "Solid.", "cards": ["Bear", "Envoy"]}],
            "dropped": [
              {"kind": "deck", "reason": "unknown rank", "text": "Odd."},
              {"kind": "deck", "reason": "no card", "text": "Bare."}
            ]
          },
          "agents": [
            {
              "id": "fake-a",
              "title": "Title of fake-a",
              "status": "answered",
              "confidence": 0.8,
              "summary": "Curve is thin.",
              "findings": [
                {
                  "severity": "key",
                  "pair": "WU",
                  "claim": "Few two drops.",
                  "cards": ["Bear"],
                  "numbers": [
                    {
                      "key": "/curve/twoDrops",
                      "value": 5.0,
                      "typical": {"median": 4.0, "p10": 2.0, "p90": 6.0, "mean": 4.0}
                    },
                    {"key": "/removal/count", "value": 2.0, "typical": null}
                  ]
                },
                {
                  "severity": "info",
                  "pair": null,
                  "claim": "Envoys are fine.",
                  "cards": ["Envoy"],
                  "numbers": []
                }
              ],
              "dropped": [
                {"reason": "no card", "claim": "Nobody."},
                {"reason": "wrong number value", "claim": "Seven."},
                {"reason": "no card", "claim": "Nothing."}
              ]
            },
            {
              "id": "fake-b",
              "title": "Title of fake-b",
              "status": "answered",
              "confidence": 0.5,
              "summary": "Nothing to say.",
              "findings": [],
              "dropped": []
            },
            {
              "id": "fake-c",
              "title": "Title of fake-c",
              "status": "missing",
              "reason": "${ReviewTestData.INVALID}"
            }
          ],
          "disagreements": [
            {
              "topic": "Blue depth",
              "agents": ["fake-a", "fake-b"],
              "cards": ["Envoy"],
              "note": "Split."
            }
          ],
          "missing": [{"id": "fake-c", "reason": "${ReviewTestData.INVALID}"}],
          "usage": {"calls": 7, "input": 1000, "output": 200, "cacheRead": 300, "cacheWrite": 400},
          "model": "claude-test",
          "attribution": "${ReviewTestData.ATTRIBUTION}"
        }
        """
            .trimIndent()

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `the JSON has the keys in order and the values of the review`() {
        val text = ReviewJson.render(ReviewTestData.review)
        val root = parse(text)

        assertEquals(topKeys, root.keys.toList())
        assertEquals(parse(expected), root)
        assertTrue(text.endsWith("}\n"))
        assertTrue(text.lines().size > 100, "the JSON is pretty-printed")
    }

    @Test
    fun `the same review gives the same bytes twice`() {
        assertEquals(
            ReviewJson.render(ReviewTestData.review),
            ReviewJson.render(ReviewTestData.review),
        )
    }

    @Test
    fun `a missing Explainer gives a reason and no text`() {
        val root = parse(ReviewJson.render(ReviewTestData.bare))

        assertEquals(
            parse("""{"status": "missing", "reason": "no findings to explain"}"""),
            root.getValue("verdict"),
        )
        assertEquals(JsonArray(emptyList()), root.getValue("decks"))
        assertEquals(JsonArray(emptyList()), root.getValue("agents"))
        assertEquals(
            parse("""{"id": "explainer", "reason": "no findings to explain"}"""),
            (root.getValue("missing") as JsonArray).single(),
        )
    }
}
