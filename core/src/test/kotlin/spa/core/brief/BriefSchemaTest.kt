package spa.core.brief

import io.github.optimumcode.json.schema.ErrorCollector
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.JsonSchemaLoader
import io.github.optimumcode.json.schema.SchemaType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BriefSchemaTest {
    private val schemaJson = Json.parseToJsonElement(BriefLoader.schemaText).jsonObject

    @Test
    fun `the schema declares draft 2020-12`() {
        assertEquals(
            "https://json-schema.org/draft/2020-12/schema",
            schemaJson.getValue("\$schema").jsonPrimitive.content,
        )
    }

    @Test
    fun `the schema is a valid draft 2020-12 schema by the meta-schema`() {
        val metaSchema =
            JsonSchemaLoader.create()
                .registerWellKnown(SchemaType.DRAFT_2020_12)
                .fromDefinition(
                    """{"${'$'}ref": "https://json-schema.org/draft/2020-12/schema"}""",
                    SchemaType.DRAFT_2020_12,
                )
        val errors = mutableListOf<String>()
        val valid =
            metaSchema.validate(
                schemaJson,
                ErrorCollector { errors += "${it.objectPath}: ${it.message}" },
            )
        assertTrue(valid, errors.joinToString("\n"))
    }

    @Test
    fun `the meta-schema check rejects a broken schema, so the test above can fail`() {
        val metaSchema =
            JsonSchemaLoader.create()
                .registerWellKnown(SchemaType.DRAFT_2020_12)
                .fromDefinition(
                    """{"${'$'}ref": "https://json-schema.org/draft/2020-12/schema"}""",
                    SchemaType.DRAFT_2020_12,
                )
        val broken = Json.parseToJsonElement("""{"type": "objekt", "minItems": -1}""")
        assertTrue(!metaSchema.validate(broken, ErrorCollector.EMPTY))
    }

    @Test
    fun `syn json validates against the schema alone`() {
        val schema = JsonSchema.fromDefinition(BriefLoader.schemaText)
        val errors = mutableListOf<String>()
        val brief = Json.parseToJsonElement(Files.readString(synBriefPath))
        val valid =
            schema.validate(brief, ErrorCollector { errors += "${it.objectPath}: ${it.message}" })
        assertTrue(valid, errors.joinToString("\n"))
    }

    @Test
    fun `every object in the schema forbids unknown properties`() {
        val objectSchemas = mutableListOf<Pair<String, JsonObject>>()
        collectObjectSchemas(schemaJson, "", objectSchemas)
        // The document itself, mechanic, archetype, genericArchetype, kitTemplate, promo,
        // boosterSlot, and weights.
        assertEquals(8, objectSchemas.size, objectSchemas.joinToString { it.first })
        for ((path, node) in objectSchemas) {
            assertEquals(
                JsonPrimitive(false),
                node["additionalProperties"],
                "$path allows unknown properties",
            )
        }
    }

    @Test
    fun `required top-level fields are the ones the ticket names`() {
        val required =
            (schemaJson.getValue("required") as JsonArray).map { it.jsonPrimitive.content }.toSet()
        assertEquals(
            setOf(
                "setCode",
                "setName",
                "version",
                "speed",
                "speedNotes",
                "mechanics",
                "archetypes",
                "genericArchetypes",
                "kitTemplate",
            ),
            required,
        )
        assertTrue("companionSetCodes" in schemaJson.getValue("properties").jsonObject)
    }

    /** Every subschema whose `type` is or includes `object`, with its location in the schema. */
    private fun collectObjectSchemas(
        node: JsonElement,
        path: String,
        into: MutableList<Pair<String, JsonObject>>,
    ) {
        when (node) {
            is JsonObject -> {
                val type = node["type"]
                val isObject =
                    type == JsonPrimitive("object") ||
                        (type is JsonArray && JsonPrimitive("object") in type)
                if (isObject) into += path to node
                node.forEach { (key, child) -> collectObjectSchemas(child, "$path/$key", into) }
            }
            is JsonArray ->
                node.forEachIndexed { i, child -> collectObjectSchemas(child, "$path/$i", into) }
            else -> Unit
        }
    }
}
