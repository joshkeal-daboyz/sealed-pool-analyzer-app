package spa.agent.tags

/** Every `suppliedBy` strength is 1, 2 or 3, as [StrengthCheck] allows them. */
object SupplyStrengthCheck : TagCheck {
    override fun problems(input: TagCheckInput): List<String> =
        input.axes.flatMap { axis ->
            axis.suppliedBy
                .orEmpty()
                .filter { !StrengthCheck.isStrength(it.strength.toString()) }
                .map {
                    "${input.axesFile}: axis '${axis.id}' strength ${it.strength} for " +
                        "'${it.fact}' is not 1, 2 or 3"
                }
        }
}
