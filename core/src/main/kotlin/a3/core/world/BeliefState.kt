package a3.core.world

import a3.core.model.Fact
import a3.core.model.Observation
import java.time.Instant
import java.util.TreeMap

/**
 * Immutable belief state. [facts] is the full history including superseded records.
 * [current] returns only facts that [validAt] accepts.
 *
 * validAt(f,t) := not superseded AND observed_at <= t AND (no expiry OR t < expires_at)
 */
data class BeliefState(
    val version: Long = 0,
    val facts: List<Fact> = emptyList()
) {
    fun validAt(fact: Fact, t: Instant): Boolean {
        if (fact.supersededBy != null) return false
        if (fact.observedAt.isAfter(t)) return false
        val expiry = fact.expiresAt
        if (expiry != null && !t.isBefore(expiry)) return false
        return true
    }

    fun current(t: Instant): List<Fact> =
        facts.filter { validAt(it, t) }
            .sortedWith(compareBy<Fact> { it.k }.thenBy { it.id })

    fun liveFacts(): List<Fact> = facts.filter { it.supersededBy == null }
}

enum class IntegrateMode {
    /** Supersede live facts whose key appears in the observation. */
    SUPERSEDE_KEYS,
    /** Compensatory rollback: supersede every live fact, then write restore facts. */
    COMPENSATE
}

/**
 * Sole belief-merge function. Runtime calls this only for an accepted Observation.
 * Planner simulation uses the same merge on a *copy* that is never the committed WorldState.
 */
object ObservationAcceptance {
    fun apply(
        state: BeliefState,
        observation: Observation,
        mode: IntegrateMode = IntegrateMode.SUPERSEDE_KEYS
    ): BeliefState {
        val newVersion = state.version + 1
        val incoming = observation.facts.mapIndexed { index, fact ->
            val id = fact.id.ifBlank { "fact_v${newVersion}_${index}_${fact.k}" }
            fact.copy(id = id, supersededBy = null)
        }
        val incomingByKey = TreeMap<String, Fact>()
        for (fact in incoming.sortedWith(compareBy({ it.k }, { it.id }))) {
            incomingByKey[fact.k] = fact
        }
        val rollbackId = "rb_${observation.id}"
        val updated = state.facts.map { old ->
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
        return BeliefState(version = newVersion, facts = updated + incoming)
    }
}
