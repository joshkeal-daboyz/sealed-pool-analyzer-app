package spa.agent.tags

/**
 * One role as a response gives it: its [axis], [role], and [strength] as the number was written.
 */
data class ResponseRole(val axis: String, val role: String, val strength: String)

/** One named partner as a response gives it: its card [id] and [strength] as written. */
data class ResponsePartner(val id: String, val strength: String)

/** One well-formed response entry, read from the response [file] (its name). */
data class ResponseCard(
    val file: String,
    val id: String,
    val roles: List<ResponseRole>,
    val partners: List<ResponsePartner>,
)

/** One card file of the packet: its [name] and the card [ids] it holds, in order. */
data class CardFileIds(val name: String, val ids: List<String>)

/** What an import check sees: the packet's [cardFiles], its [axes], and every response entry. */
class TagCheckInput(
    val cardFiles: List<CardFileIds>,
    val axes: List<Axis>,
    val responses: List<ResponseCard>,
) {
    /** Every card id the packet holds, in packet order. */
    val poolIds: List<String> = cardFiles.flatMap { it.ids }
}

/**
 * One rule a tagging response must keep. [problems] gives one line per break, naming the response
 * file and the card, and none when the responses keep the rule.
 */
fun interface TagCheck {
    fun problems(input: TagCheckInput): List<String>
}

/** The checks `spa tags-import` runs, in order. A new check is one file and one entry here. */
object TagChecks {
    val ALL: List<TagCheck> =
        listOf(
            CoverageCheck,
            AxisCheck,
            RoleNameCheck,
            StrengthCheck,
            RepeatedRoleCheck,
            PartnerPoolCheck,
            PartnerCountCheck,
            RepeatedPartnerCheck,
        )
}
