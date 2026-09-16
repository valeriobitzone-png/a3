// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.world

import a3.core.admission.AcceptedObservation
import a3.core.admission.CLAIM_ARRAY_SCHEMA
import a3.core.admission.ConfidenceProfile
import a3.core.admission.ObservationCandidate
import a3.core.admission.SourceId
import a3.core.admission.SourceType
import a3.core.world.api.Claim
import java.time.Instant

data class Observation(
    val id: String,
    val executionRef: String,
    val t: Instant,
    val facts: List<Claim>
)

data class Event(
    val id: String,
    val t: Instant,
    val source: String,
    val type: String,
    val causalId: String? = null,
    val stateVersion: Long? = null,
    val payload: Any? = null,
    val integrateMode: String? = null
)

sealed class WriteResult {
    data class Accepted(
        val state: BeliefState,
        val acceptedEventId: String
    ) : WriteResult()
}

fun Observation.toCandidate(): ObservationCandidate {
    val source = SourceId(facts.firstOrNull()?.source ?: "runtime")
    val data = facts.map { claim ->
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
        source = source,
        id = id,
        occurredAt = t,
        observedAt = t,
        ingestedAt = t,
        data = data,
        dataschema = CLAIM_ARRAY_SCHEMA,
        confidenceProfile = ConfidenceProfile(SourceType.DIRECT, opaque = "")
    )
}

/**
 * Commit envelope: EventLog plus the latest [BeliefState].
 * The fold is [BeliefState.apply]; this class records the envelope.
 */
class BeliefWriter(
    initial: BeliefState = BeliefState(),
    private val events: a3.core.events.EventLog = a3.core.events.EventLog()
) {
    var committed: BeliefState = initial
        private set

    fun apply(accepted: AcceptedObservation): WriteResult =
        commit(accepted, IntegrateMode.SUPERSEDE_KEYS, accepted.candidate.id)

    fun applyCompensating(accepted: AcceptedObservation, causalId: String?): WriteResult =
        commit(accepted, IntegrateMode.COMPENSATE, causalId)

    private fun commit(
        accepted: AcceptedObservation,
        mode: IntegrateMode,
        causalId: String?
    ): WriteResult {
        committed = committed.foldAccepted(accepted, mode)
        val acceptedId = "ev_accepted_${committed.version}"
        val observation = Observation(
            id = accepted.candidate.id,
            executionRef = accepted.candidate.id,
            t = accepted.candidate.occurredAt,
            facts = a3.core.world.claimsFrom(accepted.candidate.data)
        )
        events.append(
            Event(
                id = acceptedId,
                t = accepted.admittedAt,
                source = "observation",
                type = "observation.accepted",
                causalId = causalId ?: accepted.candidate.id,
                stateVersion = committed.version,
                payload = observation,
                integrateMode = mode.name
            )
        )
        events.append(
            Event(
                id = "ev_state_${committed.version}",
                t = accepted.admittedAt,
                source = "observation",
                type = "state.updated",
                causalId = acceptedId,
                stateVersion = committed.version,
                payload = observation
            )
        )
        return WriteResult.Accepted(committed, acceptedId)
    }

    fun eventLog(): a3.core.events.EventLog = events
}
