package spa.gate

/** The `dependencies` step: catalog additions against the ticket, and literal coordinates. */
object DependencyAudit {
    const val CATALOG = "gradle/libs.versions.toml"

    private val step = GateStep.DEPENDENCIES.id

    /**
     * Failures for catalog entries added since the merge base. With ticket [TicketId.NONE] the
     * catalog must be unchanged; otherwise every added coordinate must be listed under
     * [TicketDependencies.HEADING] in [ticketMarkdown] (null when the ticket file is missing).
     */
    fun catalogFailures(
        ticket: String,
        ticketMarkdown: String?,
        baseCatalog: String?,
        headCatalog: String?,
        reproduce: String,
    ): List<GateFailure> {
        val added = headCatalog?.let { VersionCatalog.added(baseCatalog, it) }.orEmpty()
        val unreadable =
            added
                .filter { it.coordinate == null }
                .map { failure(it.line, "Cannot read catalog entry '${it.alias}'.", reproduce) }
        val readable = added.filter { it.coordinate != null }
        return unreadable +
            when {
                ticket == TicketId.NONE ->
                    noneFailures(readable, baseCatalog, headCatalog, reproduce)
                ticketMarkdown == null ->
                    listOf(failure(null, "Ticket file tickets/$ticket.md not found.", reproduce))
                else -> unlisted(ticket, ticketMarkdown, readable, reproduce)
            }
    }

    /** Failures for coordinates written as string literals in build scripts outside build-logic. */
    fun literalFailures(scripts: Map<String, String>, reproduce: String): List<GateFailure> =
        scripts.toSortedMap().flatMap { (path, text) ->
            LiteralCoordinates.scan(text).map { hit ->
                GateFailure(
                    step,
                    path,
                    hit.line,
                    "Dependency coordinate ${hit.text} is a string literal; " +
                        "add it to $CATALOG and use the catalog alias.",
                    reproduce,
                )
            }
        }

    private fun noneFailures(
        added: List<CatalogEntry>,
        baseCatalog: String?,
        headCatalog: String?,
        reproduce: String,
    ): List<GateFailure> {
        val why = "but no ticket was resolved (ticket none), so the catalog must be unchanged."
        val perEntry = added.map {
            failure(it.line, "${it.coordinate} was added to [${it.section}] $why", reproduce)
        }
        val changed = normalize(baseCatalog) != normalize(headCatalog)
        return perEntry.ifEmpty {
            if (changed)
                listOf(failure(null, "$CATALOG changed since the merge base $why", reproduce))
            else emptyList()
        }
    }

    private fun unlisted(
        ticket: String,
        ticketMarkdown: String,
        added: List<CatalogEntry>,
        reproduce: String,
    ): List<GateFailure> {
        val allowed = TicketDependencies.allowed(ticketMarkdown)
        return added
            .filter { it.coordinate !in allowed }
            .map {
                failure(
                    it.line,
                    "${it.coordinate} was added to [${it.section}] but is not listed under " +
                        "\"${TicketDependencies.HEADING}\" in tickets/$ticket.md.",
                    reproduce,
                )
            }
    }

    private fun failure(line: Int?, message: String, reproduce: String) =
        GateFailure(step, CATALOG, line, message, reproduce)

    private fun normalize(text: String?): String? = text?.replace("\r\n", "\n")?.trimEnd()
}
