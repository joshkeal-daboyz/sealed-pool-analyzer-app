package spa.agent.synergy.check

import kotlinx.serialization.json.JsonObject
import spa.agent.synergy.SynergyTable

/**
 * One kind of owner synergy check (`fixtures/synergy-checks/README.md`): it reads its own fields of
 * a check and turns them into a [KindCheck]. A new kind is one class and one entry in
 * [CheckKinds.ALL].
 */
interface CheckKind {
    /** The check file's `kind` value this class reads. */
    val kind: String

    /**
     * The check in [check], the whole check object; a missing or bad field is an
     * [IllegalArgumentException] with a one-line message.
     */
    fun parse(check: JsonObject): KindCheck
}

/** One parsed check of some [CheckKind]: the cards it names, and how it runs on a pair table. */
interface KindCheck {
    /** Every card id the check names, each of which must be in the set's tag file. */
    val cardIds: List<String>

    /** The check's result on [table]. */
    fun run(table: SynergyTable): CheckResult
}

/** A check's outcome: whether it [passed], and its [numbers], each printed as one field. */
data class CheckResult(val passed: Boolean, val numbers: List<String>)

/** The check kinds `spa synergy-check` knows, keyed by [CheckKind.kind]. */
object CheckKinds {
    val ALL: List<CheckKind> = listOf(PairKind, GroupsKind)
}
