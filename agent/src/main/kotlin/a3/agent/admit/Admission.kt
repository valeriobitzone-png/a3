package a3.agent.admit

import a3.core.admission.AcceptedObservation
import a3.core.admission.AdmissionDecision
import a3.core.admission.AdmissionPolicy
import a3.core.admission.CLAIM_ARRAY_SCHEMA
import a3.core.admission.ConfidenceProfile
import a3.core.admission.Esito
import a3.core.admission.ObservationCandidate
import a3.core.admission.SourceId
import a3.core.admission.SourceType
import a3.core.admission.acceptedObservation
import a3.core.admission.admissionPolicy
import a3.core.admission.evaluate
import a3.core.admission.schemaResourceBytes
import a3.core.world.api.Claim
import java.time.Instant

data class AdmissionView(
    val candidate: ObservationCandidate,
    val decision: AdmissionDecision,
    val accepted: AcceptedObservation?,
    val claims: List<Claim>,
    val held: Boolean
)

object AgentAdmission {
    fun policy(): AdmissionPolicy = admissionPolicy(
        policyId = "agent-surface",
        policyVersion = "1",
        ttlSeconds = null,
        holdModelGrounded = true,
        schemas = mapOf(CLAIM_ARRAY_SCHEMA to schemaResourceBytes(CLAIM_ARRAY_SCHEMA))
    )

    fun candidate(
        source: String,
        id: String,
        at: Instant,
        claims: List<Claim>,
        sourceType: SourceType = SourceType.DIRECT
    ): ObservationCandidate {
        val data = claims.map { claim ->
            buildMap<String, Any?> {
                put("k", claim.k)
                put("v", claim.v)
                put("confidence", claim.confidence)
                put("source", claim.source)
                put("observed_at", claim.observedAt)
                claim.expiresAt?.let { put("expires_at", it) }
            }
        }
        return ObservationCandidate(
            source = SourceId(source),
            id = id,
            occurredAt = at,
            observedAt = at,
            ingestedAt = at,
            data = data,
            dataschema = CLAIM_ARRAY_SCHEMA,
            confidenceProfile = ConfidenceProfile(sourceType, opaque = "")
        )
    }

    fun consider(
        candidate: ObservationCandidate,
        policy: AdmissionPolicy,
        asOf: Instant,
        admitted: MutableSet<Pair<SourceId, String>>
    ): AdmissionView {
        val decision = evaluate(candidate, policy, asOf, admitted)
        val claims = claimsOf(candidate)
        return when (decision.esito) {
            Esito.ADMIT -> {
                val accepted = acceptedObservation(candidate, decision, asOf)
                admitted += candidate.source to candidate.id
                AdmissionView(candidate, decision, accepted, claims, held = false)
            }
            Esito.HELD -> AdmissionView(candidate, decision, null, claims, held = true)
            Esito.REJECT -> AdmissionView(candidate, decision, null, claims, held = false)
        }
    }

    fun claimsOf(candidate: ObservationCandidate): List<Claim> {
        val rows = candidate.data as? List<*> ?: return emptyList()
        return rows.mapNotNull { row ->
            val map = row as? Map<*, *> ?: return@mapNotNull null
            val k = map["k"] as? String ?: return@mapNotNull null
            val source = map["source"] as? String ?: return@mapNotNull null
            val confidence = (map["confidence"] as? Number)?.toDouble() ?: return@mapNotNull null
            Claim(
                k = k,
                v = map["v"],
                confidence = confidence,
                source = source,
                observedAt = instant(map["observed_at"]) ?: return@mapNotNull null,
                expiresAt = instant(map["expires_at"])
            )
        }
    }

    private fun instant(value: Any?): Instant? = when (value) {
        null -> null
        is Instant -> value
        is String -> Instant.parse(value)
        else -> null
    }
}
