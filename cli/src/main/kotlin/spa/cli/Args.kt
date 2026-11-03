package spa.cli

/**
 * A command's arguments split into [positionals], options that take a value, and boolean flags.
 * Options are written `--name value` or `--name=value`; flags are written `--name`.
 */
class Args
private constructor(
    val positionals: List<String>,
    private val values: Map<String, String>,
    private val flags: Set<String>,
) {
    /** The value given for option [name] (without the leading dashes), or null. */
    fun value(name: String): String? = values[name]

    /** True when flag [name] (without the leading dashes) was given. */
    fun has(name: String): Boolean = name in flags

    companion object {
        /**
         * Splits [args] for a command that accepts the options in [valueOptions] and the flags in
         * [flagOptions], all named without dashes. An unknown option, a missing value, a value on a
         * flag, or an option given twice is a [CliFailure] with [ExitCode.USAGE].
         */
        fun parse(
            args: List<String>,
            valueOptions: Set<String> = emptySet(),
            flagOptions: Set<String> = emptySet(),
        ): Args {
            val parser = Parser(valueOptions, flagOptions)
            val rest = ArrayDeque(args)
            while (rest.isNotEmpty()) parser.take(rest.removeFirst(), rest)
            return Args(parser.positionals, parser.values, parser.flags)
        }
    }

    /** Collects one command line's arguments, one at a time. */
    private class Parser(
        private val valueOptions: Set<String>,
        private val flagOptions: Set<String>,
    ) {
        val positionals = mutableListOf<String>()
        val values = mutableMapOf<String, String>()
        val flags = mutableSetOf<String>()

        /** Takes [arg]; an option's value, when written as the next argument, comes from [rest]. */
        fun take(arg: String, rest: ArrayDeque<String>) {
            when {
                !arg.startsWith("-") || arg == "-" -> positionals += arg
                !arg.startsWith("--") || arg == "--" -> usage("unknown option '$arg'")
                else -> option(arg, rest)
            }
        }

        private fun option(arg: String, rest: ArrayDeque<String>) {
            val name = arg.removePrefix("--").substringBefore('=')
            val inline = if ('=' in arg) arg.substringAfter('=') else null
            if (name in values || name in flags) usage("option '--$name' given twice")
            when (name) {
                in flagOptions ->
                    if (inline == null) flags += name else usage("option '--$name' takes no value")
                in valueOptions -> values[name] = inline ?: nextValue(name, rest)
                else -> usage("unknown option '--$name'")
            }
            if (values[name]?.isEmpty() == true) usage("option '--$name' needs a value")
        }

        private fun nextValue(name: String, rest: ArrayDeque<String>): String {
            val next = rest.firstOrNull()
            if (next == null || next.startsWith("--")) usage("option '--$name' needs a value")
            return rest.removeFirst()
        }

        private fun usage(message: String): Nothing = throw CliFailure(message, ExitCode.USAGE)
    }
}

/** The exit codes every command uses. */
object ExitCode {
    /** The command did what it was asked. */
    const val OK = 0

    /** A file, cache, brief, or the network failed, or a required option was absent. */
    const val ERROR = 1

    /** The command line itself was wrong: no command, an unknown one, or a bad flag. */
    const val USAGE = 2
}

/** A command that cannot go on. [Main] prints [message] to stderr and exits with [exitCode]. */
class CliFailure(message: String, val exitCode: Int, cause: Throwable? = null) :
    RuntimeException(message, cause)
