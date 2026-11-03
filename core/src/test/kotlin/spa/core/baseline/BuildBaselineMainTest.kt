package spa.core.baseline

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir
import spa.core.brief.briefFixturesDir
import spa.core.cards.fixturesDir

class BuildBaselineMainTest {
    @TempDir lateinit var out: Path

    private fun args(vararg rest: String) =
        arrayOf(fixturesDir.toString(), briefFixturesDir.toString(), out.toString(), *rest)

    @Test
    fun `main writes the set's baseline into the directory it is given`() {
        main(args("syn", "3", "100"))
        val written = Files.readString(out.resolve("syn.json"))
        val expected = BaselineBuilder.load(fixturesDir, briefFixturesDir, "syn").build(3, 100)
        assertEquals(expected.encode(), written)
    }

    @Test
    fun `main rejects missing and malformed arguments`() {
        assertFailsWith<IllegalArgumentException> { main(args("syn", "1")) }
        assertFailsWith<IllegalArgumentException> { main(args("syn", "one", "100")) }
        assertFailsWith<IllegalArgumentException> { main(args("syn", "1", "many")) }
        assertFailsWith<IllegalArgumentException> { main(args("syn", "1", "99")) }
    }
}
