package spa.agent.tags

import java.nio.file.Path
import spa.core.cards.CardIndex

/** What an import gives: the tag [file] when there are no [problems], else null. */
data class TagImportResult(val file: TagFile?, val problems: List<String>)

/** Turns a pass-2 packet's responses into a tag file. */
object TagImport {
    fun run(
        packetDir: Path,
        index: CardIndex,
        setCode: String,
        provenance: String,
        checks: List<TagCheck> = TagChecks.ALL,
    ): TagImportResult = TODO("SPA-047 $packetDir $index $setCode $provenance $checks")
}
