// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.runtime

import a3.core.admission.AcceptedObservation
import a3.core.admission.AdmissionPolicy
import a3.core.admission.CLAIM_ARRAY_SCHEMA
import a3.core.admission.Esito
import a3.core.admission.PolicyRegistry
import a3.core.admission.SourceId
import a3.core.admission.acceptedObservation as acceptedFromDecision
import a3.core.admission.admissionPolicy
import a3.core.admission.evaluate
import a3.core.admission.schemaResourceBytes
import a3.core.world.Observation
import a3.core.world.toCandidate
import java.time.Instant

object RuntimeAdmission {
    val registry = PolicyRegistry()
    val policy: AdmissionPolicy = registry.register(
        admissionPolicy(
            policyId = "runtime-permissive",
            policyVersion = "1",
            ttlSeconds = null,
            schemas = mapOf(CLAIM_ARRAY_SCHEMA to schemaResourceBytes(CLAIM_ARRAY_SCHEMA)),
            validatorProfile = "a3.core.json.SchemaValidator",
            validatorVersion = "1"
        )
    )
}

fun admit(
    observation: Observation,
    now: Instant,
    admittedIds: MutableSet<Pair<SourceId, String>>
): AcceptedObservation {
    val candidate = observation.toCandidate()
    val decision = evaluate(
        candidate,
        RuntimeAdmission.policy,
        candidate.ingestedAt,
        admittedIds
    )
    if (decision.esito != Esito.ADMIT) {
        throw IllegalStateException("admission ${decision.esito} ${decision.reasonCode}")
    }
    val accepted = acceptedFromDecision(candidate, decision, now)
    admittedIds.add(candidate.source to candidate.id)
    return accepted
}

fun acceptedObservation(observation: Observation, now: Instant): AcceptedObservation {
    val candidate = observation.toCandidate()
    val decision = evaluate(
        candidate,
        RuntimeAdmission.policy,
        candidate.ingestedAt,
        emptySet()
    )
    if (decision.esito != Esito.ADMIT) {
        throw IllegalStateException("admission ${decision.esito} ${decision.reasonCode}")
    }
    return acceptedFromDecision(candidate, decision, now)
}
