// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.admission

import java.time.Instant

const val CLAIM_ARRAY_SCHEMA = "claim-array.schema.json"

@JvmInline
value class SourceId private constructor(val value: String) {
    companion object {
        operator fun invoke(raw: String): SourceId {
            val canonical = raw.trim().lowercase()
            require(canonical.isNotBlank()) { "SourceId must be non-blank" }
            require(canonical != "llm") { "LLM is not a Source" }
            return SourceId(canonical)
        }
    }
}

enum class SourceType {
    DIRECT,
    INFERRED,
    GROUNDED_BY_MODEL
}

data class ConfidenceProfile(
    val sourceType: SourceType,
    val opaque: String
)

data class ObservationCandidate(
    val source: SourceId,
    val id: String,
    val occurredAt: Instant,
    val observedAt: Instant,
    val ingestedAt: Instant,
    val data: Any,
    val dataschema: String,
    val confidenceProfile: ConfidenceProfile
) {
    init {
        require(id.isNotBlank()) { "candidate id must be non-blank" }
        require(source.value != "llm") { "LLM is not a Source" }
    }
}

enum class Esito {
    ADMIT,
    REJECT,
    HELD
}

enum class ReasonCode {
    ADMIT,
    REJECT_STALE,
    REJECT_POLICY_DENY,
    REJECT_SCHEMA_INVALID,
    REJECT_DUPLICATE,
    HELD_FOR_REVIEW
}

data class AdmissionDecision(
    val esito: Esito,
    val reasonCode: ReasonCode,
    val policyId: String,
    val policyVersion: String
) {
    init {
        when (esito) {
            Esito.ADMIT -> require(reasonCode == ReasonCode.ADMIT) {
                "ADMIT requires reasonCode ADMIT"
            }
            Esito.HELD -> require(reasonCode == ReasonCode.HELD_FOR_REVIEW) {
                "HELD requires reasonCode HELD_FOR_REVIEW"
            }
            Esito.REJECT -> require(reasonCode.name.startsWith("REJECT_")) {
                "REJECT requires a REJECT_* reasonCode"
            }
        }
    }
}

class AcceptedObservation internal constructor(
    val candidate: ObservationCandidate,
    val decision: AdmissionDecision,
    val admittedAt: Instant
) {
    init {
        require(decision.esito == Esito.ADMIT) { "only ADMIT produces AcceptedObservation" }
        require(decision.reasonCode == ReasonCode.ADMIT) { "AcceptedObservation requires reasonCode ADMIT" }
    }
}

fun acceptedObservation(
    candidate: ObservationCandidate,
    decision: AdmissionDecision,
    admittedAt: Instant
): AcceptedObservation {
    if (decision.esito != Esito.ADMIT || decision.reasonCode != ReasonCode.ADMIT) {
        throw IllegalArgumentException("only ADMIT produces AcceptedObservation")
    }
    return AcceptedObservation(candidate, decision, admittedAt)
}

data class AdmissionLogEntry(
    val candidate: ObservationCandidate,
    val decision: AdmissionDecision,
    val at: Instant,
    val reasonCode: ReasonCode
) {
    init {
        require(decision.esito == Esito.REJECT || decision.esito == Esito.HELD) {
            "log entry is REJECT or HELD only"
        }
        require(reasonCode == decision.reasonCode) { "log reasonCode must match decision" }
    }
}

fun decisionBytes(decision: AdmissionDecision): ByteArray =
    a3.core.json.CanonicalJson.encode(
        mapOf(
            "esito" to decision.esito,
            "policyId" to decision.policyId,
            "policyVersion" to decision.policyVersion,
            "reasonCode" to decision.reasonCode
        )
    ).toByteArray(Charsets.UTF_8)
