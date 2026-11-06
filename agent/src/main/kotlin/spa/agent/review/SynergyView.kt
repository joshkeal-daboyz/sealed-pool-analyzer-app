package spa.agent.review

import spa.agent.synergy.SynergyTable
import spa.agent.tags.TagFile
import spa.core.cards.Printing

/**
 * A set's synergy data as the Synergy Detector reads it: the [tags], their pair score [table], and
 * [idOf], which gives the tag card id of a printing (the caller passes `GradingPool::id`).
 */
class SynergyView(val tags: TagFile, val table: SynergyTable, val idOf: (Printing) -> String)
