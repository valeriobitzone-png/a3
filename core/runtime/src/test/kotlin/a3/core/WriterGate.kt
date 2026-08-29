package a3.core

import a3.core.policy.PolicyDecision
import a3.core.runtime.mintAcceptedObservation
import a3.core.world.BeliefState
import a3.core.world.IntegrateMode
import a3.core.world.Observation
import a3.core.world.WorldState
import a3.core.world.WriteResult
import java.time.Instant

/**
 * Independent [WorldState.apply] of every `observation.accepted` payload on [source].
 * Not the same instance as the execute path — used to prove ExecutionResult equals the gate.
 */
fun reconstructThroughApply(
    initial: BeliefState,
    source: WorldState,
    now: Instant = Instant.parse("2026-08-27T08:00:00Z")
): BeliefState {
    val independent = WorldState(initial)
    for (event in source.eventLog().all()) {
        if (event.type != "observation.accepted") continue
        val obs = event.payload as Observation
        val mode = if (obs.id.startsWith("obs_rollback_")) {
            IntegrateMode.COMPENSATE
        } else {
            IntegrateMode.SUPERSEDE_KEYS
        }
        val write = independent.apply(
            mintAcceptedObservation(obs, PolicyDecision.ALLOW, now, integrateMode = mode)
        )
        check(write is WriteResult.Accepted)
    }
    return independent.committed
}
