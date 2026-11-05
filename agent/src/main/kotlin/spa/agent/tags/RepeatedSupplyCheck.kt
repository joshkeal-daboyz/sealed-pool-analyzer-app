package spa.agent.tags

/** No fact appears twice in one axis's `suppliedBy`. */
object RepeatedSupplyCheck : TagCheck {
    override fun problems(input: TagCheckInput): List<String> =
        input.axes.flatMap { axis ->
            axis.suppliedBy
                .orEmpty()
                .groupingBy { it.fact }
                .eachCount()
                .filterValues { it > 1 }
                .map { (fact, n) -> "${input.axesFile}: axis '${axis.id}' gives '$fact' $n times" }
        }
}
