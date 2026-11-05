package spa.agent.tags

import spa.agent.grader.GradingPackets

/**
 * Every card of the packet is tagged exactly once, and no response names a card the packet does not
 * hold. A missing card is named against the response file beside its card file.
 */
object CoverageCheck : TagCheck {
    override fun problems(input: TagCheckInput): List<String> {
        val pool = input.poolIds.toSet()
        val unknown =
            input.responses
                .filter { it.id !in pool }
                .map { "${it.file}: ${it.id} is not a card in this packet" }
        val byId = input.responses.groupBy { it.id }
        val twice =
            input.poolIds.mapNotNull { id ->
                val given = byId[id].orEmpty()
                if (given.size > 1) "${given.first().file}: $id is tagged ${given.size} times"
                else null
            }
        val missing =
            input.cardFiles.flatMap { file ->
                file.ids
                    .filter { it !in byId }
                    .map { "${GradingPackets.responseFileName(file.name)}: $it is not tagged" }
            }
        return twice + unknown + missing
    }
}
