package a3.prediction.engine

import a3.prediction.model.PreparedState
import a3.prediction.model.PredictionStatus
import a3.core.time.InstantSource
import java.util.ArrayList
import java.util.TreeMap

class PreparedStateStore(
    private val clock: InstantSource
) {
    private val byId = TreeMap<String, PreparedState>()

    fun put(state: PreparedState): PreparedState {
        byId[state.id] = state
        return state
    }

    fun get(id: String): PreparedState? {
        val state = byId[id] ?: return null
        return if (isLive(state)) state else null
    }

    fun peek(id: String): PreparedState? = byId[id]

    fun live(): List<PreparedState> {
        val result = ArrayList<PreparedState>()
        for (state in byId.values) {
            if (isLive(state)) result += state
        }
        return result
    }

    fun all(): List<PreparedState> = ArrayList(byId.values)

    fun markInvalidated(id: String): PreparedState? {
        val current = byId[id] ?: return null
        val updated = current.copy(status = PredictionStatus.INVALIDATED)
        byId[id] = updated
        return updated
    }

    fun isLive(state: PreparedState): Boolean {
        if (state.status != PredictionStatus.PREPARED) return false
        val now = clock.now()
        if (now.isBefore(state.preparedAt)) return false
        return now.isBefore(state.expiresAt)
    }
}
