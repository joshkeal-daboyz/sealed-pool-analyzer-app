package spa.agent.tags

/** Every axis's `suppliedBy` fact is one that at least one card of the set has. */
object SupplyFactCheck : TagCheck {
    override fun problems(input: TagCheckInput): List<String> =
        input.axes.flatMap { axis ->
            axis.suppliedBy
                .orEmpty()
                .map { it.fact }
                .distinct()
                .filter { it !in input.facts }
                .map {
                    "${input.axesFile}: axis '${axis.id}' is supplied by '$it', which no card of " +
                        "the set has"
                }
        }
}
