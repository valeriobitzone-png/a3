package a3.core.world

import a3.core.admission.AcceptedObservation
import a3.core.admission.AdmissionLogEntry
import a3.core.admission.Esito
import a3.core.admission.ObservationCandidate
import a3.core.admission.ReasonCode
import a3.core.world.api.BeliefReader
import a3.core.world.api.Claim
import java.time.Instant

/**
 * Immutable belief state. [facts] is the full history including superseded records.
 * Implements [BeliefReader] for read-only consumers (prediction uses the interface only).
 *
 * [admittedKeys] is fold-level DUPLICATE-1 memory (source,id). It is not admission
 * metadata (reasonCode / policyVersion / admittedAt) and is omitted from stateHash.
 */
data class BeliefState(
    override val version: Long = 0,
    val facts: List<Claim> = emptyList(),
    val admittedKeys: Set<Pair<String, String>> = emptySet()
) : BeliefReader {
    override fun validAt(fact: Claim, t: Instant): Boolean = fact.validAt(t)

    override fun current(t: Instant): List<Claim> =
        facts.filter { validAt(it, t) }
            .sortedWith(compareBy<Claim> { it.k }.thenBy { it.id })

    fun liveFacts(): List<Claim> = facts.filter { it.supersededBy == null }

    fun apply(accepted: AcceptedObservation): BeliefState =
        foldAccepted(accepted, IntegrateMode.SUPERSEDE_KEYS)

    fun apply(candidate: ObservationCandidate): BeliefState {
        throw IllegalArgumentException("ObservationCandidate cannot be applied")
    }

    fun apply(entry: AdmissionLogEntry): BeliefState {
        throw IllegalArgumentException("AdmissionLogEntry cannot be applied")
    }

    internal fun foldAccepted(
        accepted: AcceptedObservation,
        mode: IntegrateMode
    ): BeliefState {
        require(accepted.decision.esito == Esito.ADMIT)
        require(accepted.decision.reasonCode == ReasonCode.ADMIT)
        val key = accepted.candidate.source.value to accepted.candidate.id
        if (key in admittedKeys) {
            return this
        }
        val claims = claimsFrom(accepted.candidate.data)
        val observation = Observation(
            id = accepted.candidate.id,
            executionRef = accepted.candidate.id,
            t = accepted.candidate.occurredAt,
            facts = claims
        )
        return integrate(observation, mode).copy(admittedKeys = admittedKeys + key)
    }
}

enum class IntegrateMode {
    SUPERSEDE_KEYS,
    COMPENSATE
}

/**
 * Copy-merge of an observation into a new [BeliefState]. Does not mutate the writer.
 * [BeliefState.apply] is the committed fold; the planner uses this on counterfactual copies.
 */
fun BeliefState.integrate(
    observation: Observation,
    mode: IntegrateMode = IntegrateMode.SUPERSEDE_KEYS
): BeliefState {
    val newVersion = version + 1
    val incoming = observation.facts.mapIndexed { index, fact ->
        val id = fact.id.ifBlank { "fact_v${newVersion}_${index}_${fact.k}" }
        fact.copy(id = id, supersededBy = null)
    }
    val incomingByKey = java.util.TreeMap<String, Claim>()
    for (fact in incoming.sortedWith(compareBy({ it.k }, { it.id }))) {
        incomingByKey[fact.k] = fact
    }
    val rollbackId = "rb_${observation.id}"
    val updated = facts.map { old ->
        if (old.supersededBy != null) old
        else when (mode) {
            IntegrateMode.SUPERSEDE_KEYS -> {
                val replacement = incomingByKey[old.k]
                if (replacement != null) old.copy(supersededBy = replacement.id) else old
            }
            IntegrateMode.COMPENSATE -> {
                val replacement = incomingByKey[old.k]
                old.copy(supersededBy = replacement?.id ?: rollbackId)
            }
        }
    }
    return BeliefState(
        version = newVersion,
        facts = updated + incoming,
        admittedKeys = admittedKeys
    )
}

internal fun claimsFrom(data: Any): List<Claim> {
    val items = data as? List<*>
        ?: throw IllegalArgumentException("candidate.data must be a list of claims")
    return items.map { item ->
        val m = item as Map<*, *>
        val observed = m["observed_at"]
        Claim(
            k = m["k"] as String,
            v = m["v"],
            confidence = (m["confidence"] as Number).toDouble(),
            source = m["source"] as String,
            observedAt = observed as Instant,
            expiresAt = m["expires_at"] as Instant?,
            id = (m["id"] as? String) ?: "",
            supersededBy = m["superseded_by"] as? String
        )
    }
}
