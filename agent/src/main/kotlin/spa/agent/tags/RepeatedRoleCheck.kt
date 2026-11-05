package spa.agent.tags

/** No card gives the same role on the same axis more than once. */
object RepeatedRoleCheck : TagCheck {
    override fun problems(input: TagCheckInput): List<String> =
        input.responses.flatMap { card ->
            card.roles
                .groupingBy { it.axis to it.role }
                .eachCount()
                .filterValues { it > 1 }
                .map { (key, n) ->
                    "${card.file}: ${card.id} gives ${key.second} on ${key.first} $n times"
                }
        }
}
