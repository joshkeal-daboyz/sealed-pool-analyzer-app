package spa.agent.model

/** The seam between grading code and the model. */
fun interface ModelClient {
    fun send(request: ModelRequest): ModelResponse
}
