package spa.agent.synergy.check

import spa.agent.synergy.SynergyTable

/** One owner check: its [id], its [status] (`independent` or `seen`), and its parsed [check]. */
data class SynergyCheck(val id: String, val status: String, val check: KindCheck)

/** A set's owner check file, `fixtures/synergy-checks/<code>.json`: its [set] and [checks]. */
data class SynergyCheckFile(val set: String, val checks: List<SynergyCheck>)

/** Reads an owner check file and runs its checks on a set's pair table. */
object SynergyChecks {
    fun parse(text: String, kinds: List<CheckKind> = CheckKinds.ALL): SynergyCheckFile = TODO()

    fun lines(file: SynergyCheckFile, table: SynergyTable): List<String> = TODO()
}
