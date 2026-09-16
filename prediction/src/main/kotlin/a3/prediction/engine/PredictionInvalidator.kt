// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.prediction.engine

import a3.core.time.InstantSource
import a3.core.time.SequentialIdGenerator
import a3.prediction.model.PredictionInvalidation
import java.util.ArrayList

class PredictionInvalidator(
    private val clock: InstantSource,
    private val ids: SequentialIdGenerator,
    private val events: PredictionEventLog
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
            PredictionEvent(
                id = ids.next("evp"),
                t = clock.now(),
                type = "prediction.invalidated",
                causalId = invalidation.id,
                payload = invalidation
            )
        )
        return invalidation
    }
}
