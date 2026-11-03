package spa.core.baseline

import java.nio.file.Path
import spa.core.brief.BriefLoader
import spa.core.brief.FormatBrief
import spa.core.brief.synBriefPath

/** The committed `fixtures/baselines` directory, passed in by the Gradle test task. */
val baselineFixturesDir: Path =
    Path.of(
        requireNotNull(System.getProperty("spa.baseline.fixtures")) {
            "Run through Gradle: the test task sets spa.baseline.fixtures."
        }
    )

/** `fixtures/briefs/syn.json`. */
val synBrief: FormatBrief by lazy { BriefLoader.load(synBriefPath) }

/** The text with every CRLF or CR line ending made LF. */
fun normalizeLineEndings(text: String): String = text.replace("\r\n", "\n").replace("\r", "\n")
