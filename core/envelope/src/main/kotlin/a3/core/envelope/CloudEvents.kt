package a3.core.envelope

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * CloudEvents 1.0 JSON object schema (required + optional attributes used here).
 * Kept in-module so :core:json stays frozen.
 */
object CloudEventsSchema {
    private val mapper = ObjectMapper()

    const val JSON = """
{
  "type": "object",
  "required": ["specversion", "type", "source", "id"],
  "properties": {
    "specversion": { "type": "string", "const": "1.0" },
    "type": { "type": "string", "minLength": 1 },
    "source": { "type": "string", "minLength": 1 },
    "id": { "type": "string", "minLength": 1 },
    "time": { "type": "string", "format": "date-time" },
    "datacontenttype": { "type": "string" },
    "subject": { "type": "string" },
    "data": { "type": "object" }
  }
}
"""

    fun validateCanonical(canonicalJson: String) {
        val schema = mapper.readTree(JSON)
        val instance = mapper.readTree(canonicalJson)
        val required = schema.get("required").map { it.asText() }
        for (name in required) {
            if (!instance.has(name)) {
                throw EnvelopeReject("CloudEvents 1.0 missing '$name'")
            }
        }
        val spec = instance.get("specversion")?.asText()
        if (spec != CLOUD_EVENTS_SPEC) {
            throw EnvelopeReject("CloudEvents specversion must be 1.0")
        }
        if (!instance.get("id").isTextual || instance.get("id").asText().isEmpty()) {
            throw EnvelopeReject("CloudEvents id must be a non-empty string")
        }
        if (!instance.get("type").isTextual || instance.get("type").asText().isEmpty()) {
            throw EnvelopeReject("CloudEvents type must be a non-empty string")
        }
        if (!instance.get("source").isTextual || instance.get("source").asText().isEmpty()) {
            throw EnvelopeReject("CloudEvents source must be a non-empty string")
        }
    }
}
