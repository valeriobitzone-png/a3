package a3.core.events

import a3.core.model.Event
import a3.core.model.Observation
import a3.core.world.BeliefState
import a3.core.world.IntegrateMode
import a3.core.world.ObservationAcceptance
import java.util.Collections

class EventLog {
    private val events = ArrayList<Event>()

    fun append(event: Event) {
        require(event.id.isNotBlank()) { "event id must not be blank" }
        require(events.none { it.id == event.id }) { "Duplicate event id: ${event.id}" }
        events += event
    }

    fun size(): Int = events.size

    fun all(): List<Event> = Collections.unmodifiableList(ArrayList(events))

    /** Append-only replay of the log itself. */
    fun replay(): List<Event> = all()

    /**
     * Reconstruct belief state by folding `state.updated` payloads.
     * Observation payloads are integrated; BeliefState payloads replace (snapshots).
     */
    fun replayState(initial: BeliefState = BeliefState()): BeliefState {
        var state = initial
        for (event in events) {
            if (event.type != "state.updated") continue
            state = when (val payload = event.payload) {
                is Observation -> {
                    val mode = if (payload.id.startsWith("obs_rollback_")) {
                        IntegrateMode.COMPENSATE
                    } else {
                        IntegrateMode.SUPERSEDE_KEYS
                    }
                    ObservationAcceptance.apply(state, payload, mode)
                }
                is BeliefState -> payload
                else -> state
            }
        }
        return state
    }
}
