package spa.agent.tags

/** Every role's axis is one of the packet's axes. */
object AxisCheck : TagCheck {
    override fun problems(input: TagCheckInput): List<String> {
        val axes = input.axes.map { it.id }.toSet()
        return input.responses.flatMap { card ->
            card.roles
                .filter { it.axis !in axes }
                .map {
                    "${card.file}: ${card.id} axis '${it.axis}' is not one of the packet's axes"
                }
        }
    }
}
