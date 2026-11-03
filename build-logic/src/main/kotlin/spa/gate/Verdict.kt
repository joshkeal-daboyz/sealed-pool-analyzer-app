package spa.gate

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One thing the gate found wrong. `file` is repository-relative; `line` is null when unknown. */
@Serializable
data class GateFailure(
    val step: String,
    val file: String?,
    val line: Int?,
    val message: String,
    val reproduce: String,
)

/** What one step did: `pass`, `fail`, or `not_run` (an earlier step failed first). */
@Serializable data class StepReport(val step: String, val result: String, val note: String?)

/** The contents of `gate-verdict.json`. */
@Serializable
data class Verdict(
    val ticket: String,
    val commit: String,
    val result: String,
    val failures: List<GateFailure>,
    val steps: List<StepReport>,
)

object VerdictJson {
    const val PASS = "pass"
    const val FAIL = "fail"
    const val NOT_RUN = "not_run"

    private val json = Json {
        prettyPrint = true
        explicitNulls = true
    }

    fun encode(verdict: Verdict): String = json.encodeToString(Verdict.serializer(), verdict) + "\n"

    fun decode(text: String): Verdict = json.decodeFromString(Verdict.serializer(), text)
}
