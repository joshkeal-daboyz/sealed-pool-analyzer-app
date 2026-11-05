package spa.agent.tags

/** No card names the same partner more than once. */
object RepeatedPartnerCheck : TagCheck {
    override fun problems(input: TagCheckInput): List<String> =
        input.responses.flatMap { card ->
            card.partners
                .groupingBy { it.id }
                .eachCount()
                .filterValues { it > 1 }
                .map { (id, n) -> "${card.file}: ${card.id} names partner $id $n times" }
        }
}
