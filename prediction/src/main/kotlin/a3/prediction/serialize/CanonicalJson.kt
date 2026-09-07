package a3.prediction.serialize

import a3.core.json.CanonicalJson as JsonCanonical
import a3.core.world.api.Claim
import a3.prediction.model.Forecast
import a3.prediction.model.ForecastCandidate
import a3.prediction.model.FutureState
import a3.prediction.model.PreparedState
import a3.prediction.model.PredictionInvalidation
import a3.prediction.model.PredictionPolicy
import a3.prediction.model.PredictionStatus
import a3.prediction.model.PrefetchHint

/**
 * Prediction typed facade. Engine rules live in :core:json.
 */
object CanonicalJson {
    fun of(value: Any?): String = JsonCanonical.encode(wire(value))

    fun bytes(value: Any?): ByteArray = of(value).toByteArray(Charsets.UTF_8)

    private fun wire(value: Any?): Any? = when (value) {
        null, is Boolean, is Number, is String, is java.time.Instant -> value
        is Claim -> factFields(value)
        is Forecast -> buildMap {
            put("candidates", wire(value.candidates))
            put("context_ref", value.contextRef)
            put("context_signature", value.contextSignature)
            put("id", value.id)
            value.policyRef?.let { put("policy_ref", it) }
            put("produced_at", value.producedAt)
        }
        is ForecastCandidate -> mapOf(
            "capability_ref" to value.capabilityRef,
            "future_state_id" to value.futureStateId,
            "id" to value.id,
            "rank" to value.rank,
            "score" to value.score
        )
        is FutureState -> mapOf(
            "capability_ref" to value.capabilityRef,
            "context_ref" to value.contextRef,
            "facts" to wire(sortedFacts(value.facts)),
            "id" to value.id,
            "produced_at" to value.producedAt,
            "score" to value.score
        )
        is PreparedState -> mapOf(
            "context_ref" to value.contextRef,
            "context_signature" to value.contextSignature,
            "expires_at" to value.expiresAt,
            "facts" to wire(sortedFacts(value.facts)),
            "forecast_id" to value.forecastId,
            "future_state_id" to value.futureStateId,
            "id" to value.id,
            "prepared_at" to value.preparedAt,
            "status" to value.status.wire(),
            "ttl_seconds" to value.ttlSeconds
        )
        is PredictionPolicy -> mapOf(
            "id" to value.id,
            "max_candidates" to value.maxCandidates,
            "min_score" to value.minScore,
            "ttl_seconds" to value.ttlSeconds
        )
        is PredictionStatus -> value.wire()
        is PrefetchHint -> mapOf(
            "context_ref" to value.contextRef,
            "future_state_ref" to value.futureStateRef,
            "id" to value.id,
            "ui_state_hint" to value.uiStateHint
        )
        is PredictionInvalidation -> mapOf(
            "context_ref" to value.contextRef,
            "context_signature" to value.contextSignature,
            "id" to value.id,
            "prepared_ids" to value.preparedIds.sorted(),
            "reason" to value.reason,
            "t" to value.t
        )
        is Map<*, *> -> {
            val out = LinkedHashMap<String, Any?>()
            for ((k, v) in value) {
                if (k != null) out[k.toString()] = wire(v)
            }
            out
        }
        is Iterable<*> -> value.map { wire(it) }
        is Enum<*> -> value.name.lowercase(java.util.Locale.ROOT)
        else -> value.toString()
    }

    private fun factFields(fact: Claim): Map<String, Any?> = buildMap {
        put("confidence", fact.confidence)
        fact.expiresAt?.let { put("expires_at", it) }
        put("k", fact.k)
        put("observed_at", fact.observedAt)
        put("source", fact.source)
        put("v", wire(fact.v))
    }

    private fun sortedFacts(facts: List<Claim>): List<Claim> =
        facts.sortedWith(
            compareBy<Claim> { it.k }
                .thenBy { it.observedAt }
                .thenBy { it.id }
                .thenBy { it.source }
        )
}
