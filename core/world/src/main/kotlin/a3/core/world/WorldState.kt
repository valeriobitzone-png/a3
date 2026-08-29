package a3.core.world

import a3.core.world.api.Fact
import java.time.Instant

data class Observation(
    val id: String,
    val executionRef: String,
    val t: Instant,
    val facts: List<Fact>
)

data class Event(
    val id: String,
    val t: Instant,
    val source: String,
    val type: String,
    val causalId: String? = null,
    val stateVersion: Long? = null,
    val payload: Any? = null
)

/**
 * Compile-time commit token. Only [a3.core.runtime] can construct a subclass
 * (`internal` constructor there). Prediction cannot name WorldState.apply with
 * PreparedState / FutureState / Forecast: those overloads do not exist.
 */
abstract class AcceptedObservation
protected constructor(
    val observation: Observation,
    val now: Instant,
    val causalId: String? = null,
    val integrateMode: IntegrateMode = IntegrateMode.SUPERSEDE_KEYS,
    val policyDecision: String = "ALLOW"
)

sealed class WriteResult {
    data class Accepted(
        val state: BeliefState,
        val acceptedEventId: String
    ) : WriteResult()
}

/**
 * Committed world state. The only apply overload accepts [AcceptedObservation].
 * There is no apply(PreparedState), apply(FutureState), apply(Forecast),
 * apply(Observation), or write(EpistemicSource, …).
 */
class WorldState(
    initial: BeliefState = BeliefState(),
    private val events: a3.core.events.EventLog = a3.core.events.EventLog()
) {
    var committed: BeliefState = initial
        private set

    fun apply(accepted: AcceptedObservation): WriteResult {
        val observation = accepted.observation
        committed = committed.integrate(observation, accepted.integrateMode)
        val acceptedId = "ev_accepted_${committed.version}"
        events.append(
            Event(
                id = acceptedId,
                t = accepted.now,
                source = "observation",
                type = "observation.accepted",
                causalId = accepted.causalId ?: observation.id,
                stateVersion = committed.version,
                payload = observation
            )
        )
        events.append(
            Event(
                id = "ev_state_${committed.version}",
                t = accepted.now,
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
