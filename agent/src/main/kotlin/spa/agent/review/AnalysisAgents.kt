package spa.agent.review

import spa.agent.review.agents.ArchetypeAgent
import spa.agent.review.agents.BombAgent
import spa.agent.review.agents.ColourDepthAgent
import spa.agent.review.agents.CurveAgent
import spa.agent.review.agents.InteractionAgent
import spa.agent.review.agents.ManaAgent
import spa.agent.review.agents.SynergyAgent

/** The analysts the review runs, in the order they run and print. */
object AnalysisAgents {
    val ALL: List<AnalysisAgent> =
        listOf(
            ColourDepthAgent,
            CurveAgent,
            ManaAgent,
            InteractionAgent,
            BombAgent,
            ArchetypeAgent,
            SynergyAgent,
        )
}
