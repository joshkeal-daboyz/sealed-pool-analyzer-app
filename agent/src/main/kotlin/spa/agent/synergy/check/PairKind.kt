package spa.agent.synergy.check

import kotlinx.serialization.json.JsonObject

/** The `pair` kind: passes when `score(a, b) >= min`. */
object PairKind : CheckKind {
    override val kind = "pair"

    override fun parse(check: JsonObject): KindCheck = TODO()
}
