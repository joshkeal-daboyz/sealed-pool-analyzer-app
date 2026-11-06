package spa.agent.review

import spa.agent.model.ModelRequest

/** What running one agent came to. */
sealed interface AgentOutcome {
    val agent: AnalysisAgent

    /** The agent gave a valid [answer]; [dropped] are the findings the checks threw away. */
    data class Answered(
        override val agent: AnalysisAgent,
        val answer: AgentAnswer,
        val dropped: List<DroppedFinding>,
    ) : AgentOutcome

    /** The agent is dropped for this run; the review says so with [reason]. */
    data class Missing(override val agent: AnalysisAgent, val reason: String) : AgentOutcome
}

/** What [AgentRunner.plan] says a run would do for one enabled agent. */
sealed interface PlannedCall {
    val agent: AnalysisAgent

    /** The agent would be sent [request]. */
    data class Send(override val agent: AnalysisAgent, val request: ModelRequest) : PlannedCall

    /** The agent cannot run, for [reason], and would send nothing. */
    data class Skipped(override val agent: AnalysisAgent, val reason: String) : PlannedCall
}
