package spa.gate

/** Resolves which ticket a gate run is for. */
object TicketId {
    const val NONE = "none"

    private val pattern = Regex("SPA-\\d{3}")

    /**
     * `--ticket` wins, then `GATE_TICKET`, then the first `SPA-` plus three digits in the branch
     * name, else [NONE]. An explicit value that is not `SPA-###` is a usage error.
     */
    fun resolve(explicit: String?, environment: String?, branch: String?): String {
        val chosen =
            explicit.nonBlank()?.let { it to "--ticket" }
                ?: environment.nonBlank()?.let { it to "GATE_TICKET" }
        if (chosen != null) {
            val (value, source) = chosen
            require(pattern.matches(value)) { "$source must look like SPA-###, got '$value'" }
            return value
        }
        return branch?.let { pattern.find(it)?.value } ?: NONE
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
