package spa.core.brief

import java.nio.file.Path
import kotlin.test.assertFailsWith

/** The committed `fixtures/briefs` directory, passed in by the Gradle test task. */
val briefFixturesDir: Path =
    Path.of(
        requireNotNull(System.getProperty("spa.brief.fixtures")) {
            "Run through Gradle: the test task sets spa.brief.fixtures."
        }
    )

/** `fixtures/briefs/syn.json`, the complete example brief. */
val synBriefPath: Path = briefFixturesDir.resolve("syn.json")

/** The text of an example under `src/test/resources/briefs/`. */
fun briefResource(name: String): String {
    val stream =
        requireNotNull(BriefLoader::class.java.getResourceAsStream("/briefs/$name.json")) {
            "No test brief $name.json"
        }
    return stream.use { it.readBytes().decodeToString() }
}

/** The errors [text] fails to load with; fails the test when it loads. */
fun loadErrors(text: String): List<BriefError> =
    assertFailsWith<BriefValidationException> { BriefLoader.parse(text) }.errors
