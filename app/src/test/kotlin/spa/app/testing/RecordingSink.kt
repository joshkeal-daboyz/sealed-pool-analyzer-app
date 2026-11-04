package spa.app.testing

import spa.app.timing.TimingSink

/** A fake log sink: keeps every line [spa.app.timing.SpaTiming] writes, in order. */
class RecordingSink : TimingSink {
    val lines = mutableListOf<Pair<String, String>>()

    override fun log(tag: String, message: String) {
        lines += tag to message
    }

    /** The event names logged so far, in order. */
    val events: List<String>
        get() = lines.map { it.second.substringBefore(' ') }
}
