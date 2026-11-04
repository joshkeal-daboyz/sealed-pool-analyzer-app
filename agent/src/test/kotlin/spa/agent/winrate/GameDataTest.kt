package spa.agent.winrate

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * Criterion 1: GIH WR and games from the committed hand-written game data file.
 *
 * `small-game-data.csv` holds ten games and three cards. Per game (W = won, L = lost), the card's
 * `opening_hand` and `drawn` counts; a game counts when their sum is at least 1:
 * ```
 * game  won  Grizzly Bears  Treizeci, Sun of Serra  Serra Angel
 *  1    W    1+0 yes        1+0 yes                 0+0 no (in deck)
 *  2    L    0+1 yes        0+0 no                  0+1 yes
 *  3    W    1+1 yes        0+0 no (sideboard)      0+0 no (in deck)
 *  4    W    0+0 no         1+0 yes                 1+0 yes
 *  5    L    0+0 no         0+0 no                  0+0 no (in deck)
 *  6    L    2+0 yes        1+0 yes                 0+0 no (tutored only, in deck)
 *  7    W    0+0 no         0+0 no                  0+2 yes (counted once)
 *  8    L    0+1 yes        0+0 no                  0+0 no (in deck)
 *  9    W    0+0 no         0+0 no                  0+0 no (in deck)
 * 10    L    0+0 no         1+0 yes                 0+0 no (in deck)
 * ```
 *
 * Grizzly Bears: games 1, 2, 3, 6, 8 = 5 games, 2 won (1, 3), GIH WR 2/5 = 0.4. Treizeci, whose
 * name has a comma, is only ever in the opening hand: games 1, 4, 6, 10 = 4 games, 2 won (1, 4),
 * 0.5. Serra Angel is in the deck in every game but in hand only in games 2, 4, 7 = 3 games, 2 won
 * (4, 7), 2/3; the seven deck-only games, including game 6 where it was tutored, do not count.
 */
class GameDataTest {
    private fun resourceBytes(): ByteArray {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/spa/agent/winrate/small-game-data.csv"))
        return stream.use { it.readBytes() }
    }

    private val expected =
        GameDataSummary(
            games = 10,
            cards =
                listOf(
                    CardWinRate("Grizzly Bears", games = 5, wins = 2),
                    CardWinRate("Treizeci, Sun of Serra", games = 4, wins = 2),
                    CardWinRate("Serra Angel", games = 3, wins = 2),
                ),
        )

    @Test
    fun `the small file gives each card's games and wins as hand-counted`() {
        assertEquals(expected, GameData.read(ByteArrayInputStream(resourceBytes())))
    }

    @Test
    fun `GIH WR is wins over games`() {
        val cards = GameData.read(ByteArrayInputStream(resourceBytes())).cards
        assertEquals(listOf(0.4, 0.5, 2.0 / 3.0), cards.map { it.gihwr })
    }

    @Test
    fun `a card never in hand has 0 games and no GIH WR`() {
        assertEquals(0, CardWinRate("Unseen", 0, 0).games)
        assertTrue(CardWinRate("Unseen", 0, 0).gihwr.isNaN())
    }

    @Test
    fun `a gzipped file on disk reads the same as the plain text`(@TempDir dir: Path) {
        val gz = dir.resolve("game_data_public.SYN.PremierDraft.csv.gz")
        GZIPOutputStream(Files.newOutputStream(gz)).use { it.write(resourceBytes()) }
        val plain = dir.resolve("small.csv")
        Files.write(plain, resourceBytes())
        assertEquals(expected, GameData.read(gz))
        assertEquals(expected, GameData.read(plain))
    }

    @Test
    fun `a file with no won column is rejected`() {
        val text = "expansion,opening_hand_A,drawn_A\nSYN,1,0\n"
        val e = assertFailsWith<GameDataException> { GameData.read(text.byteInputStream()) }
        assertTrue("won" in e.message.orEmpty(), e.message)
    }

    @Test
    fun `a won value other than True or False names its line`() {
        val text = "won,opening_hand_A,drawn_A\nTrue,1,0\nmaybe,1,0\n"
        val e = assertFailsWith<GameDataException> { GameData.read(text.byteInputStream()) }
        assertTrue("line 3" in e.message.orEmpty(), e.message)
    }

    @Test
    fun `a row with the wrong number of fields names its line`() {
        val text = "won,opening_hand_A,drawn_A\nTrue,1\n"
        val e = assertFailsWith<GameDataException> { GameData.read(text.byteInputStream()) }
        assertTrue("line 2" in e.message.orEmpty(), e.message)
    }

    @Test
    fun `an empty file is rejected`() {
        assertFailsWith<GameDataException> { GameData.read(ByteArrayInputStream(ByteArray(0))) }
    }
}
