package a3.core.runtime

import a3.core.admission.Esito
import a3.core.admission.SourceId
import a3.core.admission.acceptedObservation
import a3.core.admission.evaluate
import a3.core.events.EventLog
import a3.core.world.BeliefState
import a3.core.world.BeliefWriter
import a3.core.world.IntegrateMode
import a3.core.world.Observation
import a3.core.world.WriteResult
import a3.core.world.toCandidate

/**
 * Reconstructs committed belief by folding [BeliefWriter.apply] over a *fresh* writer.
 * Never applies onto the [BeliefWriter] that already owns [log] (duplicate event ids).
 * [integrate_mode] is read from the `observation.accepted` envelope; it is never
 * inferred from observation id prefixes.
 */
object WorldStateReplay {
    fun replay(log: EventLog, initial: BeliefState = BeliefState()): BeliefState {
        val replica = BeliefWriter(initial)
        val admittedIds = linkedSetOf<Pair<SourceId, String>>()
        for (event in log.all()) {
            if (event.type != "observation.accepted") continue
            val observation = event.payload as? Observation
                ?: throw IllegalArgumentException("observation.accepted payload must be Observation")
            val candidate = observation.toCandidate()
            val decision = evaluate(
                candidate,
                RuntimeAdmission.policy,
                candidate.ingestedAt,
                admittedIds
            )
            if (decision.esito != Esito.ADMIT) {
                throw IllegalStateException("replay admission ${decision.esito} ${decision.reasonCode}")
            }
            val minted = acceptedObservation(candidate, decision, event.t)
            admittedIds.add(candidate.source to candidate.id)
            val mode = parseIntegrateMode(event.integrateMode)
            val write = when (mode) {
                IntegrateMode.SUPERSEDE_KEYS -> replica.apply(minted)
                IntegrateMode.COMPENSATE -> replica.applyCompensating(minted, event.causalId)
            }
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
