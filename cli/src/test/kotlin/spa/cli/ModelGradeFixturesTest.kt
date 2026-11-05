package spa.cli

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import spa.agent.grade.GradeFiles
import spa.agent.grade.GradeSource
import spa.agent.grader.GradingPool
import spa.core.cards.CardIndex

/**
 * SPA-034 criterion 1: each committed model grade file holds exactly one schema-valid grade for
 * every card of its set's sealed pool as the grading packet defines it, with source MODEL and the
 * same prompt version.
 */
class ModelGradeFixturesTest {
    private val sets = listOf("msh", "hob", "fra", "trk")

    @ParameterizedTest
    @CsvSource("msh, 276", "hob, 188", "fra, 280", "trk, 336")
    fun `the committed file grades every card of the sealed pool exactly once`(
        set: String,
        count: Int,
    ) {
        // GradeFiles.load validates the schema and rejects duplicate cards.
        val file = GradeFiles.load(Fixtures.root.resolve("grades/$set.json"))
        val index = CardIndex.load(Fixtures.root.resolve("scryfall"), set)
        val pool = GradingPool.printings(index).map { it.setCode to it.collectorNumber }

        assertEquals(set, file.header.setCode)
        assertEquals(GradeSource.MODEL, file.header.source)
        assertEquals(count, pool.size, "$set sealed pool size")
        assertEquals(count, file.grades.size, "$set grade count")
        assertEquals(pool.toSet(), file.grades.map { it.setCode to it.collectorNumber }.toSet())
        val names =
            GradingPool.printings(index).associate {
                (it.setCode to it.collectorNumber) to it.card.name
            }
        file.grades.forEach {
            assertEquals(
                names[it.setCode to it.collectorNumber],
                it.name,
                "${it.setCode} ${it.collectorNumber}",
            )
        }
    }

    @Test
    fun `all four files share one provenance naming the model and prompt v1`() {
        val provenances = sets.map {
            GradeFiles.load(Fixtures.root.resolve("grades/$it.json")).header.provenance
        }

        assertEquals(
            listOf("claude-fable-5-1 via Claude Code, prompt v1, 2026-11-04"),
            provenances.distinct(),
        )
    }
}
