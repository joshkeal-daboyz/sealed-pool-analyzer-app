package spa.agent.tags

/** No card names more than [TagPackets.MAX_PARTNERS] partners. */
object PartnerCountCheck : TagCheck {
    override fun problems(input: TagCheckInput): List<String> =
        input.responses
            .filter { it.partners.size > TagPackets.MAX_PARTNERS }
            .map {
                "${it.file}: ${it.id} has ${it.partners.size} partners; " +
                    "give at most ${TagPackets.MAX_PARTNERS}"
            }
}
