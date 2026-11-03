package spa.core.brief

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One set's limited format as a human describes it: its mechanics, the ten designed two-color
 * archetypes, the generic archetypes, the prerelease kit, and how fast the format plays. The shape
 * is fixed by `format-brief.schema.json`; [BriefLoader] reads and checks it.
 */
@Serializable
data class FormatBrief(
    val setCode: String,
    val setName: String,
    val version: Int,
    val speed: FormatSpeed,
    val speedNotes: String,
    val mechanics: List<Mechanic>,
    val archetypes: List<Archetype>,
    val genericArchetypes: List<GenericArchetype>,
    val kitTemplate: KitTemplate,
    val companionSetCodes: List<String> = emptyList(),
)

/** How fast games in the format tend to be decided. */
@Serializable
enum class FormatSpeed {
    @SerialName("fast") FAST,
    @SerialName("medium") MEDIUM,
    @SerialName("slow") SLOW,
}

/** A named mechanic with its rules text and, if it has one, its reminder text. */
@Serializable
data class Mechanic(val name: String, val rulesText: String, val reminderText: String? = null)

/**
 * A designed two-color archetype. [colors] is two distinct letters from WUBRG in the order the
 * brief wrote them; `WU` and `UW` are the same pair.
 */
@Serializable
data class Archetype(
    val colors: String,
    val name: String,
    val plan: String,
    val signpostUncommons: List<String>,
    val keyCommons: List<String> = emptyList(),
)

/** An archetype that is not one color pair, such as a five-color or artifacts deck. */
@Serializable
data class GenericArchetype(
    val name: String,
    val description: String,
    val colors: String? = null,
)

/**
 * What a prerelease kit contains: [boosters] boosters of [cardsPerBooster] cards each, filled by
 * [slots], plus an optional [promo] card. The slot counts sum to [cardsPerBooster].
 */
@Serializable
data class KitTemplate(
    val boosters: Int,
    val cardsPerBooster: Int,
    val slots: List<BoosterSlot>,
    val promo: PromoSlot?,
    val basicLandsSupplied: Boolean,
)

/** [count] cards of one booster, each drawn by the relative [weights] of each kind of card. */
@Serializable
data class BoosterSlot(
    val name: String,
    val count: Int,
    val foil: Boolean = false,
    val weights: Map<SlotKind, Double>,
)

/** The kit's extra promo card, drawn by the relative [weights] of each kind of card. */
@Serializable data class PromoSlot(val weights: Map<SlotKind, Double>)

/** A kind of card a slot can hold, named as the brief names it. */
@Serializable
enum class SlotKind {
    @SerialName("common") COMMON,
    @SerialName("uncommon") UNCOMMON,
    @SerialName("rare") RARE,
    @SerialName("mythic") MYTHIC,
    @SerialName("basicLand") BASIC_LAND,
    @SerialName("commonLand") COMMON_LAND,
    @SerialName("bonusSheet") BONUS_SHEET,
}
