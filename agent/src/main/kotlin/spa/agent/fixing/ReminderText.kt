package spa.agent.fixing

/**
 * Reminder text: everything in parentheses. Treasure and basic landcycling reminders would
 * otherwise match the TREASURE, MANA and SEARCH rules.
 */
object ReminderText {
    private val PARENTHESISED = Regex("""\([^)]*\)""")

    /** [text] with every parenthesised part removed. */
    fun strip(text: String): String = PARENTHESISED.replace(text, "")
}
