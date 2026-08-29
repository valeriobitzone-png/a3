package a3.core.events

import a3.core.world.BeliefState
import a3.core.world.Event
import a3.core.world.IntegrateMode
import a3.core.world.Observation
import a3.core.world.integrate
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

    fun replay(): List<Event> = all()

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
                    state.integrate(payload, mode)
                }
                is BeliefState -> payload
                else -> state
            }
        }
        return state
    }
}
