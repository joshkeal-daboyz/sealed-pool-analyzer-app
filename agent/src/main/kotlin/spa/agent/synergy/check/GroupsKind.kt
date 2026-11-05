package spa.agent.synergy.check

import kotlinx.serialization.json.JsonObject

/** The `groups` kind: more than `share` of the pairs across two groups score at least `min`. */
object GroupsKind : CheckKind {
    override val kind = "groups"

    override fun parse(check: JsonObject): KindCheck = TODO()
}
