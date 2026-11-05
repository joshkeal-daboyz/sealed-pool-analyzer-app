package spa.agent.tags

/** Every named partner is a card of the packet; the card itself is allowed. */
object PartnerPoolCheck : TagCheck {
    override fun problems(input: TagCheckInput): List<String> {
        val pool = input.poolIds.toSet()
        return input.responses.flatMap { card ->
            card.partners
                .filter { it.id !in pool }
                .map { "${card.file}: ${card.id} partner ${it.id} is not a card in this packet" }
        }
    }
}
