package spa.agent.synergy.check

import java.util.Locale
import kotlinx.serialization.json.JsonObject
import spa.agent.synergy.SynergyTable

/**
 * The `groups` kind: over every pair (a in `groupA`, b in `groupB`, a ≠ b), it counts the pairs
 * with `score >= min`, and passes when that count over the pair count is greater than `share`
 * (equal fails; no pairs fails). Its numbers are the pair count, the count meeting `min`, and the
 * share and the mean score to 4 decimals (`590 pairs 524 meeting min 1 share 0.8881 mean 1.7000`).
 */
object GroupsKind : CheckKind {
    override val kind = "groups"

    override fun parse(check: JsonObject): KindCheck {
        val fields = CheckFields(check)
        return Groups(
            fields.cardIds("groupA"),
            fields.cardIds("groupB"),
            fields.number("share"),
            fields.int("min"),
        )
    }

    private class Groups(
        val groupA: List<String>,
        val groupB: List<String>,
        val share: Double,
        val min: Int,
    ) : KindCheck {
        override val cardIds = groupA + groupB

        override fun run(table: SynergyTable): CheckResult {
            val scores = groupA.flatMap { a ->
                groupB.filter { it != a }.map { b -> table.score(a, b) }
            }
            val meeting = scores.count { it >= min }
            val ratio = if (scores.isEmpty()) 0.0 else meeting.toDouble() / scores.size
            val mean = if (scores.isEmpty()) 0.0 else scores.sum().toDouble() / scores.size
            return CheckResult(
                scores.isNotEmpty() && ratio > share,
                listOf(
                    "${scores.size} pairs",
                    "$meeting meeting min $min",
                    "share ${decimals(ratio)}",
                    "mean ${decimals(mean)}",
                ),
            )
        }

        private fun decimals(value: Double) = String.format(Locale.ROOT, "%.4f", value)
    }
}
