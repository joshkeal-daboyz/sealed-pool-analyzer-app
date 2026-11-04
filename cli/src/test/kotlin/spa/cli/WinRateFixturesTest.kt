package spa.cli

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import spa.agent.grade.GradeFiles
import spa.agent.grade.GradeSource
import spa.agent.winrate.WinRateScale
import spa.core.cards.CardIndex

/** Criterion 3: the committed win-rate tables load through the grade file loader. */
class WinRateFixturesTest {
    @ParameterizedTest
    @ValueSource(strings = ["msh", "hob", "fra"])
    fun `the committed table loads as a WIN_RATE grade file for its set`(set: String) {
        val file = GradeFiles.load(Fixtures.root.resolve("winrates/$set.json"))

        assertEquals(set, file.header.setCode)
        assertEquals(GradeSource.WIN_RATE, file.header.source)
        assertTrue("game_data_public.${set.uppercase()}.PremierDraft" in file.header.provenance)
        assertTrue(file.grades.size > 100, "$set has ${file.grades.size} grades")
        val index = CardIndex.load(Fixtures.root.resolve("scryfall"), set)
        for (grade in file.grades) {
            val games = requireNotNull(grade.games) { "${grade.name} has no games" }
            val gihwr = requireNotNull(grade.gihwr) { "${grade.name} has no gihwr" }
            assertTrue(games >= WinRateScale.MIN_GAMES, "${grade.name}: $games games")
            assertTrue(gihwr in 0.0..1.0, "${grade.name}: $gihwr")
            assertEquals(WinRateScale.confidence(games), grade.confidence, grade.name)
            val printing = index.byCollectorNumber(grade.setCode, grade.collectorNumber)
            assertEquals(
                grade.name,
                printing?.card?.name,
                "${grade.setCode} ${grade.collectorNumber}",
            )
        }
    }
}
