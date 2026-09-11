package a3.core.t12

import a3.core.confidence.AggregationWeights
import a3.core.confidence.ConfidenceVector
import a3.core.confidence.aggregate
import a3.core.confidence.parseVector
import a3.core.envelope.CloudEventEnvelope
import a3.core.envelope.CloudEventsSchema
import a3.core.envelope.contentId
import a3.core.envelope.encodeEvent
import a3.core.envelope.pack
import a3.core.envelope.parseJson
import a3.core.envelope.parseStamp
import a3.core.truth.TruthClass
import a3.core.truth.parseBearer

data class AdmittedProbe(
    val event: CloudEventEnvelope,
    val rawJson: String,
    val canonicalJson: String,
    val expectedId: String,
    val postconditionVerified: Boolean,
    val confidence: ConfidenceVector?,
    val confidenceScore: Double?,
    val computedScore: Double?
)

fun admitFromModel(json: String): AdmittedProbe {
    val parsed = parseJson(json) as? Map<*, *>
        ?: throw T12Reject("model output is not a JSON object")
    @Suppress("UNCHECKED_CAST")
    val root = parsed as Map<String, Any?>
    val data = root["data"] as? Map<*, *>
        ?: throw T12Reject("missing data")
    @Suppress("UNCHECKED_CAST")
    val dataMap = data as Map<String, Any?>
    val temporalRaw = dataMap["temporal"] as? Map<*, *>
        ?: throw T12Reject("missing temporal")
    val truthRaw = dataMap["truth"] as? Map<*, *>
        ?: throw T12Reject("missing truth")
    @Suppress("UNCHECKED_CAST")
    val stamp = parseStamp(temporalRaw as Map<String, Any?>)
    @Suppress("UNCHECKED_CAST")
    val truth = parseBearer(truthRaw as Map<String, Any?>)
    val content = dataMap["content"]
    val sourceId = sourceIdOf(root["source"]?.toString() ?: T12_SOURCE_ID)
    val type = root["type"]?.toString()?.ifBlank { null } ?: T12_EVENT_TYPE
    val subject = root["subject"]?.toString()?.ifBlank { null } ?: T12_SUBJECT
    val event = pack(
        type = type,
        sourceId = sourceId,
        subject = subject,
        stamp = stamp,
        truth = truth,
        content = content,
        foldRef = dataMap["fold_ref"]?.toString()
    )
    CloudEventsSchema.validateCanonical(encodeEvent(event))
    val expectedId = contentId(event.data)
    val verified = postconditionVerified(content)
        ?: throw T12Reject("missing postcondition_verified")
    val confidenceRaw = asStringKeyed(dataMap["confidence"])
        ?: asStringKeyed((content as? Map<*, *>)?.get("confidence"))
    val confidence = confidenceRaw?.let { parseVector(it) }
    val reportedScore = confidenceRaw?.get("score") as? Number
    val computed = confidence?.let { aggregate(it, AggregationWeights.DEFAULT) }
    return AdmittedProbe(
        event = event,
        rawJson = json,
        canonicalJson = encodeEvent(event),
        expectedId = expectedId,
        postconditionVerified = verified,
        confidence = confidence,
        confidenceScore = reportedScore?.toDouble(),
        computedScore = computed
    )
}

fun sourceIdOf(source: String): String {
    val trimmed = source.trim()
    return when {
        trimmed.startsWith("urn:a3:source:") -> trimmed.removePrefix("urn:a3:source:")
        trimmed.isBlank() -> T12_SOURCE_ID
        else -> trimmed.substringAfterLast(':').substringAfterLast('/').ifBlank { T12_SOURCE_ID }
    }
}

fun allowedTruth(truthClass: TruthClass): Boolean =
    truthClass == TruthClass.OBSERVATION || truthClass == TruthClass.UNKNOWN

private fun postconditionVerified(content: Any?): Boolean? {
    val map = content as? Map<*, *> ?: return null
    val raw = map["postcondition_verified"] ?: map["postconditionVerified"]
    return when (raw) {
        is Boolean -> raw
        is String -> raw.equals("true", ignoreCase = true)
        is Number -> raw.toInt() != 0
        else -> null
    }
}

private fun asStringKeyed(raw: Any?): Map<String, Any?>? {
    val map = raw as? Map<*, *> ?: return null
    val out = LinkedHashMap<String, Any?>()
    for ((k, v) in map) {
        if (k != null) out[k.toString()] = v
    }
    return out
}
