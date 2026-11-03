package spa.core.cards

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** The JSON settings for reading and writing Scryfall objects. */
internal object ScryfallJson {
    /** Reads Scryfall objects into the DTOs below, ignoring the many fields they do not name. */
    val reader = Json { ignoreUnknownKeys = true }

    /** Writes small objects such as `set.json` for people to read and diff. */
    @OptIn(ExperimentalSerializationApi::class)
    val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
}

/** The fields of a Scryfall set object the loader uses. */
@Serializable
internal data class ScryfallSetDto(
    val code: String,
    val name: String,
    @SerialName("card_count") val cardCount: Int,
    @SerialName("set_type") val setType: String,
    @SerialName("search_uri") val searchUri: String? = null,
)

/** One page of a Scryfall list; card objects are kept verbatim. */
@Serializable
internal data class ScryfallListDto(
    val data: List<JsonObject>,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_page") val nextPage: String? = null,
)

/** The fields of a Scryfall card object the normalizer uses. */
@Serializable
internal data class ScryfallCardDto(
    val id: String,
    @SerialName("oracle_id") val oracleId: String? = null,
    val name: String,
    val layout: String,
    val set: String,
    @SerialName("collector_number") val collectorNumber: String,
    val rarity: String,
    @SerialName("promo_types") val promoTypes: List<String> = emptyList(),
    @SerialName("mana_cost") val manaCost: String? = null,
    val cmc: Double? = null,
    val colors: List<String>? = null,
    @SerialName("color_identity") val colorIdentity: List<String> = emptyList(),
    @SerialName("color_indicator") val colorIndicator: List<String>? = null,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    @SerialName("card_faces") val cardFaces: List<ScryfallFaceDto>? = null,
)

/** The fields of one entry of a Scryfall card's `card_faces`. */
@Serializable
internal data class ScryfallFaceDto(
    val name: String,
    @SerialName("oracle_id") val oracleId: String? = null,
    @SerialName("mana_cost") val manaCost: String? = null,
    val cmc: Double? = null,
    val colors: List<String>? = null,
    @SerialName("color_indicator") val colorIndicator: List<String>? = null,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    val power: String? = null,
    val toughness: String? = null,
)
