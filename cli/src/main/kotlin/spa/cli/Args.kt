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
            val positionals = mutableListOf<String>()
            val values = mutableMapOf<String, String>()
            val flags = mutableSetOf<String>()
            var i = 0
            while (i < args.size) {
                val arg = args[i]
                i++
                if (!arg.startsWith("--") || arg == "--") {
                    if (arg.startsWith("-") && arg.length > 1) usage("unknown option '$arg'")
                    positionals += arg
                    continue
                }
                val name = arg.removePrefix("--").substringBefore('=')
                val inline = if ('=' in arg) arg.substringAfter('=') else null
                if (name in values || name in flags) usage("option '--$name' given twice")
                when (name) {
                    in flagOptions ->
                        if (inline == null) flags += name
                        else usage("option '--$name' takes no value")
                    in valueOptions -> {
                        val value = inline ?: args.getOrNull(i)?.also { i++ }
                        if (
                            value == null ||
                                value.isEmpty() ||
                                inline == null && value.startsWith("--")
                        )
                            usage("option '--$name' needs a value")
                        values[name] = value
                    }
                    else -> usage("unknown option '--$name'")
                }
            }
            return Args(positionals, values, flags)
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
