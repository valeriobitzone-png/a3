package a3.core.prediction.engine

import a3.core.events.EventLog
import a3.core.model.Event
import a3.core.prediction.model.PredictionInvalidation
import a3.core.time.InstantSource
import a3.core.time.SequentialIdGenerator
import java.util.ArrayList

/**
 * New (context_ref, context_signature) invalidates previously live PreparedState.
 */
class PredictionInvalidator(
    private val clock: InstantSource,
    private val ids: SequentialIdGenerator,
    private val events: EventLog
) {
    fun invalidateMismatched(
        store: PreparedStateStore,
        contextRef: String,
        contextSignature: String
    ): PredictionInvalidation? {
        val toInvalidate = ArrayList<String>()
        for (state in store.live()) {
            if (state.contextRef != contextRef || state.contextSignature != contextSignature) {
                toInvalidate += state.id
            }
        }
        if (toInvalidate.isEmpty()) return null
        toInvalidate.sort()
        for (id in toInvalidate) {
            store.markInvalidated(id)
        }
        val invalidation = PredictionInvalidation(
            id = ids.next("inv"),
            preparedIds = toInvalidate,
            contextRef = contextRef,
            contextSignature = contextSignature,
            reason = "context_changed",
            t = clock.now()
        )
        events.append(
            Event(
                id = ids.next("evp"),
                t = clock.now(),
                source = "prediction",
                type = "prediction.invalidated",
                causalId = invalidation.id,
                payload = invalidation
            )
        )
        return invalidation
    }
}
