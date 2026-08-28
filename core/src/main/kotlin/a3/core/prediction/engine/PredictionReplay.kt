package a3.core.prediction.engine

import a3.core.events.EventLog
import a3.core.prediction.model.Forecast
import a3.core.prediction.model.PreparedState
import a3.core.prediction.model.PredictionInvalidation
import a3.core.prediction.model.PredictionStatus
import a3.core.time.InstantSource
import java.util.ArrayList
import java.util.TreeMap

data class ReplayedPrediction(
    val forecasts: List<Forecast>,
    val prepared: List<PreparedState>
) {
    fun lastForecast(): Forecast? = forecasts.lastOrNull()

    fun live(clock: InstantSource): List<PreparedState> {
        val result = ArrayList<PreparedState>()
        for (state in prepared) {
            if (state.status != PredictionStatus.PREPARED) continue
            val now = clock.now()
            if (!now.isBefore(state.preparedAt) && now.isBefore(state.expiresAt)) {
                result += state
            }
        }
        return result
    }
}

/**
 * Fold prediction.* events. Does not write WorldState. Ignores state.updated.
 */
object PredictionReplay {
    fun replay(log: EventLog): ReplayedPrediction {
        val forecasts = TreeMap<String, Forecast>()
        val prepared = TreeMap<String, PreparedState>()
        val forecastOrder = ArrayList<String>()
        for (event in log.all()) {
            when (event.type) {
                "prediction.updated" -> when (val payload = event.payload) {
                    is Forecast -> {
                        if (payload.id !in forecasts) forecastOrder += payload.id
                        forecasts[payload.id] = payload
                    }
                    is PreparedState -> prepared[payload.id] = payload
                }
                "prediction.invalidated" -> {
                    val invalidation = event.payload as? PredictionInvalidation ?: continue
                    for (id in invalidation.preparedIds.sorted()) {
                        val current = prepared[id] ?: continue
                        prepared[id] = current.copy(status = PredictionStatus.INVALIDATED)
                    }
                }
            }
        }
        val forecastList = ArrayList<Forecast>(forecastOrder.size)
        for (id in forecastOrder) {
            forecasts[id]?.let { forecastList += it }
        }
        return ReplayedPrediction(
            forecasts = forecastList,
            prepared = ArrayList(prepared.values)
        )
    }
}
