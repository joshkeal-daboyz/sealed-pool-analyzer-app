package spa.cli

import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-048 criterion 5 (an SPA-047 follow-up): the shared blind list catches the spaced win-rate
 * wordings in any case, so a packet line naming one fails the blind check.
 */
class BlindCheckTest {
    @Test
    fun `GIH WR and game in hand fail the blind check, in any case`(@TempDir dir: Path) {
        writeFile(dir, "instructions.md", "Rank the cards.\nTheir GIH WR is given.\n")
        writeFile(dir, "cards-01.json", """[{ "id": "x/1", "name": "Game In Hand Hero" }]""")

        assertEquals(
            listOf("cards-01.json: game in hand", "instructions.md: gih wr"),
            BlindCheck.forbiddenTextHits(dir),
        )
    }

    @Test
    fun `win percent and 17 lands fail the blind check`(@TempDir dir: Path) {
        writeFile(dir, "instructions.md", "Its WIN % is high on 17 Lands.\n")

        assertEquals(
            listOf("instructions.md: win %", "instructions.md: 17 lands"),
            BlindCheck.forbiddenTextHits(dir),
        )
    }

    @Test
    fun `the list holds the SPA-033 wordings and the four spaced ones`() {
        assertEquals(
            listOf(
                "winrate",
                "gihwr",
                "17lands",
                "win rate",
                "gih wr",
                "game in hand",
                "win %",
                "17 lands",
            ),
            BlindCheck.forbiddenText,
        )
    }

    @Test
    fun `a clean packet passes`(@TempDir dir: Path) {
        writeFile(dir, "instructions.md", "Grade every card for Sealed.\n")

        assertEquals(emptyList(), BlindCheck.forbiddenTextHits(dir))
    }
}
