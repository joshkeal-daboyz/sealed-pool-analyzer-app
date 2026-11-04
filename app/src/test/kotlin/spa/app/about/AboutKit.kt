package spa.app.about

import androidx.compose.runtime.Composable
import org.robolectric.RuntimeEnvironment
import spa.app.data.TestFixtures
import spa.app.ui.theme.SpaTheme

/** The About screen under test, and what it must show, read from the repository's files. */
object AboutKit {
    @Composable
    fun Screen(onBack: () -> Unit = {}) {
        SpaTheme { AboutScreen(onBack = onBack) }
    }

    /** The repository's `README.md`, beside `fixtures/`. */
    private val readme
        get() = TestFixtures.root.parentFile.resolve("README.md")

    /**
     * B1: the first two sentences of the README's Card data section, each Markdown link reduced to
     * its label, read at test time.
     */
    val attribution: String by lazy { firstTwoSentences(cardDataParagraph(readme.readText())) }

    /** The date part of the bundled `fetched-at.txt`, read from `fixtures/`. */
    val snapshotDate: String by lazy {
        TestFixtures.text("scryfall/trk/fetched-at.txt").trim().substringBefore('T')
    }

    /** The installed package's `versionName`, which the build sets. */
    val versionName: String
        get() {
            val app = RuntimeEnvironment.getApplication()
            return checkNotNull(app.packageManager.getPackageInfo(app.packageName, 0).versionName)
        }

    /** The first paragraph under `## Card data`, its lines joined, its links reduced to labels. */
    fun cardDataParagraph(markdown: String): String {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { it.trim() == "## Card data" }
        check(start >= 0) { "README.md has no '## Card data' section" }
        val paragraph =
            lines.drop(start + 1).dropWhile { it.isBlank() }.takeWhile { it.isNotBlank() }
        return paragraph.joinToString(" ") { it.trim() }.replace(LINK, "$1")
    }

    /** The text up to the end of its second sentence. */
    fun firstTwoSentences(text: String): String {
        val ends = SENTENCE_END.findAll(text).map { it.range.first + 1 }.toList()
        check(ends.size >= 2) { "Fewer than two sentences in: $text" }
        return text.substring(0, ends[1])
    }

    private val LINK = Regex("""\[([^\]]+)]\([^)]+\)""")
    private val SENTENCE_END = Regex("""\.(\s|$)""")
}
