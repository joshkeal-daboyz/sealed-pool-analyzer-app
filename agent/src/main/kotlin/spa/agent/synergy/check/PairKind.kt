package spa.agent.synergy.check

import kotlinx.serialization.json.JsonObject
import spa.agent.synergy.SynergyTable

/**
 * The `pair` kind: passes when `score(a, b) >= min`. Its numbers are the score and each term's part
 * by name (`score 3 axis 0 partner 3`).
 */
object PairKind : CheckKind {
    override val kind = "pair"

    override fun parse(check: JsonObject): KindCheck {
        val fields = CheckFields(check)
        return PairCheck(fields.string("a"), fields.string("b"), fields.int("min"))
    }

    private class PairCheck(val a: String, val b: String, val min: Int) : KindCheck {
        override val cardIds = listOf(a, b)

        override fun run(table: SynergyTable): CheckResult {
            val parts = table.parts(a, b)
            val score = parts.sum()
            val named = table.terms.zip(parts).map { (term, part) -> "${term.name} $part" }
            return CheckResult(score >= min, listOf("score $score") + named)
        }
    }
}
