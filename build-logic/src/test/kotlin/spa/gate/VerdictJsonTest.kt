package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class VerdictJsonTest {
    private val verdict =
        Verdict(
            ticket = "SPA-003",
            commit = "abc1234",
            result = "fail",
            failures =
                listOf(
                    GateFailure(
                        "unit-tests",
                        "core/src/test/kotlin/spa/core/ManaBoxParserTest.kt",
                        42,
                        "expected 85 cards, got 84",
                        "./gradlew :core:test --tests spa.core.ManaBoxParserTest",
                    ),
                    GateFailure("format", null, null, "no file", "./gradlew check"),
                ),
            steps = listOf(StepReport("ui-tests", "pass", "skipped: no app module")),
        )

    @Test
    fun `writes the fields the gate contract names`() {
        val json = Json.parseToJsonElement(VerdictJson.encode(verdict)).jsonObject
        assertEquals(listOf("ticket", "commit", "result", "failures", "steps"), json.keys.toList())
        assertEquals("SPA-003", json.getValue("ticket").jsonPrimitive.content)
        assertEquals("abc1234", json.getValue("commit").jsonPrimitive.content)
        assertEquals("fail", json.getValue("result").jsonPrimitive.content)
        val first = json.getValue("failures").jsonArray[0].jsonObject
        assertEquals(listOf("step", "file", "line", "message", "reproduce"), first.keys.toList())
        assertEquals(42, first.getValue("line").jsonPrimitive.int)
    }

    @Test
    fun `unknown file and line are written as null, not left out`() {
        val second =
            Json.parseToJsonElement(VerdictJson.encode(verdict))
                .jsonObject
                .getValue("failures")
                .jsonArray[1]
                .jsonObject
        assertEquals(JsonNull, second.getValue("file"))
        assertEquals(JsonNull, second.getValue("line"))
    }

    @Test
    fun `round-trips`() {
        assertEquals(verdict, VerdictJson.decode(VerdictJson.encode(verdict)))
    }
}
