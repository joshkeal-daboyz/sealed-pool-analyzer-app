package spa.cli

import java.io.PrintStream
import java.nio.file.Path
import spa.core.brief.BriefLoader
import spa.core.brief.BriefValidationException
import spa.core.brief.FormatBrief
import spa.core.cards.CardIndex

/**
 * `spa validate-brief <file> --set <code> [--data <dir>]`: checks a brief against its schema and
 * rules, then checks every signpost and key-common name against the set's card index.
 *
 * A valid brief prints `OK <file>` and one `warning:` line per name the index cannot find, all on
 * stdout, and exits 0. An invalid brief prints one `error:` line per problem, with its JSON
 * pointer, on stderr and exits 1.
 */
object ValidateBriefCommand {
    fun run(args: List<String>, stdout: PrintStream, stderr: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("set", "data"))
        val file = Commands.onePositional(parsed, "brief file")
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)

        val text = Commands.readFile(Path.of(file), "brief")
        val brief =
            try {
                BriefLoader.parse(text)
            } catch (e: BriefValidationException) {
                stderr.println("$file: ${e.errors.size} error(s)")
                e.errors.forEach { stderr.println("error: $it") }
                return ExitCode.ERROR
            }
        val index = Commands.loadIndex(data, set)
        stdout.println("OK $file")
        missingNames(brief, index).forEach { (pointer, name) ->
            stdout.println("warning: $pointer: '$name' is not a card in set '$set'")
        }
        stdout.flush()
        return ExitCode.OK
    }

    /** Every signpost and key-common name [index] cannot find, with its JSON pointer. */
    fun missingNames(brief: FormatBrief, index: CardIndex): List<Pair<String, String>> =
        brief.archetypes.flatMapIndexed { a, archetype ->
            val named =
                archetype.signpostUncommons.mapIndexed { i, name ->
                    "/archetypes/$a/signpostUncommons/$i" to name
                } +
                    archetype.keyCommons.mapIndexed { i, name ->
                        "/archetypes/$a/keyCommons/$i" to name
                    }
            named.filter { (_, name) -> index.byName(name) == null }
        }
}
