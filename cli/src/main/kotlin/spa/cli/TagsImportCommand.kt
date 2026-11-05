package spa.cli

import java.io.PrintStream
import spa.agent.tags.TagCheck
import spa.agent.tags.TagChecks

/** `spa tags-import`: checks a tagger's responses and writes the tag file. */
class TagsImportCommand(private val checks: List<TagCheck> = TagChecks.ALL) {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int =
        TODO("SPA-047 $args $stdout $stderr $checks")
}
