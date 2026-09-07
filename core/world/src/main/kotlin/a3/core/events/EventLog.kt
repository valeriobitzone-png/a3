package a3.core.events

import a3.core.world.Event
import java.util.Collections

/**
 * Append-only event log. Reconstruction of committed belief lives in :core:runtime
 * as a fold over a fresh writer. This class does not mint, apply, or merge belief.
 */
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
}
