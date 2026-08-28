package a3.core.world

import a3.core.world.api.BeliefReader
import a3.core.world.api.Fact
import java.time.Instant

/**
 * Immutable belief state. [facts] is the full history including superseded records.
 * Implements [BeliefReader] for read-only consumers (prediction uses the interface only).
 */
data class BeliefState(
    override val version: Long = 0,
    val facts: List<Fact> = emptyList()
) : BeliefReader {
    override fun validAt(fact: Fact, t: Instant): Boolean = fact.validAt(t)

    override fun current(t: Instant): List<Fact> =
        facts.filter { validAt(it, t) }
            .sortedWith(compareBy<Fact> { it.k }.thenBy { it.id })

    fun liveFacts(): List<Fact> = facts.filter { it.supersededBy == null }
}

enum class IntegrateMode {
    SUPERSEDE_KEYS,
    COMPENSATE
}

/**
 * Sole belief-merge function. WorldState.apply uses this only for AcceptedObservation.
 * Planner simulation uses the same merge on a copy that is never committed WorldState.
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
        val incomingByKey = java.util.TreeMap<String, Fact>()
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
