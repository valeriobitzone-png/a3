package a3.core.world

import a3.core.events.EventLog
import a3.core.model.Event
import a3.core.model.Observation
import java.time.Instant
import java.util.TreeMap

enum class EpistemicSource {
    OBSERVATION_ACCEPTED,
    PREDICTION,
    POLICY,
    EXECUTION
}

sealed class WriteResult {
    data class Accepted(val state: BeliefState) : WriteResult()
    data class Rejected(val source: EpistemicSource, val reason: String) : WriteResult()
}

/**
 * Committed world state. R1–R5: the only successful write pathway is
 * [EpistemicSource.OBSERVATION_ACCEPTED]. Prediction, Policy and Execution
 * writes are rejected and appended to the event log.
 */
class WorldState(
    initial: BeliefState = BeliefState(),
    private val events: EventLog = EventLog()
) {
    var committed: BeliefState = initial
        private set

    fun write(
        source: EpistemicSource,
        observation: Observation,
        now: Instant,
        causalId: String? = null
    ): WriteResult {
        if (source != EpistemicSource.OBSERVATION_ACCEPTED) {
            events.append(
                Event(
                    id = "ev_write_rejected_${source.name}_${events.size() + 1}",
                    t = now,
                    source = source.name.lowercase(),
                    type = "state.write_rejected",
                    causalId = causalId,
                    stateVersion = committed.version,
                    payload = TreeMap<String, Any>().apply {
                        put("reason", "only Observation.accepted may write WorldState")
                        put("source", source.name)
                    }
                )
            )
            return WriteResult.Rejected(
                source,
                "only Observation.accepted may write WorldState"
            )
        }
        committed = ObservationAcceptance.apply(committed, observation)
        events.append(
            Event(
                id = "ev_state_${committed.version}",
                t = now,
                source = "observation",
                type = "state.updated",
                causalId = causalId ?: observation.id,
                stateVersion = committed.version,
                payload = observation
            )
        )
        return WriteResult.Accepted(committed)
    }

    fun eventLog(): EventLog = events
}
