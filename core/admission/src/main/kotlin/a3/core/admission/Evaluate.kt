// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.admission

import a3.core.json.CanonicalJson
import a3.core.json.SchemaValidator
import java.time.Duration
import java.time.Instant

/**
 * Pure admission. asOf is a caller-supplied instant (online: candidate.ingestedAt).
 * Dedup order vs other gates is a forward-ref to step 3; this run uses
 * duplicate → blacklist → schema → ttl → HELD → ADMIT.
 */
fun evaluate(
    candidate: ObservationCandidate,
    immutablePolicySnapshot: AdmissionPolicy,
    asOf: Instant,
    admittedIdSet: Set<Pair<SourceId, String>>
): AdmissionDecision {
    val policy = immutablePolicySnapshot
    val key = candidate.source to candidate.id
    if (key in admittedIdSet) {
        return reject(policy, ReasonCode.REJECT_DUPLICATE)
    }
    if (candidate.source in policy.blacklist) {
        return reject(policy, ReasonCode.REJECT_POLICY_DENY)
    }
    if (!schemaValid(candidate, policy)) {
        return reject(policy, ReasonCode.REJECT_SCHEMA_INVALID)
    }
    val ttl = policy.ttlSeconds
    if (ttl != null) {
        if (ttl == 0L) {
            return reject(policy, ReasonCode.REJECT_STALE)
        }
        val age = Duration.between(candidate.observedAt, asOf).seconds
        if (age > ttl) {
            return reject(policy, ReasonCode.REJECT_STALE)
        }
    }
    if (policy.holdModelGrounded &&
        candidate.confidenceProfile.sourceType == SourceType.GROUNDED_BY_MODEL
    ) {
        return AdmissionDecision(
            esito = Esito.HELD,
            reasonCode = ReasonCode.HELD_FOR_REVIEW,
            policyId = policy.policyId,
            policyVersion = policy.policyVersion
        )
    }
    return AdmissionDecision(
        esito = Esito.ADMIT,
        reasonCode = ReasonCode.ADMIT,
        policyId = policy.policyId,
        policyVersion = policy.policyVersion
    )
}

private fun reject(policy: AdmissionPolicy, reason: ReasonCode): AdmissionDecision =
    AdmissionDecision(
        esito = Esito.REJECT,
        reasonCode = reason,
        policyId = policy.policyId,
        policyVersion = policy.policyVersion
    )

private fun schemaValid(candidate: ObservationCandidate, policy: AdmissionPolicy): Boolean {
    if (candidate.dataschema.isBlank()) return false
    val expected = policy.schemas[candidate.dataschema] ?: return false
    return try {
        val canonical = CanonicalJson.encode(candidate.data)
        SchemaValidator.validateCanonical(candidate.dataschema, canonical)
        val classpath = schemaResourceBytes(candidate.dataschema)
        expected.contentEquals(classpath)
    } catch (_: Exception) {
        false
    }
}
