package spa.agent.review

/**
 * One analyst of the pool review (G-006): its own class, its own prompt file
 * `spa/agent/review/<id>-<version>.md` (the version comes from the review config), its own tests.
 * The runner, the checker and the printer see only this interface; adding, removing or reordering
 * an agent touches [AnalysisAgents.ALL] and the agent's own files.
 */
interface AnalysisAgent {
    /** The agent's key in the config and its section key: lowercase letters and hyphens. */
    val id: String

    /** The heading of the agent's section in the review. */
    val title: String

    /** The numbers this agent may quote, from the resolved [input]. */
    fun numbers(input: ReviewInput): NumberTable

    /** Plain text the model may use besides the table; empty for none. */
    fun facts(input: ReviewInput): String

    /** Null when the agent can run on [input]; otherwise a one-line reason it cannot. */
    fun unavailable(input: ReviewInput): String?
}
