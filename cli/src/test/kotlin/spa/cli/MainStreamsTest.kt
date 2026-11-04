package spa.cli

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The streams `main` writes through: UTF-8 whatever the platform's default charset. */
class MainStreamsTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `utf8 writes UTF-8 bytes onto the descriptor and flushes each line`() {
        val file = temp.resolve("out.txt")
        FileOutputStream(file.toFile()).use { sink ->
            val stream = Main.utf8(sink.fd)
            stream.println("Agadeem’s Awakening é")
            // println flushes: the bytes are in the file before the stream is closed.
            assertEquals(
                "Agadeem’s Awakening é" + System.lineSeparator(),
                Files.readString(file, Charsets.UTF_8),
            )
        }
    }
}
