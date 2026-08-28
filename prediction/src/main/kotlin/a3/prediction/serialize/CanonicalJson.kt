package a3.prediction.serialize

import a3.core.world.api.Fact
import a3.prediction.model.Forecast
import a3.prediction.model.ForecastCandidate
import a3.prediction.model.FutureState
import a3.prediction.model.PreparedState
import a3.prediction.model.PredictionInvalidation
import a3.prediction.model.PredictionPolicy
import a3.prediction.model.PredictionStatus
import a3.prediction.model.ProjectionCandidate
import java.time.Instant
import java.util.Locale
import java.util.TreeMap

/**
 * Canonical JSON for prediction types. Same rules as T1 core CanonicalJson.
 */
object CanonicalJson {
    fun of(value: Any?): String = encode(value)

    fun bytes(value: Any?): ByteArray = of(value).toByteArray(Charsets.UTF_8)

    private fun encode(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is Number -> number(value)
        is String -> string(value)
        is Instant -> string(value.toString())
        is Fact -> obj(factFields(value))
        is Forecast -> obj(
            buildMap {
                put("candidates", value.candidates)
                put("context_ref", value.contextRef)
                put("context_signature", value.contextSignature)
                put("id", value.id)
                value.policyRef?.let { put("policy_ref", it) }
                put("produced_at", value.producedAt)
            }
        )
        is ForecastCandidate -> obj(
            mapOf(
                "capability_ref" to value.capabilityRef,
                "future_state_id" to value.futureStateId,
                "id" to value.id,
                "rank" to value.rank,
                "score" to value.score
            )
        )
        is FutureState -> obj(
            mapOf(
                "capability_ref" to value.capabilityRef,
                "context_ref" to value.contextRef,
                "facts" to sortedFacts(value.facts),
                "id" to value.id,
                "produced_at" to value.producedAt,
                "score" to value.score
            )
        )
        is PreparedState -> obj(
            mapOf(
                "context_ref" to value.contextRef,
                "context_signature" to value.contextSignature,
                "expires_at" to value.expiresAt,
                "facts" to sortedFacts(value.facts),
                "forecast_id" to value.forecastId,
                "future_state_id" to value.futureStateId,
                "id" to value.id,
                "prepared_at" to value.preparedAt,
                "status" to value.status.wire(),
                "ttl_seconds" to value.ttlSeconds
            )
        )
        is PredictionPolicy -> obj(
            mapOf(
                "id" to value.id,
                "max_candidates" to value.maxCandidates,
                "min_score" to value.minScore,
                "ttl_seconds" to value.ttlSeconds
            )
        )
        is PredictionStatus -> string(value.wire())
        is ProjectionCandidate -> obj(
            mapOf(
                "context_ref" to value.contextRef,
                "future_state_ref" to value.futureStateRef,
                "id" to value.id,
                "ui_state_hint" to value.uiStateHint
            )
        )
        is PredictionInvalidation -> obj(
            mapOf(
                "context_ref" to value.contextRef,
                "context_signature" to value.contextSignature,
                "id" to value.id,
                "prepared_ids" to value.preparedIds.sorted(),
                "reason" to value.reason,
                "t" to value.t
            )
        )
        is Map<*, *> -> {
            val sorted = TreeMap<String, Any?>()
            for ((k, v) in value) {
                if (k != null) sorted[k.toString()] = v
            }
            obj(sorted)
        }
        is Iterable<*> -> arr(value.toList())
        is Enum<*> -> string(value.name.lowercase(Locale.ROOT))
        else -> string(value.toString())
    }

    private fun factFields(fact: Fact): Map<String, Any?> {
        val fields = TreeMap<String, Any?>()
        fields["confidence"] = fact.confidence
        fact.expiresAt?.let { fields["expires_at"] = it }
        fields["k"] = fact.k
        fields["observed_at"] = fact.observedAt
        fields["source"] = fact.source
        fields["v"] = fact.v
        return fields
    }

    private fun sortedFacts(facts: List<Fact>): List<Fact> =
        facts.sortedWith(
            compareBy<Fact> { it.k }
                .thenBy { it.observedAt }
                .thenBy { it.id }
                .thenBy { it.source }
        )

    private fun obj(fields: Map<String, Any?>): String {
        val sorted = TreeMap<String, Any?>()
        for ((k, v) in fields) {
            if (v != null) sorted[k] = v
        }
        return buildString {
            append('{')
            var first = true
            for ((k, v) in sorted) {
                if (!first) append(',')
                first = false
                append(string(k))
                append(':')
                append(encode(v))
            }
            append('}')
        }
    }

    private fun arr(items: List<*>): String = buildString {
        append('[')
        items.forEachIndexed { i, item ->
            if (i > 0) append(',')
            append(encode(item))
        }
        append(']')
    }

    private fun string(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else append(ch)
            }
        }
        append('"')
    }

    private fun number(value: Number): String {
        val d = value.toDouble()
        if (d.isNaN() || d.isInfinite()) {
            throw IllegalArgumentException("canonical JSON cannot encode $value")
        }
        return if (value is Int || value is Long || value is Short || value is Byte ||
            d == d.toLong().toDouble()
        ) {
            d.toLong().toString()
        } else {
            d.toString()
        }
    }
}
