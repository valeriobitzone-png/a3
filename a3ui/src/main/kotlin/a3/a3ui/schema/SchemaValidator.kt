package a3.a3ui.schema

import a3.a3ui.serialize.CanonicalJson
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Paths
import java.util.TreeMap

object SchemaValidator {
    private val mapper = ObjectMapper()
    private val cache = TreeMap<String, JsonNode>()

    fun validateCanonical(schemaFile: String, canonicalJson: String) {
        val schema = loadSchema(schemaFile)
        val instance = mapper.readTree(canonicalJson)
        val errors = ArrayList<String>()
        check(schema, instance, "$", errors)
        require(errors.isEmpty()) {
            "schema $schemaFile failed: ${errors.joinToString("; ")}"
        }
    }

    fun validate(schemaFile: String, model: Any) {
        validateCanonical(schemaFile, CanonicalJson.of(model))
    }

    private fun loadSchema(schemaFile: String): JsonNode {
        cache[schemaFile]?.let { return it }
        val node = mapper.readTree(readSchema(schemaFile))
        cache[schemaFile] = node
        return node
    }

    private fun readSchema(schemaFile: String): String {
        val resource = javaClass.classLoader.getResource("a3/schemas/$schemaFile")
        if (resource != null) return resource.readText()
        val candidates = listOf(
            Paths.get("schemas", schemaFile),
            Paths.get("..", "schemas", schemaFile)
        )
        val path = candidates.firstOrNull { Files.isRegularFile(it) }
            ?: throw IllegalStateException("JSON Schema not found: $schemaFile")
        return Files.readString(path)
    }

    private fun check(schema: JsonNode, instance: JsonNode, path: String, errors: MutableList<String>) {
        if (schema.has("type")) {
            val types = typeList(schema.get("type"))
            if (!matchesType(instance, types)) {
                errors += "$path: expected type $types, got ${instance.nodeType}"
                return
            }
        }
        if (schema.has("enum")) {
            val allowed = schema.get("enum").map { mapper.writeValueAsString(it) }
            val actual = mapper.writeValueAsString(instance)
            if (actual !in allowed) errors += "$path: value $actual not in enum $allowed"
        }
        if (instance.isNumber) {
            val n = instance.doubleValue()
            if (schema.has("minimum") && n < schema.get("minimum").doubleValue()) {
                errors += "$path: $n < minimum"
            }
            if (schema.has("maximum") && n > schema.get("maximum").doubleValue()) {
                errors += "$path: $n > maximum"
            }
        }
        if (instance.isTextual && schema.path("format").asText("") == "date-time") {
            if (!instance.asText().matches(DATE_TIME)) errors += "$path: not a date-time"
        }
        if (instance.isObject) {
            if (schema.has("required")) {
                for (req in schema.get("required")) {
                    val name = req.asText()
                    if (!instance.has(name)) errors += "$path: missing required '$name'"
                }
            }
            val props = schema.get("properties")
            val additional = schema.path("additionalProperties")
            val fieldNames = ArrayList<String>()
            instance.fieldNames().forEachRemaining { fieldNames.add(it) }
            fieldNames.sorted().forEach { name ->
                val child = instance.get(name)
                if (props != null && props.has(name)) {
                    check(props.get(name), child, "$path.$name", errors)
                } else if (additional.isBoolean && !additional.booleanValue()) {
                    errors += "$path: additional property '$name' not allowed"
                } else if (additional.isObject) {
                    check(additional, child, "$path.$name", errors)
                }
            }
        }
        if (instance.isArray && schema.has("items")) {
            val items = schema.get("items")
            instance.forEachIndexed { i, child -> check(items, child, "$path[$i]", errors) }
        }
    }

    private fun typeList(node: JsonNode): List<String> =
        if (node.isArray) node.map { it.asText() } else listOf(node.asText())

    private fun matchesType(instance: JsonNode, types: List<String>): Boolean =
        types.any { type ->
            when (type) {
                "object" -> instance.isObject
                "array" -> instance.isArray
                "string" -> instance.isTextual
                "number" -> instance.isNumber
                "integer" -> instance.isIntegralNumber
                "boolean" -> instance.isBoolean
                "null" -> instance.isNull
                else -> true
            }
        }

    private val DATE_TIME = Regex(
        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$"
    )
}

object ModelValidator {
    fun surface(model: Any) = SchemaValidator.validate("a3uisurface.schema.json", model)
}
