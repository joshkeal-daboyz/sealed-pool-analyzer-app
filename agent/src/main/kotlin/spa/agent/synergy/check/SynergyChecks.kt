package spa.agent.synergy.check

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import spa.agent.synergy.SynergyTable

/** One owner check: its [id], its [status] (`independent` or `seen`), and its parsed [check]. */
data class SynergyCheck(val id: String, val status: String, val check: KindCheck) {
    /** True for a check no fix has seen. */
    val isIndependent: Boolean
        get() = status == INDEPENDENT

    companion object {
        const val INDEPENDENT = "independent"
        const val SEEN = "seen"
    }
}

/** A set's owner check file, `fixtures/synergy-checks/<code>.json`: its [set] and [checks]. */
data class SynergyCheckFile(val set: String, val checks: List<SynergyCheck>)

/**
 * Reads an owner check file (`fixtures/synergy-checks/README.md`) and runs its checks on a set's
 * pair table. Model-free. Every problem is an [IllegalArgumentException] with a one-line message.
 */
object SynergyChecks {
    private const val SEP = "  "

    /**
     * The check file in [text]. Each check's `id`, `status` and `kind` are read here, and the rest
     * by the kind in [kinds] whose [CheckKind.kind] it names; an unknown kind is an error.
     */
    fun parse(text: String, kinds: List<CheckKind> = CheckKinds.ALL): SynergyCheckFile {
        val element =
            try {
                Json.parseToJsonElement(text)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("not JSON: ${e.message?.lineSequence()?.first()}", e)
            }
        require(element is JsonObject) { "expected a JSON object" }
        val set = CheckFields(element).string("set")
        val array = element["checks"] as? JsonArray
        requireNotNull(array) { "'checks' must be an array" }
        val byKind = kinds.associateBy { it.kind }
        val checks = array.mapIndexed { index, item ->
            require(item is JsonObject) { "check ${index + 1} must be an object" }
            check(item, index, byKind)
        }
        val repeated = checks.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(repeated.isEmpty()) { "repeated check id: " + repeated.joinToString(", ") }
        return SynergyCheckFile(set, checks)
    }

    private fun check(item: JsonObject, index: Int, kinds: Map<String, CheckKind>): SynergyCheck {
        val fields = CheckFields(item)
        val id = prefixed("check ${index + 1}") { fields.string("id") }
        return prefixed("check '$id'") {
            val status = fields.string("status")
            require(status == SynergyCheck.INDEPENDENT || status == SynergyCheck.SEEN) {
                "status must be '${SynergyCheck.INDEPENDENT}' or '${SynergyCheck.SEEN}', " +
                    "got '$status'"
            }
            val name = fields.string("kind")
            val kind = requireNotNull(kinds[name]) { "unknown kind '$name'" }
            SynergyCheck(id, status, kind.parse(item))
        }
    }

    /**
     * One line per check of [file] in file order, `PASS` or `FAIL`, the id, the status, then its
     * numbers, and last `<passed> of <total> checks pass (<k> independent)`, where k counts the
     * passing checks that are independent. Every card id is checked against [table] before any
     * check runs.
     */
    fun lines(file: SynergyCheckFile, table: SynergyTable): List<String> {
        file.checks.forEach { check ->
            val missing = check.check.cardIds.distinct().filter { table.card(it) == null }
            require(missing.isEmpty()) {
                "check '${check.id}': not in the tag file for set '${table.tags.set}': " +
                    missing.joinToString(", ")
            }
        }
        val results = file.checks.map { it to it.check.run(table) }
        val lines = results.map { (check, result) ->
            val head = "${if (result.passed) "PASS" else "FAIL"} ${check.id} ${check.status}"
            (listOf(head) + result.numbers).joinToString(SEP)
        }
        val passed = results.filter { it.second.passed }.map { it.first }
        val summary =
            "${passed.size} of ${results.size} checks pass " +
                "(${passed.count { it.isIndependent }} independent)"
        return lines + summary
    }

    private fun <T> prefixed(what: String, block: () -> T): T =
        try {
            block()
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$what: ${e.message}", e)
        }
}
