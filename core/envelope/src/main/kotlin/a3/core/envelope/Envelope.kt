package a3.core.envelope

import a3.core.temporal.TemporalStamp
import a3.core.truth.TruthBearer
import a3.core.truth.parseBearer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Instant

private val mapper = ObjectMapper()

fun pack(
    type: String,
    sourceId: String,
    subject: String,
    stamp: TemporalStamp,
    truth: TruthBearer,
    content: Any?,
    foldRef: String? = null,
    attestation: Attestation? = null
): CloudEventEnvelope {
    require(type.isNotBlank()) { "type must be non-blank" }
    require(sourceId.isNotBlank()) { "source must be non-blank" }
    require(subject.isNotBlank()) { "subject must be non-blank" }
    val emittedType = if (attestation != null) {
        val normalized = normalizeType(type)
        if (normalized !in CORE_TYPES) {
            throw EnvelopeReject("type not in CORE registry")
        }
        normalized
    } else {
        type
    }
    val source = sourceUri(sourceId)
    val payload = EnvelopePayload(stamp, truth, content, foldRef, attestation)
    val id = contentId(payload)
    val event = CloudEventEnvelope(
        specversion = CLOUD_EVENTS_SPEC,
        type = emittedType,
        source = source,
        id = id,
        time = stamp.tPresent.toString(),
        datacontenttype = DATA_CONTENT_TYPE,
        subject = subject,
        data = payload
    )
    validateCloudEvent(event)
    return event
}

fun contentId(payload: EnvelopePayload): String =
    Jcs.sha256Hex(payload.toCanonical())

fun sourceUri(sourceId: String): String =
    URI.create("urn:a3:source:$sourceId").toString()

fun validateCloudEvent(event: CloudEventEnvelope) {
    if (event.specversion != CLOUD_EVENTS_SPEC) {
        throw EnvelopeReject("specversion must be 1.0")
    }
    if (event.type.isBlank()) throw EnvelopeReject("type must be non-blank")
    if (event.id.isBlank()) throw EnvelopeReject("id must be non-blank")
    if (event.datacontenttype != DATA_CONTENT_TYPE) {
        throw EnvelopeReject("datacontenttype must be application/json")
    }
    if (event.subject.isBlank()) throw EnvelopeReject("subject must be non-blank")
    try {
        URI(event.source)
    } catch (e: Exception) {
        throw EnvelopeReject("source is not a URI")
    }
    try {
        Instant.parse(event.time)
    } catch (e: Exception) {
        throw EnvelopeReject("time is not ISO-8601")
    }
    if (event.time != event.data.temporal.tPresent.toString()) {
        throw EnvelopeReject("time must equal t_present")
    }
    val expected = contentId(event.data)
    if (event.id != expected) {
        throw EnvelopeReject("id is not SHA-256 of JCS payload")
    }
    val attestation = event.data.attestation
    if (attestation != null) {
        validateAttestation(attestation, isIrreversible(event))
    }
}

fun parseEvent(json: String): CloudEventEnvelope {
    val root = mapper.readTree(json)
    val dataNode = root.get("data") ?: throw EnvelopeReject("missing data")
    val dataMap = nodeToMap(dataNode)
    val temporalRaw = dataMap["temporal"] as? Map<*, *>
        ?: throw EnvelopeReject("missing temporal")
    val truthRaw = dataMap["truth"] as? Map<*, *>
        ?: throw EnvelopeReject("missing truth")
    @Suppress("UNCHECKED_CAST")
    val stamp = parseStamp(temporalRaw as Map<String, Any?>)
    @Suppress("UNCHECKED_CAST")
    val truth = parseBearer(truthRaw as Map<String, Any?>)
    val payload = EnvelopePayload(
        temporal = stamp,
        truth = truth,
        content = dataMap["content"],
        foldRef = dataMap["fold_ref"]?.toString(),
        attestation = parseAttestation(dataMap["attestation"])
    )
    val event = CloudEventEnvelope(
        specversion = text(root, "specversion"),
        type = normalizeType(text(root, "type")),
        source = text(root, "source"),
        id = text(root, "id"),
        time = text(root, "time"),
        datacontenttype = text(root, "datacontenttype"),
        subject = text(root, "subject"),
        data = payload
    )
    validateCloudEvent(event)
    return event
}

fun isIrreversible(event: CloudEventEnvelope): Boolean {
    if (isIrreversibleType(event.type)) return true
    val content = event.data.content as? Map<*, *> ?: return false
    val flag = content["irreversible"]
    return flag == true || flag?.toString().equals("true", ignoreCase = true)
}

fun encodeEvent(event: CloudEventEnvelope): String = Jcs.of(event.toCanonical())

fun parseJson(json: String): Any? = nodeToValue(mapper.readTree(json))

internal fun nodeToValue(node: JsonNode): Any? = when {
    node.isNull -> null
    node.isBoolean -> node.booleanValue()
    node.isTextual -> node.textValue()
    node.isInt -> node.intValue()
    node.isLong -> node.longValue()
    node.isNumber -> {
        val d = node.doubleValue()
        if (d == d.toLong().toDouble()) d.toLong() else d
    }
    node.isArray -> node.map { nodeToValue(it) }
    node.isObject -> nodeToMap(node)
    else -> node.asText()
}

internal fun nodeToMap(node: JsonNode): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>()
    val names = node.fieldNames()
    while (names.hasNext()) {
        val name = names.next()
        out[name] = nodeToValue(node.get(name))
    }
    return out
}

private fun text(node: JsonNode, field: String): String {
    val value = node.get(field) ?: throw EnvelopeReject("missing $field")
    if (!value.isTextual) throw EnvelopeReject("$field must be string")
    return value.asText()
}
