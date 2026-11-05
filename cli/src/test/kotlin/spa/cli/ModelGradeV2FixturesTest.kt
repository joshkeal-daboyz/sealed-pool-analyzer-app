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
 * SPA-037 criterion 2: each committed v2 grade file (`fixtures/grades/<code>.v2.json`) holds
 * exactly one schema-valid grade for every card of its set's sealed pool, with source MODEL and a
 * provenance naming prompt v2; and `spa grade-backtest --model` runs on each backtest set's v2
 * file.
 */
class ModelGradeV2FixturesTest {
    private val sets = listOf("msh", "hob", "fra", "trk")

    private fun v2(set: String) = Fixtures.root.resolve("grades/$set.v2.json")

    @ParameterizedTest
    @CsvSource("msh, 276", "hob, 188", "fra, 280", "trk, 336")
    fun `the v2 file grades every card of the sealed pool exactly once`(set: String, count: Int) {
        // GradeFiles.load validates the schema and rejects duplicate cards.
        val file = GradeFiles.load(v2(set))
        val index = CardIndex.load(Fixtures.root.resolve("scryfall"), set)
        val printings = GradingPool.printings(index)
        val pool = printings.map { it.setCode to it.collectorNumber }

        assertEquals(set, file.header.setCode)
        assertEquals(GradeSource.MODEL, file.header.source)
        assertEquals(count, pool.size, "$set sealed pool size")
        assertEquals(count, file.grades.size, "$set grade count")
        assertEquals(pool.toSet(), file.grades.map { it.setCode to it.collectorNumber }.toSet())
        val names = printings.associate { (it.setCode to it.collectorNumber) to it.card.name }
        file.grades.forEach {
            assertEquals(
                names[it.setCode to it.collectorNumber],
                it.name,
                "${it.setCode} ${it.collectorNumber}",
            )
        }
    }

    @Test
    fun `all four v2 files share one provenance naming the model and prompt v2`() {
        val provenances = sets.map { GradeFiles.load(v2(it)).header.provenance }

        assertEquals(
            listOf("claude-fable-5-1 via Claude Code, prompt v2, 2026-11-05"),
            provenances.distinct(),
        )
    }

    @ParameterizedTest
    @CsvSource(
        "msh, 'all: rho 0.401, n 273'",
        "hob, 'all: rho 0.392, n 179'",
        "fra, 'all: rho 0.480, n 266'",
    )
    fun `grade-backtest runs on the v2 file and names prompt v2`(set: String, all: String) {
        val run =
            runCli(
                "grade-backtest",
                "--set",
                set,
                "--model",
                v2(set).toString(),
                "--data",
                Fixtures.data,
            )

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("", run.stderr)
        val lines = run.stdoutLines()
        assertEquals(all, lines.first())
        assertEquals(
            "model: claude-fable-5-1 via Claude Code, prompt v2, 2026-11-05",
            lines.single { it.startsWith("model: ") },
        )
    }
}
