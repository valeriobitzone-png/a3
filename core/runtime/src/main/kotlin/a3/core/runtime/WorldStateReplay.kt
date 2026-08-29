package a3.core.runtime

import a3.core.events.EventLog
import a3.core.policy.PolicyDecision
import a3.core.world.BeliefState
import a3.core.world.IntegrateMode
import a3.core.world.Observation
import a3.core.world.WorldState
import a3.core.world.WriteResult

/**
 * Reconstructs committed belief by folding [WorldState.apply] over a *fresh* world.
 * Never applies onto the [WorldState] that already owns [log] (duplicate event ids).
 * [integrate_mode] is read from the `observation.accepted` envelope; it is never
 * inferred from observation id prefixes.
 */
object WorldStateReplay {
    fun replay(log: EventLog, initial: BeliefState = BeliefState()): BeliefState {
        val replica = WorldState(initial)
        for (event in log.all()) {
            if (event.type != "observation.accepted") continue
            val observation = event.payload as? Observation
                ?: throw IllegalArgumentException("observation.accepted payload must be Observation")
            val minted = mintAcceptedObservation(
                observation,
                PolicyDecision.ALLOW,
                event.t,
                event.causalId,
                parseIntegrateMode(event.integrateMode)
            )
            val write = replica.apply(minted)
            check(write is WriteResult.Accepted)
        }
        return replica.committed
    }

    internal fun parseIntegrateMode(raw: String?): IntegrateMode {
        if (raw.isNullOrBlank()) {
            throw IllegalArgumentException("observation.accepted missing integrate_mode")
        }
        return when (raw) {
            IntegrateMode.SUPERSEDE_KEYS.name -> IntegrateMode.SUPERSEDE_KEYS
            IntegrateMode.COMPENSATE.name -> IntegrateMode.COMPENSATE
            else -> throw IllegalArgumentException("unknown integrate_mode: $raw")
        }
    }
}
